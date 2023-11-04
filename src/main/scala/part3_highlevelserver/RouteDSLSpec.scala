package part3_highlevelserver
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Uri.Empty
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import spray.json._
import akka.pattern.ask
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

case class Book(id: Int,author: String, title:String)

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat = jsonFormat3(Book)
}

class RouteDSLSpec extends WordSpec with Matchers with ScalatestRouteTest with BookJsonProtocol {

  import RouteDSLSpec._
  "A digital library backend" should {
    "return all the books in the library" in {
      // send an HTTP request through an endpoint that you want to test
      // inspect the response
      Get("/api/book") ~>  libraryRoute ~> check {
        status shouldBe StatusCodes.OK

        entityAs[List[Book]] shouldBe books
      }
    }

    "return a book by hitting a query parameter endpoint" in {
      Get("/api/book?id=2") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Option[Book]] shouldBe Some(Book(2,"JRR Tolkien","The Lord of the Rings"))
      }
    }

    "return a book by hitting the id in the path" in {
        Get("/api/book/2")  ~> libraryRoute ~> check {

          val strictEntityFuture = response.entity.toStrict(1 second)
          val strictEntity = Await.result(strictEntityFuture, 1 second)

          strictEntity.contentType shouldBe ContentTypes.`application/json`

          val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
            book shouldBe Some(Book(2,"JRR Tolkien","The Lord of the Rings"))
        }
      }

    "post should insert book in database" in {
      val newBook = Book(5,"Steven Pressfield","The art of war")
      Post("/api/book",newBook) ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        assert(books.contains(newBook))

        books should contain(newBook) //same
      }
    }

    "not accept other methods than POST and GET" in {
      Delete("/api/book") ~> libraryRoute ~> check {
        val emptyVal = Seq()
        rejections should not be Seq()
        rejections should(not).be(Seq())
      }
    }

    "return all the books of a given author" in {
      Get("/api/book/author/JRR%20Tolkien") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books.filter(_.author == "JRR Tolkien")
      }
    }

    }
}

object RouteDSLSpec extends BookJsonProtocol  with SprayJsonSupport{
   var books = List(
     Book(1,"Harper Lee","To Kill a Mockingbird"),
     Book(2,"JRR Tolkien","The Lord of the Rings"),
     Book(3,"GRR Marting","A song of Ice and Fire"),
     Book(4,"Tony Robbins","Awaken the Giant Within"),
   )

  /*
  * GET /api/books all books.
  * GET /api/books/X getting book with X id.
  * GET /api/book?id=X - same
  * GET /api/book - adds a new book to the library
  * */

  val libraryRoute =
    pathPrefix("api" / "book") {
      (path("author" / Segment) & get) { author =>
        complete(books.filter(_.author == author))
      } ~
      get {
        (path(IntNumber) | parameter('id.as[Int])) { id =>
            complete(books.find(_.id ==id))
        }~
        pathEndOrSingleSlash{
          complete(books)
        }
      } ~
      post {
        entity(as[Book]) { book =>
          books = books :+ book
          complete(StatusCodes.OK)
        }~
          complete(StatusCodes.BadRequest)
      }

    }

}