package ru.nh.auth

import ru.nh.auth.AuthService.{ Auth, Token }

trait AuthService[F[_]] {
  def register(id: String, password: String): F[Unit]

  def login(id: String, password: String): F[Option[Token]]
  def authorize(token: String): F[Option[Auth]]
}

object AuthService {
  final case class UserPassword(id: String, password: String)
  final case class Token(token: String)
  final case class Auth(requestId: String, userId: String, roles: Set[Role])

  final case class Role(roleId: String)

  object Role {
    val Admin: Role = Role("network-highload-admin")
  }
}
