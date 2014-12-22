package controllers

import java.util.UUID
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.Logger
import play.api.mvc.{ BodyParser, Request, RequestHeader, Result }
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.auth.{ AuthorizedAction, AuthorizedBodyParser }
import controllers.backend.FileGroupBackend
import controllers.forms.MassUploadControllerForm
import controllers.util.{ JobQueueSender, MassUploadFileIteratee, TransactionAction }
import models.orm.stores.{ DocumentSetCreationJobStore, DocumentSetStore, DocumentSetUserStore }
import models.OverviewDatabase
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.jobs.models.ClusterFileGroup
import org.overviewproject.tree.orm.{DocumentSet,DocumentSetCreationJob,DocumentSetUser}
import org.overviewproject.tree.Ownership
import org.overviewproject.util.ContentDisposition

trait MassUploadController extends Controller {
  protected val fileGroupBackend: FileGroupBackend

  /** Starts or resumes a file upload.
    *
    * The BodyParser does the real work. This method merely says Ok.
    */
  def create(guid: UUID) = TransactionAction(authorizedUploadBodyParser(guid)) { implicit request: Request[MassUploadFileIteratee.Result] =>
    request.body match {
      case MassUploadFileIteratee.BadRequest(message) => BadRequest(message)
      case MassUploadFileIteratee.Ok() => Ok
    }
  }

  /**
   * @returns information about the upload specified by `guid` in the headers of the response.
   * content_range and content_length are provided.
   */
  def show(guid: UUID) = AuthorizedAction.inTransaction(anyUser).async { request =>
    def contentDisposition(upload: GroupedFileUpload) = {
      ContentDisposition.fromFilename(upload.name).contentDisposition
    }

    def uploadHeaders(upload: GroupedFileUpload): Seq[(String, String)] = {
      def computeEnd(uploadedSize: Long): Long =
        if (upload.uploadedSize == 0) 0
        else upload.uploadedSize - 1

      Seq(
        (CONTENT_LENGTH, s"${upload.uploadedSize}"),
        (CONTENT_RANGE, s"bytes 0-${computeEnd(upload.uploadedSize)}/${upload.size}"),
        (CONTENT_DISPOSITION, contentDisposition(upload)))
    }

    findUploadInCurrentFileGroup(request.user.email, guid).map((_: Option[GroupedFileUpload]) match {
      case Some(u) if (isUploadEmpty(u)) => {
        // You can't send an Ok or PartialContent when Content-Length=0
        NoContent.withHeaders((CONTENT_DISPOSITION, contentDisposition(u)))
      }
      case Some(u) if (isUploadComplete(u)) => Ok.withHeaders(uploadHeaders(u): _*)
      case Some(u) => PartialContent.withHeaders(uploadHeaders(u): _*)
      case None => NotFound
    })
  }

  /**
   * Notify the worker that clustering can start as soon as all currently uploaded files
   * have been processed
   */
  def startClustering = AuthorizedAction(anyUser).async { implicit request =>
    MassUploadControllerForm().bindFromRequest.fold(
      e => Future(BadRequest),
      startClusteringFileGroupWithOptions(request.user.email, _)
    )
  }

  /**
   * Cancel the upload and notify the worker to delete all uploaded files
   */
  def cancelUpload = AuthorizedAction.inTransaction(anyUser).async { request =>
    fileGroupBackend.find(request.user.email, None)
      .flatMap(_ match {
        case Some(fileGroup) => fileGroupBackend.destroy(fileGroup.id)
        case None => Future.successful(())
      })
      .map(_ => Ok)
  }

  // method to create the MassUploadFileIteratee
  protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID): Iteratee[Array[Byte], Either[Result, Unit]]

  /** interface to database related methods */
  protected val storage: Storage

  /** interface to message queue related methods */
  val messageQueue: MessageQueue

  trait Storage {
    /** @returns a newly created DocumentSet */
    def createDocumentSet(userEmail: String, title: String, lang: String): DocumentSet

    /** @returns a newly created DocumentSetCreationJob */
    def createMassUploadDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, lang: String, splitDocuments: Boolean,
                                               suppliedStopWords: String, importantWords: String): DocumentSetCreationJob
  }

  trait MessageQueue {
    /** Notify the worker that clustering can start */
    def startClustering(job: DocumentSetCreationJob, documentSetTitle: String): Future[Unit]
  }

  private def authorizedUploadBodyParser(guid: UUID) =
    AuthorizedBodyParser(anyUser) { user => uploadBodyParser(user.email, guid) }

  private def uploadBodyParser(userEmail: String, guid: UUID): BodyParser[MassUploadFileIteratee.Result] =
    BodyParser("Mass upload bodyparser") { request =>
      massUploadFileIteratee(userEmail, request, guid)
    }

  private def findUploadInCurrentFileGroup(userEmail: String, guid: UUID): Future[Option[GroupedFileUpload]] = {
    for {
      fileGroup <- fileGroupBackend.find(userEmail, None)
      upload <- groupedFileUploadBackend.find(fileGroup.id, guid)
    } yield upload
  }

  private def isUploadComplete(upload: GroupedFileUpload): Boolean =
    upload.uploadedSize == upload.size

  private def isUploadEmpty(upload: GroupedFileUpload): Boolean =
    upload.uploadedSize == 0

  private def startClusteringFileGroupWithOptions(userEmail: String,
                                                  options: (String, String, Boolean, String, String)): Future[Result] = {
    val (name, lang, splitDocuments, suppliedStopWords, importantWords) = options

    fileGroupBackend.find(userEmail, None).flatMap(_ match {
      case Some(fileGroup) => {
        val job: DocumentSetCreationJob = OverviewDatabase.inTransaction {
          await(fileGroupBackend.update(fileGroup.id, true))
          val documentSet = storage.createDocumentSet(userEmail, name, lang)
          storage.createMassUploadDocumentSetCreationJob(
            documentSet.id, fileGroup.id, lang, splitDocuments, suppliedStopWords, importantWords)
        }
        messageQueue
          .startClustering(job, name)
          .map(() => Redirect(routes.DocumentSetController.index()))
      }
      case None => Future.successful(NotFound)
    })
  }
}

/** Controller implementation */
object MassUploadController extends MassUploadController {

  override protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID): Iteratee[Array[Byte], MassUploadFileIteratee.Result] =
    MassUploadFileIteratee(userEmail, request, guid)

  override val storage = DatabaseStorage
  override val messageQueue = new ApolloQueue
  override val fileGroupBackend = FileGroupBackend

  object DatabaseStorage extends Storage {
    import org.overviewproject.tree.orm.DocumentSetCreationJobState.FilesUploaded
    import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload

    override def createDocumentSet(userEmail: String, title: String, lang: String): DocumentSet = {
      val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(title = title))
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet.id, userEmail, Ownership.Owner))

      documentSet
    }

    override def createMassUploadDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long,
                                                        lang: String, splitDocuments: Boolean,
                                                        suppliedStopWords: String,
                                                        importantWords: String): DocumentSetCreationJob = {
      DocumentSetCreationJobStore.insertOrUpdate(
        DocumentSetCreationJob(
          documentSetId = documentSetId,
          fileGroupId = Some(fileGroupId),
          lang = lang,
          splitDocuments = splitDocuments,
          suppliedStopWords = suppliedStopWords,
          importantWords = importantWords,
          state = FilesUploaded,
          jobType = FileUpload))
    }
  }

  class ApolloQueue extends MessageQueue {
    override def startClustering(job: DocumentSetCreationJob, documentSetTitle: String): Future[Unit] = {
      val command = ClusterFileGroup(
        documentSetId=job.documentSetId,
        fileGroupId=job.fileGroupId.get,
        name=documentSetTitle,
        lang=job.lang,
        splitDocuments=job.splitDocuments,
        stopWords=job.suppliedStopWords,
        importantWords=job.importantWords
      )

      JobQueueSender.send(command)
    }
  }
}



