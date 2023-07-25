package ru.nh.user

import cats.~>
import ru.nh.user.UserAccessor.{ UserAccessorMapK, UserRow }

import java.time.{ Instant, LocalDate }
import java.util.UUID

trait UserAccessor[F[_]] {
  def save(u: RegisterUserCommand): F[UserRow]
  def getUserRow(userId: UUID): F[Option[UserRow]]

  def getUser(userId: UUID): F[Option[User]]

  def getHobbies(userId: UUID): F[List[String]]

  def search(firstNamePrefix: String, lastNamePrefix: String): F[Option[UserRow]]

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

    def search(firstNamePrefix: String, lastNamePrefix: String): G[Option[UserRow]] =
      read(underlying.search(firstNamePrefix, lastNamePrefix))
  }
}
