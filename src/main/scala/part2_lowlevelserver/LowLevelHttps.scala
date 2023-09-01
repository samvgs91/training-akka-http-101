package part2_lowlevelserver

import java.security.{KeyStore, SecureRandom}
import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.io.Tcp.Connect
import akka.stream.ActorMaterializer
import part2_lowlevelserver.LowLevelHttps.getClass

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import java.io.InputStream


object HttpContext {

  //Step 1: key store
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystoreFile: InputStream  = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  // alternative: new FileInputStream(new File("src/main/resources/keystore.pkcs12"))
  val password = "akka-https".toCharArray //always fetch password from secure place!
  ks.load(keystoreFile,password) // this keystore will obtain the encoded certificates that we will use

  //Step 2: Initialize a key manager
  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509") //Public Key infrastructure.
  keyManagerFactory.init(ks,password)

  //Step 3: initialize a trust manager -> he manages who sign those certificates.
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  //Step 4: initialize an SSL context
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  //Step 5: return the https connection context
  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.https(sslContext)

}

object LowLevelHttps extends App{

  implicit val system = ActorSystem("LowLevelHttps")
  implicit val materializer = ActorMaterializer()


  //Note: check note JVM security course.

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET,_,_,_,_) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
          """.stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Resource can't be found.
            | </body>
            |</html>
          """.stripMargin)
      )
  }

  val httpsBinding = Http().bindAndHandleSync(requestHandler,"localhost",8443, HttpContext.httpsConnectionContext)
//  val httpsBinding2 = Http().bindAndHandleSync(requestHandler,"localhost",8081)

}
