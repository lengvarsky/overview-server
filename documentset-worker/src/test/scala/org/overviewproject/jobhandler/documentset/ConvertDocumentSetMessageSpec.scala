package org.overviewproject.jobhandler.documentset

import org.specs2.mutable.Specification
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandlerProtocol._

class ConvertDocumentSetMessageSpec extends Specification {

  "ConvertMessage" should {

    "convert a search command" in {
      val documentSetId = 123
      val queryString = "project:5239 search terms"

      val messageString = s"""{
        "cmd": "search",
        "args": { 
          "documentSetId": $documentSetId,
          "query": "$queryString"
        }
     }"""

      val command = ConvertDocumentSetMessage(messageString)

      command must beLike { case SearchCommand(documentSetId, queryString) => ok }

    }

    "convert a delete command" in {
      val documentSetId = 123
      val waitFlag = true

      val messageString = s"""{
        "cmd": "delete",
        "args": {
          "documentSetId": $documentSetId,
          "waitForJobRemoval": $waitFlag
        }
      }"""

      val command = ConvertDocumentSetMessage(messageString)

      command must beLike { case DeleteCommand(documentSetId, waitFlag) => ok }
    }

    "convert a deleteTreeJob commmand" in {
      val jobId = 123

      val messageString = s"""{
        "cmd": "delete_tree_job",
        "args": {
           "jobId": $jobId
        }
      }"""

      val command = ConvertDocumentSetMessage(messageString)

      command must beLike { case DeleteTreeJobCommand(jobId) => ok }

    }

  }

}
