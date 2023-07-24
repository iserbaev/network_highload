package ru.nh.auth

import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.parser.decode
import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm
import ru.nh.auth.AuthService.{ Token, UserPassword }
import ru.nh.user.UserAccessor

import java.time.Instant
import java.util.UUID
import scala.util.Try

class AuthService(userAccessor: UserAccessor[IO]) {
  import ru.nh.http.json.all._

  private val key: String            = "secretKey"
  private val algo: JwtHmacAlgorithm = JwtAlgorithm.HS256

  private def buildToken(id: String, password: String) = {
    val claim = JwtClaim(
      content = s"""{"id":"$id", "password":"$password"}""",
      expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond),
      issuedAt = Some(Instant.now.getEpochSecond)
    )

    Token(JwtCirce.encode(claim, key, algo))
  }

  private def decodeToken(token: String): Try[UserPassword] =
    JwtCirce
      .decode(token, key, Seq(algo))
      .flatMap { claim =>
        decode[UserPassword](claim.content).toTry
      }

  def login(id: String, password: String): IO[Option[AuthService.Token]] =
    userAccessor.getUserRow(UUID.fromString(id)).map {
      _.flatMap { row =>
        Option.when(row.password == password)(buildToken(id, password))
      }
    }

  def authorize(token: String): IO[Option[AuthService.Auth]] =
    IO.fromTry(decodeToken(token)).flatMap { userPasswordFromToken =>
      userAccessor.getUserRow(UUID.fromString(userPasswordFromToken.id)).map {
        _.flatMap { row =>
          Option.when(row.password == userPasswordFromToken.password)(
            AuthService.Auth(UUID.randomUUID().toString, userPasswordFromToken.id, Set(AuthService.Role.User))
          )
        }
      }
    }
}

object AuthService {
  final case class UserPassword(id: String, password: String)
  final case class Token(token: String)
  final case class Auth(requestId: String, userId: String, roles: Set[Role])

  final case class Role(roleId: String)

  object Role {
    val Admin: Role = Role("network-highload-admin")
    val User: Role  = Role("network-highload-user")
  }

  def apply(userAccessor: UserAccessor[IO]): Resource[IO, AuthService] =
    Resource
      .pure(new AuthService(userAccessor))
}
