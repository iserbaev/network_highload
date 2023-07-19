package ru.nh.user

import cats.effect.IO

import java.util.UUID

trait UserAccessor {
  def save(u: RegisterUserCommand): IO[User]
  def get(userId: UUID): IO[Option[User]]

}
