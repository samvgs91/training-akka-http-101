package part3_highlevelserver

import akka.actor.FSM.Failure

import scala.util.{Failure, Success}
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import com.sun.xml.internal.ws.util.FastInfosetUtil
import part3_highlevelserver.PersonDB.PersonCreated

import scala.concurrent.Future
import scala.concurrent.duration._


//val personDb = ActorSystem("PersonDB")

case class Person(pin:Int,name:String)

trait PersonJsonProtocol extends DefaultJsonProtocol {
  implicit val personFormat = jsonFormat2(Person)
  implicit val personCreatedFormat = jsonFormat1(PersonCreated)
}

object PersonDB {
  case class RegisterPerson(person:Person)
  case object ListPersons
  case class PersonCreated(pin:Int)
  case class FindPerson(pin:Int)
}

class PersonDB extends Actor with ActorLogging {
  import PersonDB._

  var persons: Map[Int,Person] = Map()
  var currentPersonPin: Int = 0

  override def receive: Receive = {
    case RegisterPerson(person:Person) =>
      log.info(s"Registering a new person $person with id")
      persons = persons + (person.pin -> person)
      sender() ! PersonCreated(person.pin)
//      currentPersonPin +=1
    case ListPersons =>
      log.info(s"Searching for all persons")
      sender() ! persons.values.toList
    case FindPerson(pin) =>
      log.info(s"Searching for person with pin $pin")
      sender() ! persons.get(pin)
  }
}

object HighLevelExercise extends App with PersonJsonProtocol {

  /// Importing
  import PersonDB.{FindPerson,ListPersons,RegisterPerson,PersonCreated}
  import part2_lowlevelserver.Guitar

  implicit val system = ActorSystem("HighLevelPractice")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  implicit val timeout = Timeout(2 seconds)

  val personDb = system.actorOf(Props[PersonDB],"ExercisePersonDB")

  def toHttpEntity(payload:String) = HttpEntity(ContentTypes.`application/json`,payload)

  val personServerRoute = {
    (pathPrefix("api"/"person") & get) {
      (path(IntNumber) | parameter('pin.as[Int])) { pin =>
        complete(
          (personDb ? FindPerson(pin))
            .mapTo[Option[Person]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)
        )
      } ~
        pathEndOrSingleSlash {
          complete(
            (personDb ? ListPersons)
              .mapTo[List[Person]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
    } ~
    pathPrefix("api"/"person") {
      (post & extractRequest & extractLog) {  (request,log) =>
          val entity = request.entity
          val strictEntityFuture = entity.toStrict(2 seconds)

          val personFuture: Future[Person] = strictEntityFuture.map { entity =>
                entity.data.utf8String.parseJson.convertTo[Person]
          }

//          val responsePersonCreated = personFuture
//                      .map { case person:Person =>
//                        (personDb ? RegisterPerson(person))
//                          .mapTo[PersonCreated]
//                          .map(_.toJson.prettyPrint)
//                          .map(toHttpEntity)
//                      }.map {
//
//          }

          onComplete(personFuture) {
            case Success(person: Person) =>
              log.info(s"Got person correctly $person")
              val registerResponse = (personDb ? RegisterPerson(person))
                .mapTo[PersonCreated]
                .map(_.toJson.prettyPrint)
                .map(toHttpEntity)
              complete(registerResponse)
            case ex: Exception =>
              failWith(ex)
          }

//        responsePersonCreated.onComplete {
//          case Success(entity: HttpEntity) =>
//            log.info(s"Got person correctly $entity")
//          case ex:Exception =>
//            log.warning(s"Something went wrong with $ex")
//        }
//
//          complete(responsePersonCreated.map(_=>StatusCodes.OK).recover {
//            case _=> StatusCodes.InternalServerError
//          })


        }
      }

    }


  Http().bindAndHandle(personServerRoute,"localhost",8080)
  /*
  * GET /api/people fetched all people you have registered
  * GET /api/people?pin=X retrieve the person with the PIN, return as Json
  * GET /api/people/X fetches the guitar with id X
  * POST /api/people with a Json payload denoting a person
  */



}
