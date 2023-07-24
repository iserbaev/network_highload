package ru.nh.user

import cats.~>
import ru.nh.user.UserAccessor.{ UserAccessorMapK, UserRow }

import java.time.Instant
import java.util.UUID

trait UserAccessor[F[_]] {
  def save(u: RegisterUserCommand): F[UserRow]
  def getUserRow(userId: UUID): F[Option[UserRow]]

  def getUser(userId: UUID): F[Option[User]]

  def getHobbies(userId: UUID): F[List[String]]

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
      gender: String,
      city: String,
      password: String
  ) {
    def toUser(hobbies: List[String]): User =
      User(userId, name, surname, age, gender, hobbies, city)
  }

  private[user] final class UserAccessorMapK[F[_], G[_]](underlying: UserAccessor[F], read: F ~> G, write: F ~> G)
      extends UserAccessor[G] {
    def save(u: RegisterUserCommand): G[UserRow] =
      write(underlying.save(u))

    def getUserRow(userId: UUID): G[Option[UserRow]] =
      read(underlying.getUserRow(userId))

    def getUser(userId: UUID): G[Option[User]] =
      read(underlying.getUser(userId))

    def getHobbies(userId: UUID): G[List[String]] =
      read(underlying.getHobbies(userId))
  }
}
