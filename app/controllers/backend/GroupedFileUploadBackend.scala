package controllers.backend

import java.util.UUID
import scala.concurrent.Future

import org.overviewproject.models.GroupedFileUpload

trait GroupedFileUploadBackend extends Backend {
  /** Finds or creates a GroupedFileUpload.
    *
    * If the guid matches an existing guid in the given FileGroup, this will
    * return the existing GroupedFileUpload. Otherwise, it will return a new
    * one.
    */
  def findOrCreate(attributes: GroupedFileUpload.CreateAttributes): Future[GroupedFileUpload]

  /** Finds a GroupedFileUpload.
    */
  def find(fileGroupId: Long, guid: UUID): Future[Option[GroupedFileUpload]]

  /** Writes bytes to a GroupedFileUpload.
    *
    * Returns the updated GroupedFileUpload.
    *
    * @param id GroupedFileUpload id.
    * @param position Position to start writing.
    * @param bytes Bytes to write.
    */
  def writeBytes(id: Long, position: Long, bytes: Array[Byte]): Future[GroupedFileUpload]
}
