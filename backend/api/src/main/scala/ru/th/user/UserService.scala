package ru.th.user

import cats.effect.IO

import java.util.UUID

trait UserService {
  def register(userInfo: UserInfo): IO[Unit]
  def get(id: UUID): IO[Option[UserInfo]]

}

object UserService {

}
