package org.overviewproject.jobhandler.filegroup.task

import scala.collection.JavaConverters._
import scala.util.control.Exception._
import org.apache.pdfbox.util.PDFTextStripper
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.Page
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.File
import org.overviewproject.jobhandler.filegroup.task.MimeTypeDetectingDocumentConverter._
import org.overviewproject.jobhandler.filegroup.task.LibreOfficeDocumentConverter._
import org.overviewproject.util.Logger

/**
 * Generates the steps needed to process uploaded files:
 *   1. Create a [[File]] with a view, based on the uploaded file
 *   1. Split the PDF into pages
 *   1. Save the pages
 *
 * If an Exception is thrown, processing completes, but the error is saved as a `DocumentProcessingError`
 * @todo store the error in `Page`
 */
trait CreatePagesProcess {

  protected case class TaskInformation(documentSetId: Long, uploadedFileId: Long)

  protected def startCreatePagesTask(documentSetId: Long, uploadedFileId: Long): FileGroupTaskStep =
    CreateViewableFile(TaskInformation(documentSetId, uploadedFileId))

  // Create the file with a view
  private case class CreateViewableFile(taskInformation: TaskInformation) extends ErrorSavingTaskStep {

    override def executeTaskStep: FileGroupTaskStep = {
      val upload = storage.loadUploadedFile(taskInformation.uploadedFileId).get // throws if not found.
      val file = createFile(taskInformation.documentSetId, upload)
      storage.deleteUploadedFile(upload)

      LoadPdf(taskInformation, file)
    }
  }

  // Read the pdf document 
  private case class LoadPdf(taskInformation: TaskInformation, file: File) extends ErrorSavingTaskStep {
    override def executeTaskStep: FileGroupTaskStep = {
      val pdfDocument = pdfProcessor.loadFromDatabase(file.viewOid)

      WritePdfPages(taskInformation, file, pdfDocument)
    }
  }

  private case class WritePdfPages(taskInformation: TaskInformation, file: File, pdfDocument: PdfDocument) extends ErrorSavingTaskStep {

    override def executeTaskStep: FileGroupTaskStep = {
      storage.savePages(file.id, pdfDocument.pages.map { p => p.data -> p.text })
      pdfDocument.close
      
      CreatePagesProcessComplete(taskInformation.documentSetId, taskInformation.uploadedFileId, Some(file.id))      
    }
  }

  /** [[FileGroupTaskStep]] that catches exceptions, stores them as errors, and completes the [[CreatePagesProcess]] */
  protected trait ErrorSavingTaskStep extends FileGroupTaskStep {
    private lazy val logger = Logger.forClass(getClass)
    private val UnknownError = "Unknown error"

    protected val taskInformation: TaskInformation

    override def execute: FileGroupTaskStep = runSavingError { executeTaskStep }

    protected def executeTaskStep: FileGroupTaskStep

    protected def saveError(error: Throwable): FileGroupTaskStep = {
      val errorMessage = Option(error.getMessage).getOrElse(UnknownError)
      logError(error)
      
      storage.saveProcessingError(taskInformation.documentSetId, taskInformation.uploadedFileId, errorMessage)
      CreatePagesProcessComplete(taskInformation.documentSetId, taskInformation.uploadedFileId, None)
    }

    private val runSavingError = handling(classOf[Exception]) by saveError
    
    private def logError(error: Throwable): Unit = error match {
      case e: LibreOfficeConverterFailedException => logger.error(s"Conversion Error: ", error)
      case e: LibreOfficeNoOutputException => logger.warn(s"No Conversion Output: ", error)
      case e: CouldNotDetectMimeTypeException => logger.warn("Could not read file {} during MIME type detection", e.filename)
      case e: DocumentConverterDoesNotExistException => logger.warn("No DocumentConverter exists for file {} with MIME type {}", e.filename, e.mimeType)
      case e => logger.info("Text extraction failed: ", error)
    }
    
  }

  protected val createFile: CreateFile
  protected val pdfProcessor: PdfProcessor
  protected trait PdfProcessor {
    def loadFromDatabase(oid: Long): PdfDocument
  }

  protected val storage: Storage
  protected trait Storage {
    def loadUploadedFile(uploadedFileId: Long): Option[GroupedFileUpload]
    def deleteUploadedFile(upload: GroupedFileUpload): Unit

    /** Saves pages permanently.
      *
      * The pages are sequential. (The first is page 1.) Each consists of an
      * Array[Byte] blob of data and a String text representation.
      */
    def savePages(fileId: Long, pages: Iterable[Tuple2[Array[Byte],String]]): Unit

    def saveProcessingError(documentSetId: Long, uploadedFileId: Long, errorMessage: String): Unit
  }

}

