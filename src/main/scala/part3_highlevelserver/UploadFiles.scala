package part3_highlevelserver

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import java.io.File
import scala.concurrent.Future
import scala.util.{Failure, Success}

object UploadFiles extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher


  val fileRoute = {
    (pathEndOrSingleSlash & get) {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   <form action="http://localhost:8080/upload" method="post" enctype="multipart/form-data">
            |     <input type="file" name="myFile">
            |     <button type="submit">Upload</button>
            |   </form>
            | </body>
            |</html>
            |""".stripMargin
        )
      )
    }~
    (path("upload") & extractLog ) { log =>
      // handle upload files
      // multipart/form-data
      entity(as[Multipart.FormData]) { formdata =>
        val partsSource: Source[Multipart.FormData.BodyPart,Any] = formdata.parts
        val filePartsSink: Sink[Multipart.FormData.BodyPart,Future[Done]] =
          Sink.foreach[Multipart.FormData.BodyPart] { bodyPart =>
             if (bodyPart.name == "myFile") {
               //create a file
               val filename = "src/main/resources/downloads" + bodyPart.filename.getOrElse("tempFile_"+System.currentTimeMillis())
               val file = new File(filename)
               log.info(s"Writing to file: ${filename}")

               val fileContentSource: Source[ByteString,_] = bodyPart.entity.dataBytes
               val fileContentsSink: Sink[ByteString,_] = FileIO.toPath(file.toPath) //you can change this...(down)
               //...an actual implementation to an more specific production environment.

               fileContentSource.runWith(fileContentsSink)

             }
          }

        val writeOperationFuture = partsSource.runWith(filePartsSink)
          onComplete(writeOperationFuture) {
            case Success(_) => complete("File uploaded.")
            case Failure(ex) => complete(s"File failed to upload ${ex}")
          }
      }


    }
  }

  Http().bindAndHandle(fileRoute,"localhost",8080)
}
