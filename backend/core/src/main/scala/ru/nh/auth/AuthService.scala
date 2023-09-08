package ru.nh.auth

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import io.circe.parser.decode
import org.http4s.blaze.client.BlazeClientBuilder
import org.typelevel.log4cats.LoggerFactory
import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm
import ru.nh.auth.AuthService.{ Token, UserPassword }

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._
import scala.util.Try

trait AuthService {
  def save(login: String, password: String, key: String): IO[Unit]
  def login(id: String, password: String): IO[Option[AuthService.Token]]

  def authorize(token: String): IO[Option[AuthService.Auth]]
}
private[auth] class AuthServiceImpl(applicationKey: String, loginAccessor: LoginAccessor[IO]) extends AuthService {
  import ru.nh.http.json.all._

  private val algoKey: String        = "secretKey"
  private val algo: JwtHmacAlgorithm = JwtAlgorithm.HS256

  private def buildToken(id: String, password: String) = {
    val claim = JwtClaim(
      content = s"""{"id":"$id", "password":"$password"}""",
      expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond),
      issuedAt = Some(Instant.now.getEpochSecond)
    )

    Token(JwtCirce.encode(claim, algoKey, algo))
  }

  private def decodeToken(token: String): Try[UserPassword] =
    JwtCirce
      .decode(token, algoKey, Seq(algo))
      .flatMap { claim =>
        decode[UserPassword](claim.content).toTry
      }

  def save(login: String, password: String, key: String): IO[Unit] =
    loginAccessor.save(login, password).whenA(key == applicationKey)

  def login(id: String, password: String): IO[Option[AuthService.Token]] =
    loginAccessor.get(id).map {
      _.flatMap { row =>
        Option.when(row.password == password)(buildToken(id, password))
      }
    }

  def authorize(token: String): IO[Option[AuthService.Auth]] =
    IO.fromTry(decodeToken(token)).flatMap { userPasswordFromToken =>
      loginAccessor.get(userPasswordFromToken.id).map {
        _.flatMap { row =>
          Option.when(row.password == userPasswordFromToken.password)(
            AuthService
              .Auth(UUID.randomUUID().toString, userPasswordFromToken.id, Set(AuthService.Role.User).mkString(","))
          )
        }
      }
    }
}

object AuthService {
  final case class UserPassword(id: String, password: String)
  final case class Token(token: String)
  final case class Auth(requestId: String, userId: String, roles: String)

  final case class Role(roleId: String)

  object Role {
    val Admin: Role = Role("network-highload-admin")
    val User: Role  = Role("network-highload-user")
  }

  def apply(key: String, loginAccessor: LoginAccessor[IO]): Resource[IO, AuthService] =
    Resource
      .pure(new AuthServiceImpl(key, loginAccessor))

  def client(host: String, port: Int)(implicit L: LoggerFactory[IO]): Resource[IO, AuthService] = Resource.suspend {
    L.fromClass(classOf[AuthClient]).map { implicit log =>
      createClient.map(c => new AuthClient(host, port, c))
    }
  }

  private def createClient =
    BlazeClientBuilder[IO]
      .withRequestTimeout(180.seconds)
      .withResponseHeaderTimeout(170.seconds)
      .withIdleTimeout(190.seconds)
      .withMaxWaitQueueLimit(1024)
      .resource
}
