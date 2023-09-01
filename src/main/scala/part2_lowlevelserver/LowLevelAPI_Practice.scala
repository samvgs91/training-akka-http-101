package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethod, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.server.ContentNegotiator.Alternative.ContentType
import part2_lowlevelserver.LowLevelAPI.httpAsyncConnectionHandler

import scala.concurrent.Future
import scala.util.{Failure, Success}

object LowLevelAPI_Practice extends App{

  implicit val system = ActorSystem("PractiLowLevelAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val asyncFlowHandler : Flow[HttpRequest,HttpResponse,_] =  Flow[HttpRequest].map {

    case HttpRequest(HttpMethods.GET,Uri.Path("/about"),_,_,_) =>
      HttpResponse(StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   This is a practice to see routes. Date: 15/08/2023
            | </body>
            |</html>
                    """.stripMargin
        )
      )
    case HttpRequest(HttpMethods.GET,Uri.Path("/"),_,_,_) =>
      HttpResponse(StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Front Door
            | </body>
            |</html>
                    """.stripMargin
        )
      )
    // path /search redirects to some other part of our website/webapp/microservice
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"),_, _, _) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com"))
      )

    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Resource can't be found.
            | </body>
            |</html>
            """.stripMargin)
      )

  }

  Http().bind("localhost",8388).runForeach { connection =>
        connection.handleWith(asyncFlowHandler)
  }
  // we can shut down an httpserver:

//  val bindingFuture = Http().bindAndHandle(asyncFlowHandler,"localhost",8388)
//
//  bindingFuture.flatMap(binding => binding.unbind()).onComplete(_=> system.terminate())

}
