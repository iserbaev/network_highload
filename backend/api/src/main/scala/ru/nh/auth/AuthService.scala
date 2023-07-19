package ru.nh.auth

import cats.effect.IO
import ru.nh.auth.AuthService.Auth

import java.util.UUID

trait AuthService {
  def authorize(token: String): IO[Auth]
}

object AuthService {
  final case class Auth(userId: String, roles: Set[Role])

  final case class Role(roleId: String)

  object Role {
    val Admin: Role = Role("network-highload-admin")
  }

  final case class DummyAuthService() extends AuthService {
    def authorize(token: String): IO[Auth] =
      IO(Auth(UUID.randomUUID().toString, Set(Role.Admin)))
  }
}
