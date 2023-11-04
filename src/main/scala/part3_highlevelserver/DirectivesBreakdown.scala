package part3_highlevelserver

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer

object DirectivesBreakdown extends App{

  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /**
   *  Como obtner la virguilla "~" -> "Alt gr" + "+"
   * */


  /**
   * Type #1: filtering directives
   */

  val simpleHttpMethodRoute =
    post { // equivalent directives for get, put, path, delete, head, options
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute = {
    path("about") {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |  Hello from the about page!
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    }
  }

  val complexPathRoute =
    path("api" / "myEndpoint") {
      complete(StatusCodes.OK)
    } // /api/myEndpoint

  val dontConfuse =
    path("api/myEndpoint") {
      complete(StatusCodes.OK)
    }

  val pathEndRoute =
    pathEndOrSingleSlash { //localhost:8080 OR localhost:8080/
      complete(StatusCodes.OK)
    }

  //Http().bindAndHandle(pathEndRoute,"localhost",8080)


  /**
   * Type #2: extraction directives
   * */

    // GET on api/item/42
  val pathExtractionRoute =
    path("api" / "item" /IntNumber) { (intNumber:Int) =>
      // other directives
      println(s"I've got a number in my path:$intNumber")
      complete(StatusCodes.OK)
    }

  val pathMultiExtractRoute =
    path("api" / "order" / IntNumber / IntNumber) { (id,inventory) =>
      println(s"I've got TWO numbers : $id and $inventory")
      complete(StatusCodes.OK)
    }

  val queryParamExtractRoute =
    path("api" / "item") {

      //parameter("id") { (itemId) =>
      //convert paramter type safe -->
      //parameter("id".as[Int]) { (itemId:Int) =>
      // simbols in scala? 'id ->better performance by reference equialty instead of content comparison.
      parameter('id.as[Int]) { (itemId:Int) =>
          println(s"I've got the parameter ID : $itemId")
          complete(StatusCodes.OK)
      }
    }

  val extractRequestRoute =
    path("controlEndpoint") {
      extractRequest { (httpRequest: HttpRequest) =>
        extractLog { (log: LoggingAdapter) =>
          log.info(s"Log->I've got the http request: $httpRequest")
          println(s"I've got the http request: $httpRequest")
          complete(StatusCodes.OK)
        }
      }
    }




  //Http().bindAndHandle(extractRequestRoute,"localhost",8080)

  /**
   * Type #3: composite directives
   * */

  val simpleNestedRoute =
    path("api" / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }

  val compactSimpleNestedRoute = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute =
    (path("controlEndpoint") & extractRequest & extractLog) { (request,log) =>
      log.info(s"Request info: $request")
      complete(StatusCodes.OK)
    }

 // about | aboutUs
  val repeatRoute =
    path("about") {
      complete(StatusCodes.OK)
    } ~
    path("aboutUs") {
      complete(StatusCodes.OK)
    }

  val dryRoute =
    (path("about") | path("aboutUs")) {
      complete(StatusCodes.OK)
    }

  //yourblog.com/42 AND yourblog.com?postId=42

  val blogByIdRoute =
    path(IntNumber) { (blogpostId:Int) =>
      //some complex server logic
      complete(StatusCodes.OK)
    }

  val blogByQueryParamRoute =
    parameter('postId.as[Int]) { (blogpostId:Int) =>
      //some complex server logic
      complete(StatusCodes.OK)
    }

  val combinedBlogByIdRoute =
    (path(IntNumber)  | parameter('postId.as[Int])){ (blogpostId:Int) =>
      //some complex server logic
      complete(StatusCodes.OK)
    }

  /**
   * Type #4: "actionable" directives
   * */

  val completeOkRoute = complete(StatusCodes.OK)

  val failedRoute =
    path("notSupported") {
      failWith(new RuntimeException("Unsupported!!")) //complete with a 500 error
    }

  val routeWithRejection = {
//    path("home") {
//      reject
//    } ~
    path("index") {
      completeOkRoute
    }
  }
}
