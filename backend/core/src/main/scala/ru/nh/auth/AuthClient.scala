package ru.nh.auth
import cats.effect.IO
import cats.syntax.all._
import io.circe.Decoder
import org.http4s.{ Method, Request, Uri }
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import ru.nh.auth.AuthService.{ Auth, Token, UserPassword }

class AuthClient(host: String, port: Int, client: Client[IO])(implicit logger: Logger[IO]) extends AuthService {
  import ru.nh.http.json.all._

  private val baseUrl =
    Uri(Uri.Scheme.http.some, Uri.Authority(host = Uri.RegName(host), port = port.some).some)

  def save(login: String, password: String, key: String): IO[Unit] = {
    val request = Request[IO](
      method = Method.POST,
      uri = baseUrl / "auth" / "save" / key
    ).withEntity(UserPassword(login, password))

    runRequest(request)
  }

  def login(id: String, password: String): IO[Option[AuthService.Token]] = {
    val request = Request[IO](
      method = Method.POST,
      uri = baseUrl / "auth" / "login"
    ).withEntity(UserPassword(id, password))

    runQueryRequest[Token](request).map(_.some)
  }

  def authorize(token: String): IO[Option[AuthService.Auth]] = {
    val request = Request[IO](
      method = Method.POST,
      uri = baseUrl / "auth" / "authorize"
    ).withEntity(Token(token))

    runQueryRequest[Auth](request).map(_.some)
  }

  private def runRequest(request: Request[IO]): IO[Unit] =
    client
      .run(request)
      .use { response =>
        if (response.status.isSuccess)
          logger
            .info(
              s"Outgoing HTTP request: status=${response.status.code} method=${request.method.name} uri=${request.uri}"
            )
        else {
          logger
            .error(
              s"Outgoing HTTP request failed: status=${response.status.code} method=${request.method.name} uri=${request.uri}"
            ) *> IO.raiseError(new Exception(s"Outgoing request failed with status=${response.status.code}"))
        }
      }

  private def runQueryRequest[Resp](request: Request[IO])(implicit decoder: Decoder[Resp]): IO[Resp] =
    client
      .run(request)
      .use { response =>
        if (response.status.isSuccess)
          logger
            .info(
              s"Outgoing HTTP request: status=${response.status.code} method=${request.method.name} uri=${request.uri}"
            ) *> response.as[Resp]
        else {
          logger
            .error(
              s"Outgoing HTTP request failed: status=${response.status.code} method=${request.method.name} uri=${request.uri}"
            ) *> IO.raiseError(new Exception(s"Outgoing request failed with status=${response.status.code}"))
        }
      }
}
