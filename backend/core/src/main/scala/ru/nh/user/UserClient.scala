package ru.nh.user

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.http4s.{ Method, Request, Uri }
import org.typelevel.log4cats.LoggerFactory
import ru.nh.http.ClientsSupport

import java.util.UUID

class UserClient(host: String, port: Int, clientsSupport: ClientsSupport) {

  private val baseUrl =
    Uri(Uri.Scheme.http.some, Uri.Authority(host = Uri.RegName(host), port = port.some).some)

  def getFriends(userId: UUID, token: String): IO[List[UUID]] = {
    val request = Request[IO](
      method = Method.GET,
      uri = baseUrl / "user" / "friend" / "get" / userId,
      headers = clientsSupport.jwtHeader(token)
    )

    clientsSupport.runQueryRequest[List[UUID]](request)
  }
}

object UserClient {
  def resource(host: String, port: Int)(implicit L: LoggerFactory[IO]): Resource[IO, UserClient] = Resource.suspend {
    L.fromClass(classOf[UserClient]).map { implicit log =>
      ClientsSupport.createClient.map(c => new UserClient(host, port, c))
    }
  }
}
