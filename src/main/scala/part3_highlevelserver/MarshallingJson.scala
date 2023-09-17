package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

case class Player(nickname:String, characterClass:String, level:Int)

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player)
}

object GameaAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname:String)
  case class GetPlayerByClass(characterClass:String)
  case class AddPlayer(player:Player)
  case class RemovePlayer(player:Player)
  case object OperationSuccess
}

class GameAreaMap extends Actor with ActorLogging {
  import GameaAreaMap._
  var players = Map[String,Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Getting all players")
      sender() ! players.values.toList
    case GetPlayer(nickname) =>
      log.info(s"Getting player $nickname")
      sender() ! players.get(nickname)

    case GetPlayerByClass(characterClass) =>
      log.info(s"Getting all players with class $characterClass")
      sender() ! players.values.filter(p => p.characterClass == characterClass)

    case AddPlayer(player) =>
      log.info(s"Adding player $player")
      players = players + (player.nickname-> player)
      sender() ! OperationSuccess

    case RemovePlayer(player) =>
      log.info(s"Trying to remove player $player")
      players = players - player.nickname
      sender() ! OperationSuccess
  }
}

object MarshallingJson extends App
  with PlayerJsonProtocol
  with SprayJsonSupport { //implicit converted to a json also to ResponseMarshabale
  import GameaAreaMap._

//  implicit val system = ActorSystem("MarshallingActor")
  implicit val system = ActorSystem("MarshallingActor")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val rtjvmGameMap = system.actorOf(Props[GameAreaMap],"rockTheJVMGameAreaMap")
  val playersList = List(
    Player("luthais","DemonHunter",70),
    Player("atroxxx","Warrior",55) ,
    Player("prunHunter","Hunter",70)
  )

  playersList.foreach { player =>
    rtjvmGameMap ! AddPlayer(player)
  }


  /*
  * GET /api/player, return all the players in the map, as JSON
  * GET /api/player/(nickname), returns the player with the given nickname (as Json)
  * GET /api/player?
  *
  *
  * */
 implicit  val timeout = Timeout(2 seconds)
  val server = pathPrefix("api" / "player") {
    get {
      path("class" / Segment) { characterClass =>
        val playersByClassCharacter = (rtjvmGameMap ? GetPlayerByClass(characterClass)).mapTo[List[Player]]
        complete(playersByClassCharacter)
      } ~
      (path(Segment) | parameter('nickname)) { nickname =>
        val playerByNicknameFuture = (rtjvmGameMap ? GetPlayer(nickname)).mapTo[Option[Player]]
        complete(playerByNicknameFuture)
      } ~
        pathEndOrSingleSlash {
          complete((rtjvmGameMap ? GetAllPlayers).mapTo[List[Player]])
        }
    } ~
    post {
      entity(as[Player]) { player =>
          complete((rtjvmGameMap ? AddPlayer(player)).map(_=> StatusCodes.OK))
      }
    } ~
    delete {
      entity(as[Player]) { player =>
        complete((rtjvmGameMap ? RemovePlayer(player)).map(_=> StatusCodes.OK))
      }
    }
  }

  Http().bindAndHandle(server,"localhost",8080)
}
