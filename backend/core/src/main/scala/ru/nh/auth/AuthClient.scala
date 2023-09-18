package ru.nh.auth

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.{ Method, Request, Uri }
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService.{ Auth, Token, UserPassword }
import ru.nh.http.ClientsSupport

class AuthClient(host: String, port: Int, client: Client[IO])(implicit logger: Logger[IO]) extends AuthService {
  import ru.nh.http.json.all._

  private val baseUrl =
    Uri(Uri.Scheme.http.some, Uri.Authority(host = Uri.RegName(host), port = port.some).some)

  private val clientsSupport = new ClientsSupport(client)

  def save(login: String, password: String, key: String): IO[Unit] = {
    val request = Request[IO](
      method = Method.POST,
      uri = baseUrl / "auth" / "save" / key
    ).withEntity(UserPassword(login, password))

    clientsSupport.runRequest(request)
  }

  def login(id: String, password: String): IO[Option[AuthService.Token]] = {
    val request = Request[IO](
      method = Method.POST,
      uri = baseUrl / "auth" / "login"
    ).withEntity(UserPassword(id, password))

    clientsSupport.runQueryRequest[Token](request).map(_.some)
  }

  def authorize(token: String): IO[Option[AuthService.Auth]] = {
    val request = Request[IO](
      method = Method.POST,
      uri = baseUrl / "auth" / "authorize"
    ).withEntity(Token(token))

    clientsSupport.runQueryRequest[Auth](request).map(_.some)
  }
}

object AuthClient {
  def resource(host: String, port: Int)(implicit L: LoggerFactory[IO]): Resource[IO, AuthService] = Resource.suspend {
    L.fromClass(classOf[AuthClient]).map { implicit log =>
      ClientsSupport.createClient.map(c => new AuthClient(host, port, c))
    }
  }
}
