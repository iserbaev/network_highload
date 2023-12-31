package ru.nh

import cats.effect.IO

import java.util.UUID

trait UserService {
  def register(userInfo: RegisterUserCommand): IO[User]

  def get(id: UUID): IO[Option[User]]

  def search(firstNamePrefix: String, lastNamePrefix: String, limit: Int): IO[List[User]]

  def addFriends(f: Friends): IO[Unit]

  def getFriends(userId: UUID): IO[List[Friends]]

  def deleteFriend(f: Friends): IO[Unit]
}
