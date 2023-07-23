package ru.nh.user

import cats.~>
import ru.nh.user.UserAccessor.UserAccessorMapK

import java.time.Instant
import java.util.UUID

trait UserAccessor[F[_]] {
  def save(u: RegisterUserCommand): F[User]
  def get(userId: UUID): F[Option[User]]

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
      city: String
  ) {
    def toUser(hobbies: List[String]): User =
      User(userId, name, surname, age, gender, hobbies, city)
  }

  private[user] final class UserAccessorMapK[F[_], G[_]](underlying: UserAccessor[F], read: F ~> G, write: F ~> G)
      extends UserAccessor[G] {
    def save(u: RegisterUserCommand): G[User] =
      write(underlying.save(u))

    def get(userId: UUID): G[Option[User]] =
      read(underlying.get(userId))
  }
}
