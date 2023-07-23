package ru.nh.auth

import cats.effect.IO
import ru.nh.auth.AuthService.{ Auth, Token }

import java.util.UUID

trait AuthService[F[_]] {
  def login(id: String, password: String): F[Option[Token]]
  def authorize(token: String): F[Auth]
}

object AuthService {
  final case class UserPassword(id: String, password: String)
  final case class Token(token: String)
  final case class Auth(requestId: String, userId: String, roles: Set[Role])

  final case class Role(roleId: String)

  object Role {
    val Admin: Role = Role("network-highload-admin")
  }

  final case class DummyAuthService() extends AuthService[IO] {
    def login(id: String, password: String): IO[Option[Token]] =
      IO(Some(Token(id)))

    def authorize(token: String): IO[Auth] =
      IO(Auth(UUID.randomUUID().toString, token, Set(Role.Admin)))
  }
}
