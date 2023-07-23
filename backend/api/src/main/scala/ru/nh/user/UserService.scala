package ru.nh.user

import cats.effect.IO

import java.util.UUID

trait UserService {
  def register(userInfo: RegisterUserCommand): IO[Unit]

  def get(id: UUID): IO[Option[User]]

}
