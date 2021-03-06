package controllers.backend

import org.overviewproject.models.tables.Documents

class DbSavedSearchDocumentBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbSavedSearchDocumentBackend

    def findDocument(id: Long) = {
      import org.overviewproject.database.Slick.simple._
      Documents.filter(_.id === id).firstOption(session)
    }
  }

  "DbSavedSearchDocumentBackend" should {
    "index a search result" in new BaseScope {
      val documentSet = factory.documentSet()
      val searchResult = factory.searchResult(documentSetId=documentSet.id)
      val document1 = factory.document(documentSetId=documentSet.id)
      val document2 = factory.document(documentSetId=documentSet.id)
      factory.documentSearchResult(documentId=document1.id, searchResultId=searchResult.id)
      factory.documentSearchResult(documentId=document2.id, searchResultId=searchResult.id)

      val ret = await(backend.index(searchResult.id))
      ret.items.length must beEqualTo(2)
      ret.pageInfo.total must beEqualTo(2)
      ret.items.map(_.id) must containTheSameElementsAs(Seq(document1.id, document2.id))
    }

    "not index a different search result" in new BaseScope {
      val documentSet = factory.documentSet()
      val searchResult1 = factory.searchResult(documentSetId=documentSet.id)
      val searchResult2 = factory.searchResult(documentSetId=documentSet.id)
      val document1 = factory.document(documentSetId=documentSet.id)
      val document2 = factory.document(documentSetId=documentSet.id)
      factory.documentSearchResult(documentId=document1.id, searchResultId=searchResult2.id)
      factory.documentSearchResult(documentId=document2.id, searchResultId=searchResult2.id)

      val ret = await(backend.index(searchResult1.id))
      ret.items.length must beEqualTo(0)
      ret.pageInfo.total must beEqualTo(0)
    }
  }
}
