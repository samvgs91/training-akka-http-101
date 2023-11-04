package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.CompactByteString
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http
import scala.concurrent.duration._

object WebsocketsDemo extends App{

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  //Message types:
  // TextMessage and BinaryMessage (sound, images)

  val textMessage = TextMessage(Source.single("hello via a text message"))
  val binaryMessage = BinaryMessage(Source.single(CompactByteString("hello via a binary message")))

  val html =
    """
      |<html>
      |    <head>
      |        <script>
      |            var exampleSocket = new WebSocket("ws://localhost:8080/greeter");
      |            console.log("starting web socket...");
      |
      |            exampleSocket.onmessage = function(event) {
      |                var newChild = document.createElement("div");
      |                newChild.innerText = event.data;
      |                document.getElementById("1").appendChild(newChild);
      |            };
      |
      |            exampleSocket.onopen = function(event) {
      |                exampleSocket.send("socket seems to be open...");
      |            };
      |
      |            exampleSocket.send("socket says: hello, server!");
      |        </script>
      |    </head>
      |    <body>
      |        Starting websocket...
      |    <div id="1">
      |    </div>
      |    </body>
      |</html>
      |""".stripMargin

  def webSocketFlow: Flow[Message,Message,Any] = Flow[Message].map {
    case tm: TextMessage =>
      TextMessage(Source.single("Server says back:") ++ tm.textStream ++Source.single("!"))
    case bm: BinaryMessage =>
      bm.dataStream.runWith(Sink.ignore)
      TextMessage(Source.single("Server received a binary message..."))
  }

  val webSocketRoute = {
    (pathEndOrSingleSlash & get) {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          html
        )
      )
    } ~
    path("greeter") {
      //uses flow to hanlde websocket messages
      handleWebSocketMessages(socialFlow)
    }
  }
  Http().bindAndHandle(webSocketRoute,"localhost",8080)

  case class SocialPost(owner:String, content:String)
  val socialFeed = Source(
    List(
      SocialPost("Martin","Scala 3 has been announced!"),
      SocialPost("Daniel","A new course is Open!"),
      SocialPost("Martin","I killed Java.")
    )
  )

  val socialMessages = socialFeed
    .throttle(1,2 seconds)
    .map(socialPost => TextMessage(s"${socialPost.owner} said: ${socialPost.content}"))

  val socialFlow : Flow[Message,Message,Any] = Flow.fromSinkAndSource(
    Sink.foreach[Message](println),socialMessages
  )
}
