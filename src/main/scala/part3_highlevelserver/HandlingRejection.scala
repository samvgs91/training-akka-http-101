package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.javadsl.server.{MethodRejection, MissingQueryParamRejection}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.Rejection

object HandlingRejection extends App {

  implicit val system = ActorSystem("HandlingRejection")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleRouter =
    path("api"/"myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
      parameter('id) { _=>
        complete(StatusCodes.OK)
      }
    }


  // Rejection handler
  val badRequestHandler:RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers = { // handle rejections from the top level
    handleRejections(badRequestHandler) {
      // define server logic inside
      path("api" / "myEndpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
          post {
            handleRejections(forbiddenHandler) {
              parameter('myParam) { _ =>
                complete(StatusCodes.OK)
              }
            }
          }
      }
    }
  }
  //Http().bindAndHandle(simpleRouteWithHandlers,"localhost",8080)


  ///custom rejection handler

  implicit val customRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"I got a query param rejection: $m")
        complete("Rejected query param")
    }
    .handle {
      case m:MethodRejection =>
        println(s"I got a query rejection: $m")
        complete("Rejected method!")
    }.result()

    // we use simple route cause and we are using the implicit customRejectionHandler
    // that is going to be apply automatically in the simple route
    Http().bindAndHandle(simpleRouter,"localhost",8080)
}
