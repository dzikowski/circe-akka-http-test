import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.ExecutionContext
import scala.io.StdIn


object Model {

  object Roles extends Enumeration {
    type Role = Value
    val Admin, Guest = Value
  }

  case class User(roles: Seq[Roles.Value])
}

object Serialization {

  import Model._

  implicit object RoleEncoder extends Encoder[Roles.Value] {
    override def apply(r: Roles.Value): Json = r.toString.asJson
  }

  // A marker to allow serialization only of objects of given types
  trait CanBeSerialized[T]
  object CanBeSerialized {
    def apply[T] = new CanBeSerialized[T] {}
    implicit def listCanBeSerialized[T](implicit cbs: CanBeSerialized[T]): CanBeSerialized[List[T]] = null
  }

  implicit def circeMarshaller[A <: AnyRef](implicit e: Encoder[A], cbs: CanBeSerialized[A]): ToEntityMarshaller[A] = {
    Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`) {
      e(_).noSpaces
    }
  }
}

object Server {

  import Model._
  import Serialization._

  // Allows to serialize both an object of type User and List[User]
  implicit val cbs = CanBeSerialized[User]

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = system.dispatcher

    val us = List(User(Seq(Roles.Admin)))

    val route =
      path("hello1") { // ERROR: {"::":[{"roles":["Admin"]}]}
        get {
          complete(us)
        }
      } ~
        path("hello2") { // OK: [{"roles":["Admin"]}]
          get {
            implicit object RolesSetEncoder extends Encoder[Seq[Roles.Value]] {
              override def apply(rs: Seq[Roles.Value]): Json = rs.map(_.asJson).asJson
            }
            complete(us)
          }
        }


    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
