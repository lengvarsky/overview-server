package models

import anorm.SQL
import anorm.SqlParser._
import java.sql.Connection
import models.DatabaseStructure.DocumentData

class DocumentDataLoader {
  def loadDocument(id: Long)(implicit c: Connection) : Option[DocumentData] = {
    val documentParser = long("id") ~ str("title") ~ str("text_url") ~ str("view_url")
    
    val document = SQL("""
        SELECT id, title, text_url, view_url
        FROM document
        WHERE id = {documentId}
        """).on("documentId" -> id).
        as(documentParser map(flatten) *)
        
    document.headOption
  }
}