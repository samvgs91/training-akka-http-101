package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol, Initializator}
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.{FindAllGuitars, FindGuitar, FindGuitarInStock}
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

object HighLevelExample extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("HighLevelIntro")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  implicit val timeout = Timeout(2 seconds)

  // directives

  /*
  * GET /api/guitar fetches ALL guitars
  * GET /api/guitar?id=X fetches the guitar with id X
  * GET /api/guitar/X fetches the guitar with id X
  * GET /api/guitar/inventory?inStock=true
  */


  import Initializator._

  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val initilizator = system.actorOf(Props[Initializator], "InitilizatorGuitarDB")

  initilizator ! Start(guitarDb)

  val guitarServerRoute = {
    path("api" / "guitar") {
      parameter('id.as[Int]) { (id: Int) =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitar =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitar.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      get {
        val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]

        val entityFuture = guitarsFuture.map { guitars =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        }
        complete(entityFuture)
      }
    } ~
      path("api" / "guitar" / IntNumber) { id =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitar =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitar.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      path("api" / "guitar" / "inventory") {
        get {
            parameter('inStock.as[Boolean]) { inStock =>
              val guitarFuture: Future[List[Guitar]] = (guitarDb ? FindGuitarInStock(inStock)).mapTo[List[Guitar]]
              val entityFuture = guitarFuture.map { guitars =>
                HttpEntity(
                  ContentTypes.`application/json`,
                  guitars.toJson.prettyPrint
                )
              }
              complete(entityFuture)
            }
        }
      }
  }

//  val simplifiedGuitarServerRoute =
//    pathPrefix("api" / "guitar") {
//      path("inventory") {
//
//      }
//    }

  def toHttpEntity(payload:String) = HttpEntity(ContentTypes.`application/json`,payload)

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter('inStock.as[Boolean]) { inStock =>
          complete(
              (guitarDb ? FindGuitarInStock(inStock))
                .mapTo[List[Guitar]]
                .map(_.toJson.prettyPrint)
                .map(toHttpEntity)
          )
        }
      } ~
      (path(IntNumber) | parameter('id.as[Int])) { id =>
        complete(
            (guitarDb ? FindGuitar(id))
                .mapTo[Option[Guitar]]
                .map(_.toJson.prettyPrint)
                .map(toHttpEntity)
        )
      } ~
      pathEndOrSingleSlash {
        complete(
            (guitarDb ? FindAllGuitars)
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
        )
      }
    }

  Http().bindAndHandle(simplifiedGuitarServerRoute,"localhost",8080)
}