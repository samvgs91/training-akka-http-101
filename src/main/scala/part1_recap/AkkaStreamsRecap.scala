package part1_recap

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow,Source,Sink}

object AkkaStreamsRecap extends App{
 implicit val system = ActorSystem("AkkaStreamRecap")
  implicit val meterializer = ActorMaterializer() //-> construct akka stream components.

  val source = Source(1 to 100)
  val sink = Sink.foreach[Int](println)
  val flow = Flow[Int].map(x => x + 1)


}
