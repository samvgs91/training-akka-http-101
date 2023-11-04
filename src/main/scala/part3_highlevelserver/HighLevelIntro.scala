package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import part2_lowlevelserver.HttpContext

object HighLevelIntro extends App{

  implicit val system = ActorSystem("HighLevelIntro")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // directives
  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route =
    path("home") { //DIRECTIVE
      complete(StatusCodes.OK) //DIRECTIVE
    }

  val pathGetRoute: Route =
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }

//  Note #1:
//    Directives:
//  - are several ones
//  - can path and complete response.
//    - the result of directives of an error.
//
//  Rote type can be implicitly convert to a Flow
//
//  Note #2:
//    Directives has defaults behaviours.



  // chaining directives with ~ -> chain character
  // you can chain other directives
  // ~ = otherwise

  val chainedRoute: Route =
    path("myEndpoint"){
      get {
        complete(StatusCodes.OK)
      } ~
      post{
        complete(StatusCodes.Forbidden)
      }
    } ~
      path("home") {
        complete(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              |<body>
              | Hello from the high level Akka HTTP!
              |</body>
              |</html>
              |""".stripMargin
          )
        )
      } //Routing tree

  //Side Note: we can also add https -> the certificates
  //Http().bindAndHandle(chainedRoute,"localhost",8080,HttpContext.httpsConnectionContext)

  Http().bindAndHandle(chainedRoute,"localhost",8080)


}
