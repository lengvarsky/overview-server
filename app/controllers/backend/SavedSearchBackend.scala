package controllers.backend

import java.sql.Timestamp
import scala.concurrent.Future

import controllers.util.JobQueueSender
import org.overviewproject.jobs.models.Search
import org.overviewproject.models.SearchResult 
import org.overviewproject.models.tables.{DocumentSearchResults,SearchResults}

trait SavedSearchBackend {
  def create(search: Search) : Future[Unit] // FIXME make it a Future[SearchResult]
  def index(documentSetId: Long) : Future[Seq[SearchResult]]
  def show(documentSetId: Long, query: String) : Future[Option[SearchResult]]
  def destroy(documentSetId: Long, query: String) : Future[Unit]
}

trait DbSavedSearchBackend extends SavedSearchBackend { self: DbBackend =>
  val jobQueueSender: JobQueueSender

  override def create(search: Search) = jobQueueSender.send(search)

  override def index(documentSetId: Long) = db { session =>
    DbSavedSearchBackend.byDocumentSetId(documentSetId)(session)
  }

  override def show(documentSetId: Long, query: String) = db { session =>
    DbSavedSearchBackend.byDocumentSetIdAndQuery(documentSetId, query)(session)
  }

  override def destroy(documentSetId: Long, query: String) = db { session =>
    DbSavedSearchBackend.deleteByDocumentSetIdAndQuery(documentSetId, query)(session)
  }
}

object DbSavedSearchBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    SearchResults.filter(_.documentSetId === documentSetId)
  }

  private lazy val byDocumentSetIdAndQueryCompiled = Compiled { (documentSetId: Column[Long], query: Column[String]) =>
    SearchResults.filter(_.documentSetId === documentSetId).filter(_.query === query)
  }

  private lazy val documentSearchResultsCompiled = Compiled { (documentSetId: Column[Long], query: Column[String]) =>
    val searchResultId = SearchResults.filter(_.documentSetId === documentSetId).filter(_.query === query).map(_.id)
    DocumentSearchResults.filter(_.searchResultId in searchResultId)
  }

  def byDocumentSetId(documentSetId: Long)(session: Session) = {
    byDocumentSetIdCompiled(documentSetId).list(session)
  }

  def byDocumentSetIdAndQuery(documentSetId: Long, query: String)(session: Session) = {
    byDocumentSetIdAndQueryCompiled(documentSetId, query).firstOption(session)
  }

  def deleteByDocumentSetIdAndQuery(documentSetId: Long, query: String)(session: Session) = {
    // Slick is sync, really, as much as we pretend it's async
    documentSearchResultsCompiled(documentSetId, query).delete(session)
    byDocumentSetIdAndQueryCompiled(documentSetId, query).delete(session)
  }
}

object SavedSearchBackend extends DbSavedSearchBackend with DbBackend {
  override val jobQueueSender = JobQueueSender
}
