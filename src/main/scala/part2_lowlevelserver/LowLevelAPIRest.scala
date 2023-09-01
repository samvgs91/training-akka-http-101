package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

case class Guitar(make:String, model:String, stock: Int = 0)

object Initializator {
  case class Start(guitarsdb: ActorRef)
}

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id:Int)
  case class FindGuitar(id:Int)
  case object FindAllGuitars
  case class AddQuantity(id:Int, quantity:Int)
  case class FindGuitarInStock(inStock: Boolean)
}

class Initializator extends Actor with ActorLogging{
  import GuitarDB._
  import Initializator._

  override def receive: Receive = {
    case GuitarCreated(id) => println(s" Guitar created with id: $id")

    case Start(guitarDb) =>

      val guitarList = List(
        Guitar("Fender","Stratocaster"),
        Guitar("Gibson","Less Paul"),
        Guitar("Martin","LX1")
      )
      guitarList.foreach { guitar =>
        guitarDb ! CreateGuitar(guitar)
      }
  }

}

class GuitarDB extends Actor with ActorLogging{
  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList

    case FindGuitar(id) =>
      log.info(s"Searching guitar by id:$id")
      sender() ! guitars.get(id)

    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar $guitar with id")
      guitars = guitars+ (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1

    case GuitarCreated(currentGuitarId) => log.info(s"Guitar was created with id: $currentGuitarId ")

    case AddQuantity(id,quantity) =>
      log.info(s"Trying to add $quantity items for guitar $id")
      val guitar: Option[Guitar] = guitars.get(id)
      val newGuitar: Option[Guitar] =
          guitar.map {
            case Guitar(make,model,q) => Guitar(make,model,q+quantity)
          }

      newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))
      sender() ! newGuitar // this return None if no matching an Id. This also helps in http request section.

    case FindGuitarInStock(inStock) =>
      log.info(s"Searching for all guitars ${if(inStock) "in" else "out of"} stock")
      if (inStock)
         sender() ! guitars.values.filter(_.stock > 0)
      else
         sender() ! guitars.values.filter(_.stock == 0)

  }

}
// step 2: define the trait
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // step 3: define your implicit format
  implicit val guitarFormat = jsonFormat3(Guitar)
}

object LowLevelAPIRest extends App with GuitarStoreJsonProtocol {

  import part2_lowlevelserver.GuitarDB.{CreateGuitar, FindAllGuitars, FindGuitar, GuitarCreated , AddQuantity, FindGuitarInStock}
  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher


 /*
   GET on localhost:8080/api/guitar => ALL the guitars in the store
   POST on localhost:8080/api/guitar => insert the guitar into the store
 * */

  //JSON -> Marshalling
  val simpleGuitar = Guitar("Fender","Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  //unmarshalling
  val simpleGuitarJsonString =
    """
      |{
      |   "make": "Fender",
      |   "model": "Stratocaster",
      |   "stock": 3
      |}
      |""".stripMargin

  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  /*
   setup
  */
  import Initializator._

  val guitarDb = system.actorOf(Props[GuitarDB],"LowLevelGuitarDB")
  val initilizator = system.actorOf(Props[Initializator],"InitilizatorGuitarDB")

  initilizator ! Start(guitarDb)

  /*
    server code
  */

  implicit val defaultTimeout = Timeout(10 seconds)

  def getGuitar(query:Query):Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt) // Option[Int]

    guitarId match { // validate if I get the
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id) =>
        val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) => HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitar.toJson.prettyPrint
            )
          )
        }
    }
  }



  val requestHandler: HttpRequest => Future[HttpResponse] = {

    case HttpRequest(HttpMethods.POST,uri@Uri.Path("/api/guitar/inventory"),_,_,_) =>
      val query = uri.query()
      val guitarId: Option[Int] = query.get("id").map(_.toInt) //parse the guitar id
      val guitarQuantity: Option[Int] =  query.get("quantity").map(_.toInt) //parse the guitar new quantity to be added.

      val validGuitarResponseFuture: Option[Future[HttpResponse]]  = for {
          id <- guitarId
          quantity <- guitarQuantity
      } yield {
        //TODO construct an HTTP response
        val newGuitarFuture: Future[Option[Guitar]] = (guitarDb ? AddQuantity(id,quantity)).mapTo[Option[Guitar]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }

      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case HttpRequest(HttpMethods.GET,uri@Uri.Path("/api/guitar/inventory"),_,_,_) =>
      val query = uri.query()
      val inStock: Option[Boolean] = query.get("inStock").map(_.toBoolean) //parse to boolean

      inStock match {
        case Some(value) =>
          val newGuitarFuture:Future[List[Guitar]]= (guitarDb ? FindGuitarInStock(value)).mapTo[List[Guitar]]

          newGuitarFuture.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }
        case None => Future(HttpResponse(StatusCodes.BadRequest))

      }





    case HttpRequest(HttpMethods.GET,uri@Uri.Path("/api/guitar"),_,_,_) =>
      /*
      * query parameters handling code
      */

      val query = uri.query() // query object <=> Map[String,String]
      if (query.isEmpty) {
          val  guitarsFuture:Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
          guitarsFuture.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }
        } else
        {
           //fetch guitar associated to the guitar id
           //localhost:8080/api/guitar?id=45
          getGuitar(query)
        }


    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"),_,entity,_) =>
      // entities are a source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }


    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status= StatusCodes.NotFound)
      }
    }

   Http().bindAndHandleAsync(requestHandler,"localhost",8081)




}
