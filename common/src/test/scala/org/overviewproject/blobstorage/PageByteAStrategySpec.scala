package org.overviewproject.blobstorage

import org.overviewproject.models.tables.Files
import org.overviewproject.models.File
import org.overviewproject.models.tables.Pages
import org.overviewproject.models.Page
import org.overviewproject.test.SlickSpecification
import org.overviewproject.database.Slick.simple._
import scala.concurrent.Future

class PageByteAStrategySpec extends SlickSpecification with StrategySpecHelper {

  "PageByteAStrategy" should {

    "#get" should {

      "return an enumerator from data" in new ExistingPageScope {
        val future = strategy.get(s"pagebytea:${page.id}")
        val enumerator = await(future)
        val bytesRead = consume(enumerator)

        bytesRead must be equalTo data
      }

      "throw a delayed exception if data is NULL" in new NoDataPageScope {
        val future = strategy.get(s"pagebytea:${page.id}")
        await(future) must throwA[Exception]
      }

      "throw an exception when get location does not look like pagebytea:PAGEID" in new ExistingPageScope {
        invalidLocationThrowsException(strategy.get)
      }

      "throw a delayed exception if pageId is not a valid id" in new ExistingFileScope {
        val future = strategy.get(s"pagebytea:0")
        await(future) must throwA[Exception]
      }
    }

    "#delete" should {
      "not do anything" in new ExistingPageScope {
        val future = strategy.delete(s"pagebytea:${page.id}")
        await(future)

        pageData must beSome(data)
      }
    }

    "#create" should {
      "throw NotImplementedError" in new ExistingPageScope { 
        strategy.create(s"pagebytea:${page.id}", contentStream, data.length) must throwA[NotImplementedError]
      }
    }
  }

  object DbFactory {

    private val insertFileInvoker = {
      val q = for (f <- Files) yield (f.referenceCount, f.contentsOid, f.viewOid, f.name, f.contentsSize, f.viewSize)
      (q returning Files).insertInvoker
    }

    private val insertPageInvoker = {
      val q = for (p <- Pages) yield (p.fileId, p.pageNumber, p.data, p.dataSize)
      (q returning Pages).insertInvoker
    }

    def insertFile(implicit session: Session): File =
      insertFileInvoker.insert(1, 10l, 10l, "name", 100l, 100l)

    def insertPage(fileId: Long, data: Array[Byte])(implicit session: Session): Page =
      insertPageInvoker.insert(fileId, 1, Some(data), data.length)

    def insertPageNoData(fileId: Long)(implicit session: Session): Page =
      insertPageInvoker.insert(fileId, 1, None, 0)

  }

  trait PageBaseScope extends DbScope {

    class TestPageByteAStrategy(session: Session) extends PageByteAStrategy {
      import scala.concurrent.ExecutionContext.Implicits.global

      override def db[A](block: Session => A): Future[A] = Future {
        block(session)
      }
    }

    val strategy = new TestPageByteAStrategy(session)

    def invalidLocationThrowsException[T](f: String => T) =
      (f("pagebyteX:1234") must throwA[IllegalArgumentException]) and
        (f("pagebytea::1234") must throwA[IllegalArgumentException]) and
        (f("pagebytea:") must throwA[IllegalArgumentException])

  }

  trait ExistingFileScope extends PageBaseScope {
    val file = DbFactory.insertFile
  }

  trait ExistingPageScope extends ExistingFileScope {
    val data = Array[Byte](1, 2, 3)
    val page = DbFactory.insertPage(file.id, data)
    
    val contentStream = byteArrayInputStream(data)
    
    def pageData = {
      val q = for (p <- Pages if p.id === page.id) yield p.data
      q.firstOption.flatten
    } 
  }

  trait NoDataPageScope extends ExistingFileScope {
    val page = DbFactory.insertPageNoData(file.id)
  }
}

