package ru.nh.auth

import cats.~>
import ru.nh.auth.LoginAccessor.{ LoginAccessorMapK, LoginRow }

import java.time.Instant

trait LoginAccessor[F[_]] {
  def save(login: String, password: String): F[Unit]

  def get(login: String): F[Option[LoginRow]]

  def mapK[G[_]](read: F ~> G, write: F ~> G): LoginAccessor[G] =
    new LoginAccessorMapK(this, read, write)
}

object LoginAccessor {
  final case class LoginRow(login: String, password: String, createdAt: Instant)
  private[auth] final class LoginAccessorMapK[F[_], G[_]](underlying: LoginAccessor[F], read: F ~> G, write: F ~> G)
      extends LoginAccessor[G] {
    def save(login: String, password: String): G[Unit] =
      write(underlying.save(login, password))

    def get(login: String): G[Option[LoginRow]] =
      read(underlying.get(login))
  }
}
