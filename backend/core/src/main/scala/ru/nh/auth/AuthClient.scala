package ru.nh.auth

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{ Method, Request, Uri }
import org.typelevel.log4cats.LoggerFactory
import ru.nh.auth.AuthClient.LoginRequest
import ru.nh.auth.AuthService.{ Auth, Token, UserPassword }
import ru.nh.cache.{ AsyncCache, Caffeine }
import ru.nh.http.ClientsSupport

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

class AuthClient(
    host: String,
    port: Int,
    clientsSupport: ClientsSupport,
    tokensCache: AsyncCache[LoginRequest, AuthService.Token]
) extends AuthService {
  import ru.nh.http.json.all._

  private val baseUrl =
    Uri(Uri.Scheme.http.some, Uri.Authority(host = Uri.RegName(host), port = port.some).some)

  def save(login: String, password: String, key: String): IO[Unit] = {
    val request = Request[IO](
      method = Method.POST,
      uri = baseUrl / "auth" / "save" / key
    ).withEntity(UserPassword(login, password))

    clientsSupport.runRequest(request)
  }

  def login(id: String, password: String): IO[Option[AuthService.Token]] = {
    def runRequest(id: String, password: String) = {
      val request = Request[IO](
        method = Method.POST,
        uri = baseUrl / "auth" / "login"
      ).withEntity(UserPassword(id, password))

      clientsSupport.runQueryRequest[Token](request)
    }

    tokensCache.getF(LoginRequest(id, password), req => runRequest(req.id, req.password)).map(_.some)
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
  final case class LoginRequest(id: String, password: String)

  def resource(host: String, port: Int, cacheInvalidationTtl: FiniteDuration)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, AuthService] =
    (
      Caffeine().expireAfterWrite(cacheInvalidationTtl).buildAsync[LoginRequest, AuthService.Token],
      Resource.eval(L.fromClass(classOf[AuthClient]))
    )
      .flatMapN { (cache, log) =>
        ClientsSupport.createClient(log).map(c => new AuthClient(host, port, c, cache))
      }

  def resource(host: String, port: Int)(implicit L: LoggerFactory[IO]): Resource[IO, AuthService] =
    resource(host, port, 5.minute)

}
