package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.{CreateGuitar, FindAllGuitars}
import part2_lowlevelserver.LowLevelAPIRest.guitarDb
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

object test_restapi extends App{

  implicit val system = ActorSystem("PractiLowLevelAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher


  /*
    server code
  */

  implicit val defaultTimeout = Timeout(10 seconds)

  val requestHandler: HttpRequest => Future[HttpResponse] = {
//    case HttpRequest(HttpMethods.GET,Uri.Path("/api/guitars"),_,_,_) =>
//      val  guitarsFuture:Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
//
//      guitarsFuture.map { guitars =>
//        HttpResponse(
//          entity = HttpEntity(
//            ContentTypes.`application/json`,
//            guitars.toJson.prettyPrint
//          )
//        )
//      }
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status= StatusCodes.NotFound)
      }
  }

  Http().bindAndHandleAsync(requestHandler,"localhost",8081)



}
