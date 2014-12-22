package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.FileGroup

trait FileGroupBackend extends Backend {
  /** Updates a FileGroup.
    *
    * The only property you can update is <tt>completed</tt>.
    *
    * Returns an error if the database write fails.
    */
  def update(id: Long, completed: Boolean): Future[FileGroup]

  /** Finds or creates a FileGroup.
    *
    * The FileGroup will <em>not</em> be <tt>completed</tt>. If no FileGroup
    * exists which is not <tt>completed</tt> with the given parameters, a new
    * one will be created.
    */
  def findOrCreate(attributes: FileGroup.CreateAttributes): Future[FileGroup]

  /** Finds a FileGroup.
    *
    * The FileGroup will <em>not</em> be <tt>completed</tt>.
    */
  def find(userEmail: String, apiToken: Option[String]): Future[Option[FileGroup]]

  /** Destroys a FileGroup and all associated uploads.
    *
    * Throws an exception if the file group is completed. (When I wrote this
    * method, I wasn't considering that case at all.)
    */
  def destroy(id: Long): Future[Unit]
}
