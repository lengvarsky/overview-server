package org.overviewproject.models

case class FileGroup(
  id: Long,
  userEmail: String,
  apiToken: Option[String],
  completed: Boolean
)

object FileGroup {
  case class CreateAttributes(
    userEmail: String,
    apiToken: Option[String]
  )
}
