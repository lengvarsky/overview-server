package org.overviewproject.jobhandler.documentset

import scala.concurrent.Promise
import scala.concurrent.duration._
import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse
import org.overviewproject.database.{ DocumentSetDeleter => NewDocumentSetDeleter }
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol._
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.specs2.mutable.{ Before, Specification }
import org.specs2.time.NoTimeConversions
import org.overviewproject.database.DocumentSetCreationJobDeleter


class DeleteHandlerSpec extends Specification with Mockito with NoTimeConversions {

  "DeleteHandler" should {

    trait MockComponents {
      val searchIndex = mock[SearchIndexComponent]
      val documentSetDeleter = smartMock[DocumentSetDeleter]
      val newDocumentSetDeleter = smartMock[NewDocumentSetDeleter]
      val jobDeleter = smartMock[DocumentSetCreationJobDeleter]
      val jobStatusChecker = smartMock[JobStatusChecker]

      val searchIndexRemoveDocumentSetPromise = Promise[Unit]
      val deleteDocumentSetPromise = Promise[Unit]
      
      searchIndex.removeDocumentSet(anyLong) returns searchIndexRemoveDocumentSetPromise.future
      newDocumentSetDeleter.delete(anyLong) returns deleteDocumentSetPromise.future
    }

    class TestDeleteHandler extends DeleteHandler with MockComponents {
      override protected val JobWaitDelay = 5 milliseconds
      override protected val MaxRetryAttempts = 3
    }

    abstract class DeleteContext extends ActorSystemContext with Before {
      protected val documentSetId = 2l

      protected var parentProbe: TestProbe = _
      protected var deleteHandler: TestActorRef[TestDeleteHandler] = _
      protected var jobStatusChecker: JobStatusChecker = _
      
      protected def searchIndex = deleteHandler.underlyingActor.searchIndex
      protected def searchIndexRemoveDocumentSetResult = deleteHandler.underlyingActor.searchIndexRemoveDocumentSetPromise
      protected def deleteDocumentSetResult = deleteHandler.underlyingActor.deleteDocumentSetPromise
      
      protected def documentSetDeleter = deleteHandler.underlyingActor.documentSetDeleter
      protected def newDocumentSetDeleter = deleteHandler.underlyingActor.newDocumentSetDeleter

      protected def setJobStatus: Unit =
        deleteHandler.underlyingActor.jobStatusChecker isJobRunning (documentSetId) returns false

      override def before = {
        parentProbe = TestProbe()
        deleteHandler = TestActorRef(Props(new TestDeleteHandler), parentProbe.ref, "DeleteHandler")
        jobStatusChecker =  deleteHandler.underlyingActor.jobStatusChecker
        
        setJobStatus
      }
    }

    abstract class DeleteWhileJobIsRunning extends DeleteContext {
      override protected def setJobStatus: Unit =
        deleteHandler.underlyingActor.jobStatusChecker isJobRunning (documentSetId) returns true thenReturns false
    }

    abstract class TimeoutWhileWaitingForJob extends DeleteContext {
      override protected def setJobStatus: Unit =
        jobStatusChecker isJobRunning (documentSetId) returns true

      override def before = {
        super.before
        parentProbe.watch(deleteHandler)
      }
    }

    "delete documents and alias with the specified documentSetId from the index" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      there was one(searchIndex).removeDocumentSet(documentSetId)
    }

    "delete document set related data" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      
      there was one(newDocumentSetDeleter).delete(documentSetId)
    }
    
    "notify parent when deletion of documents and alias completes successfully" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      
      parentProbe.expectNoMsg(10 millis)
      
      searchIndexRemoveDocumentSetResult.success(Unit)
      parentProbe.expectNoMsg(10 millis)

      deleteDocumentSetResult.success(Unit)
      
      parentProbe.expectMsg(JobDone(documentSetId))
    }

    "notify parent when deletion fails" in new DeleteContext {
      val error = new Exception

      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      searchIndexRemoveDocumentSetResult.failure(error)
      deleteDocumentSetResult.success(Unit)

      // FIXME: We can't distinguish between failure and success right now
      parentProbe.expectMsg(JobDone(documentSetId))
    }


    "wait until clustering job is no longer running before deleting" in new DeleteWhileJobIsRunning {
      deleteHandler ! DeleteDocumentSet(documentSetId, true)
      searchIndexRemoveDocumentSetResult.success(Unit)
      deleteDocumentSetResult.success(Unit)
      
      parentProbe.expectMsg(JobDone(documentSetId))
    }

    "don't wait until clustering job is no longer running if waitForJobRemoval is false and no job is running" in new DeleteWhileJobIsRunning {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      searchIndexRemoveDocumentSetResult.success(Unit)
      deleteDocumentSetResult.success(Unit)
      
      parentProbe.expectMsg(JobDone(documentSetId))
      there was no(jobStatusChecker).isJobRunning(documentSetId)
    }
    
    "cancel any clustering job before deleting a document set if waitForJobRemoval is false" in new DeleteWhileJobIsRunning {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      searchIndexRemoveDocumentSetResult.success(Unit)
      deleteDocumentSetResult.success(Unit)
      
      parentProbe.expectMsg(JobDone(documentSetId))
      there was one(jobStatusChecker).cancelJob(documentSetId)
    }

    "timeout while waiting for job that never gets cancelled" in new TimeoutWhileWaitingForJob {
      deleteHandler ! DeleteDocumentSet(documentSetId, true)

      parentProbe.expectMsg(JobDone(documentSetId))
      parentProbe.expectTerminated(deleteHandler)
    }
    
    "delete reclustering job" in new DeleteContext {
      val jobId = 12L

      deleteHandler ! DeleteReclusteringJob(jobId)
      
      parentProbe.expectMsg(JobDone(jobId))
      // FIXME: We can't actually test this because the underlying actor is terminated
      // when we enable cancellation of individual reclustering jobs this path can be moved
      // out into a testable class
      // there was one(documentSetDeleter).deleteJobInformation(documentSetId)
    }
  }
}

