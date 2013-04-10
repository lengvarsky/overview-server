package org.overviewproject.http

import scala.concurrent.duration.DurationInt
import org.overviewproject.http.RequestQueueProtocol._
import org.specs2.mock.Mockito
import org.specs2.mutable.{ After, Specification }
import org.specs2.time.NoTimeConversions
import com.ning.http.client.Response
import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import org.specs2.specification.Scope


class RequestQueueSpec extends Specification with Mockito with NoTimeConversions { // avoid conflicts with akka time conversion
  sequential

  trait ClientContext extends Scope {
    val MaxInFlightRequests = 5
    val client = new TestClient
    val request = PublicRequest("url")
    val response = mock[Response]

    response.getStatusCode returns 200
    response.getResponseBody returns "body"

  }

  abstract class ActorSystemContext extends TestKit(ActorSystem()) with ImplicitSender with ClientContext with After {
    def after = system.shutdown()
    
    def createRequestQueue(maxInFlightRequests: Int = MaxInFlightRequests): TestActorRef[RequestQueue] = TestActorRef(new RequestQueue(client, maxInFlightRequests))
  }

  
  "RequestQueue" should {

    "handle one request" in new ActorSystemContext {
      val requestQueue = createRequestQueue()
      requestQueue ! AddToEnd(request)

      client.completeAllRequests(response)
      expectMsgType[Result](1 seconds)
    }

    "only have N requests in flight at a time" in new ActorSystemContext {
      val requestQueue = createRequestQueue()
      
      1 to (MaxInFlightRequests + 1) foreach {_ => requestQueue ! AddToEnd(request) }

      client.requestsInFlight must be equalTo(MaxInFlightRequests)
      
      client.completeNext(response)
      client.requestsInFlight must be equalTo(MaxInFlightRequests)
      
      client.completeAllRequests(response)

      client.requestsInFlight must be equalTo(0)
    }

    "add requests to the front of the queue" in new ActorSystemContext {
      val frontUrl = "front url"
      val requestQueue = createRequestQueue(maxInFlightRequests = 1)
      
      1 to 5 foreach {_ => requestQueue ! AddToEnd(request) }
      
      requestQueue ! AddToFront(PublicRequest(frontUrl))
      
      client.completeNext(response)
      client.requestedUrls.headOption must beSome(frontUrl)
    }
    
    "treat failed requests as completed" in new ActorSystemContext {
       val failure = new Exception("failed")
       val requestQueue = createRequestQueue(maxInFlightRequests = 1)
       
       requestQueue ! AddToEnd(request)
       requestQueue ! AddToEnd(request)
       
       client.failNext(failure)
       
       expectMsg(Failure(failure))
       
       client.requestsInFlight must be equalTo(1)
    }
  }
}