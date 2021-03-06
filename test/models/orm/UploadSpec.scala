package models.orm

import java.sql.Timestamp
import java.util.UUID
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.Play.start
import play.api.Play.stop
import play.api.test.FakeApplication

import helpers.DbTestContext
import models.User
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.UploadedFile

class UploadSpec extends Specification {

  step(start(FakeApplication()))

  "Upload" should {

    trait UploadContext extends DbTestContext {
      val uploadId = UUID.randomUUID
      val contentsOid = 0l
      val timestamp = new Timestamp(System.currentTimeMillis)
      val user = User()
      val uploadedFile = UploadedFile("content-disposition", "content-type", 100l)
      var upload: Upload = _

      override def setupWithDb = {
        Schema.users.insert(user)
        Schema.uploadedFiles.insert(uploadedFile)
        upload = Upload(0l, user.id, uploadId, contentsOid, uploadedFile.id, timestamp, 100)
        
        upload.save
      }
    }

    "write to and read from the database" in new UploadContext {
      upload.id must not be equalTo(0)

      val foundUpload = Schema.uploads.where(u => u.id === upload.id).headOption
      foundUpload must beSome
    }

    "be findable by UUID and user_id" in new UploadContext {
      val foundUpload = Upload.findUserUpload(user.id, uploadId)

      foundUpload must beSome
    }
    
    "be deleted" in new UploadContext {
      upload.delete
      val notFound = Upload.findUserUpload(user.id, uploadId)
      
      notFound must beNone
    }
    
    

  }

  step(stop)
}
