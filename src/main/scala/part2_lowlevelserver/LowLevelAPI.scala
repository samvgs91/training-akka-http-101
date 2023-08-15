package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethod, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.server.ContentNegotiator.Alternative.ContentType
import part2_lowlevelserver.LowLevelAPI_Practice.asyncFlowHandler

import scala.concurrent.Future
import scala.util.{Failure, Success}

object LowLevelAPI extends App {

  implicit val system = ActorSystem("LowLevelServerAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val serverSource = Http().bind("localhost",8000)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from :${connection.remoteAddress}")
  }

  val serverBindingFuture = serverSource.to(connectionSink).run()

  serverBindingFuture.onComplete {
    case Success(_) => println("Server binding sucessful.")
    case Failure(ex) => println(s"Server binding failed: $ex")
  }

  /*
   Method 1: syncronously serve HTTP responses
   */

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET,_,_,_,_) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
          """.stripMargin
        )
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

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

//  Http().bind("localhost",8080).runWith(httpSyncConnectionHandler)

    //shorthand version
    Http().bindAndHandleSync(requestHandler,"localhost",8080)

  /*
     Method 2: serve back HTTP response ASYNCHRONOUSLY
     Note: in production make sure you have a specific context to asign to these futures.
  * */

  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET,Uri.Path("/home"),_,_,_) => //method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2.0)
      Future(HttpResponse(
        StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   Hello from Akka HTTP!
              | </body>
              |</html>
            """.stripMargin
          )
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
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
      ))
  }

  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

//  Http().bind("localhost",8081).runWith(httpAsyncConnectionHandler)

  //shorthand version
  Http().bindAndHandleAsync(asyncRequestHandler,"localhost",8081)

  /*
  Method 3: async via Akka streams
  */


  val streamBaseRequestHandler: Flow[HttpRequest,HttpResponse,_] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET,Uri.Path("/home"),_,_,_) => //method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2.0)
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
            """.stripMargin
        )
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

  //Note: Http().bind("localhost") is the source taking this as an Akka Stream.
//  Http().bind("localhost",8082).runForeach { connection =>
//    connection.handleWith(streamBaseRequestHandler)
//  }



}
