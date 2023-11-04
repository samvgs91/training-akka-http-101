package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler

object HandlingException extends App {

  implicit val system = ActorSystem("HandlingException")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleRoute =
    path("api" / "people") {
      get {
        throw new RuntimeException("Gettingl all the people that take too long")
      }~
      post {
        parameter('id) { id =>
          if (id.length > 2)
            throw new NoSuchElementException(s"Parameter $id cannot be found in the database.TABLE FLIP!")

          complete(StatusCodes.OK)
        }
//        throw new RuntimeException("Gettingl all the people that take too long")
      }
    }

  implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound,e.getMessage)
    case e: IllegalArgumentException =>
      complete(StatusCodes.BadRequest,e.getMessage)
  }

   Http().bindAndHandle(simpleRoute,"localhost",8080)



  val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
  }

  val noSuchElementExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e:NoSuchElementException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

   // exceptions will bubleup all the errors in the nested path not like the rejection that will create a list of rejections.
  val delicateHandleRoute =
      handleExceptions(runtimeExceptionHandler) {
        path("api" / "people") {
          get {
            throw new RuntimeException("Gettingl all the people that take too long")
          }~
          handleExceptions(noSuchElementExceptionHandler) {
            post {
              parameter('id) { id =>
                if (id.length > 2)
                  throw new NoSuchElementException(s"Parameter $id cannot be found in the database.TABLE FLIP!")

                complete(StatusCodes.OK)
              }
              //        throw new RuntimeException("Gettingl all the people that take too long")
            }
          }
        }
      }

  Http().bindAndHandle(delicateHandleRoute,"localhost",8080)


}
