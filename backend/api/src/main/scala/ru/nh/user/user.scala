package ru.nh.user

import java.util.UUID

final case class User(
    id: UUID,
    name: String,
    surname: String,
    age: Int,
    gender: String,
    hobbies: List[String],
    city: String
)

final case class RegisterUserCommand(
    name: String,
    surname: String,
    age: Int,
    gender: String,
    hobbies: List[String],
    city: String,
    password: String
) {
  def toUser(id: UUID): User =
    User(id, name, surname, age, gender, hobbies, city)
}

final case class UserId(id: String)
