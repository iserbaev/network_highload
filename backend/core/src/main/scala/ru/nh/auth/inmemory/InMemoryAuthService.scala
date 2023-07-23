package ru.nh.auth.inmemory

import cats.effect.kernel.Resource
import cats.effect.{ IO, Ref }
import io.circe.parser.decode
import pdi.jwt._
import ru.nh.auth.AuthService
import ru.nh.auth.AuthService.{ Token, UserPassword }

import java.time.Instant
import java.util.UUID
import scala.util.Try

class InMemoryAuthService private (userPasswords: Ref[IO, Map[String, String]]) extends AuthService[IO] {
  import ru.nh.http.json.all._

  private val key: String        = "secretKey"
  private val algo: JwtAlgorithm = JwtAlgorithm.HS256

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
      .decode(token)
      .flatMap { claim =>
        decode[UserPassword](claim.content).toTry
      }

  def register(id: String, password: String): IO[Unit] =
    userPasswords.update(_.updated(id, password))

  def login(id: String, password: String): IO[Option[AuthService.Token]] =
    userPasswords.get.map(_.get(id).filter(_ == password).map { _ =>
      buildToken(id, password)
    })

  def authorize(token: String): IO[Option[AuthService.Auth]] =
    IO.fromTry(decodeToken(token)).flatMap { up =>
      userPasswords.get
        .map(_.get(up.id).filter(_ == up.password))
        .map(_.map(_ => AuthService.Auth(UUID.randomUUID().toString, up.id, Set(AuthService.Role.Admin))))
    }
}

object InMemoryAuthService {
  def apply(): Resource[IO, AuthService[IO]] =
    Resource
      .eval(IO.ref(Map.empty[String, String]))
      .map(new InMemoryAuthService(_))
}
