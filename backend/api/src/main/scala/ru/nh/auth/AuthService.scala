package ru.nh.auth

import cats.effect.IO
import ru.nh.auth.AuthService.Auth

trait AuthService {
  def login(id: String, password: String): IO[String]
  def authorize(token: String): IO[Auth]
}

object AuthService {
  final case class Auth(userId: String, roles: Set[Role])

  final case class Role(roleId: String)

  object Role {
    val Admin: Role = Role("network-highload-admin")
  }

  final case class DummyAuthService() extends AuthService {
    def login(id: String, password: String): IO[String] =
      IO(id)

    def authorize(token: String): IO[Auth] =
      IO(Auth(token, Set(Role.Admin)))
  }
}
