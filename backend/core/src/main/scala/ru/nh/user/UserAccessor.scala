package ru.nh.user

import cats.data.Chain
import cats.{ Functor, Reducible, ~> }
import ru.nh.user.UserAccessor.{ PostRow, UserAccessorMapK, UserRow }

import java.time.{ Instant, LocalDate }
import java.util.UUID

trait UserAccessor[F[_]] {
  def save(u: RegisterUserCommand): F[UserRow]

  def saveBatch[R[_]: Reducible: Functor](u: R[RegisterUserCommand]): F[Unit]
  def getUserRow(userId: UUID): F[Option[UserRow]]

  def getUser(userId: UUID): F[Option[User]]

  def getHobbies(userId: UUID): F[List[String]]

  def search(firstNamePrefix: String, lastNamePrefix: String): F[Option[UserRow]]

  def addFriend(userId: UUID, friendId: UUID): F[Unit]

  def deleteFriend(userId: UUID, friendId: UUID): F[Unit]

  def addPost(userId: UUID, text: String): F[UUID]
  def getPost(postId: UUID): F[Option[PostRow]]
  def updatePost(postId: UUID, text: String): F[Unit]
  def deletePost(postId: UUID): F[Unit]

  def postFeed(userId: UUID, offset: Int, limit: Int): F[Chain[PostRow]]

  def mapK[G[_]](read: F ~> G, write: F ~> G): UserAccessor[G] =
    new UserAccessorMapK(this, read, write)
}

object UserAccessor {
  final case class UserRow(
      userId: UUID,
      createdAt: Instant,
      name: String,
      surname: String,
      age: Int,
      city: String,
      password: String,
      gender: Option[String],
      biography: Option[String],
      birthdate: Option[LocalDate]
  ) {
    def toUser(hobbies: List[String]): User =
      User(userId, name, surname, age, city, gender, birthdate, biography, hobbies)
  }

  final case class PostRow(
      userId: UUID,
      postId: UUID,
      createdAt: Instant,
      text: String
  ) {
    def toPost: Post =
      Post(postId, text, userId, createdAt)
  }
  object PostRow {
    implicit val ordering: Ordering[PostRow] = Ordering.by[PostRow, Instant](_.createdAt)
  }

  private[user] final class UserAccessorMapK[F[_], G[_]](underlying: UserAccessor[F], read: F ~> G, write: F ~> G)
      extends UserAccessor[G] {
    def save(u: RegisterUserCommand): G[UserRow] =
      write(underlying.save(u))

    def saveBatch[R[_]: Reducible: Functor](u: R[RegisterUserCommand]): G[Unit] =
      write(underlying.saveBatch(u))

    def getUserRow(userId: UUID): G[Option[UserRow]] =
      read(underlying.getUserRow(userId))

    def getUser(userId: UUID): G[Option[User]] =
      read(underlying.getUser(userId))

    def getHobbies(userId: UUID): G[List[String]] =
      read(underlying.getHobbies(userId))

    def search(firstNamePrefix: String, lastNamePrefix: String): G[Option[UserRow]] =
      read(underlying.search(firstNamePrefix, lastNamePrefix))

    def addFriend(userId: UUID, friendId: UUID): G[Unit] =
      write(underlying.addFriend(userId, friendId))

    def deleteFriend(userId: UUID, friendId: UUID): G[Unit] =
      write(underlying.deleteFriend(userId, friendId))

    def addPost(userId: UUID, text: String): G[UUID] =
      write(underlying.addPost(userId, text))

    def getPost(postId: UUID): G[Option[PostRow]] =
      read(underlying.getPost(postId))

    def updatePost(postId: UUID, text: String): G[Unit] =
      write(underlying.updatePost(postId, text))

    def deletePost(postId: UUID): G[Unit] =
      write(underlying.deletePost(postId))

    def postFeed(userId: UUID, offset: Int, limit: Int): G[Chain[PostRow]] =
      read(underlying.postFeed(userId, offset, limit))
  }
}
