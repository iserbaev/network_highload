package ru.nh.user

import java.time.LocalDate
import java.util.UUID

final case class User(
    id: UUID,
    name: String,
    surname: String,
    age: Int,
    gender: String,
    hobbies: List[String],
    city: String
) {
  def toUserProfile(birthdate: Option[LocalDate], biography: Option[String]) =
    UserProfile(id, name, surname, age, birthdate, biography, city)
}

final case class RegisterUserCommand(
    name: String,
    surname: String,
    age: Int,
    gender: String,
    hobbies: List[String],
    city: String,
    password: String
)

final case class UserId(id: UUID)

final case class UserProfile(
    id: UUID,
    first_name: String,
    second_name: String,
    age: Int,
    birthdate: Option[LocalDate],
    biography: Option[String],
    city: String
)
