package ru.nh.user

import cats.data.Chain
import cats.effect.IO

import java.util.UUID

trait UserService {
  def register(userInfo: RegisterUserCommand): IO[User]

  def get(id: UUID): IO[Option[User]]

  def search(firstNamePrefix: String, lastNamePrefix: String): IO[Option[User]]

  def addFriend(userId: UUID, friendId: UUID): IO[Unit]

  def deleteFriend(userId: UUID, friendId: UUID): IO[Unit]

  def addPost(userId: UUID, text: String): IO[UUID]

  def getPost(postId: UUID): IO[Option[Post]]

  def updatePost(postId: UUID, text: String): IO[Unit]

  def deletePost(postId: UUID): IO[Unit]

  def postFeed(userId: UUID, offset: Int, limit: Int): IO[Chain[Post]]
}
