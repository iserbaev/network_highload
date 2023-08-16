package ru.nh.user

import cats.effect.IO

import java.util.UUID

trait UserService {
  def register(userInfo: RegisterUserCommand): IO[User]

  def get(id: UUID): IO[Option[User]]

  def search(firstNamePrefix: String, lastNamePrefix: String): IO[Option[User]]

  def addFriend(userId: UUID, friendId: UUID): IO[Unit]

  def deleteFriend(userId: UUID, friendId: UUID): IO[Unit]

  def addPost(userId: UUID, text: String): IO[UUID]

  def getPost(postId: UUID): IO[Option[(UUID, String)]]

  def updatePost(postId: UUID, text: String): IO[Unit]

  def deletePost(postId: UUID): IO[Unit]
}
