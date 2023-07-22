package ru.nh.user

import java.time.Instant
import java.util.UUID

trait UserAccessor[F[_]] {
  def save(u: RegisterUserCommand): F[User]
  def get(userId: UUID): F[Option[User]]

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
}
