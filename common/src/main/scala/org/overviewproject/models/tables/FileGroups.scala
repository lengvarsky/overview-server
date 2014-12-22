package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.FileGroup

class FileGroupsImpl(tag: Tag) extends Table[FileGroup](tag, "file_group") {
  def id = column[Long]("id", O.PrimaryKey)
  def userEmail = column[String]("user_email")
  def apiToken = column[Option[String]]("api_token")
  def completed = column[Boolean]("completed")
  
  def * = (id, userEmail, apiToken, completed) <> ((FileGroup.apply _).tupled, FileGroup.unapply)
}

object FileGroups extends TableQuery(new FileGroupsImpl(_))
