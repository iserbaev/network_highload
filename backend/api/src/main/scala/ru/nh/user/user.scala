package ru.nh.user

import java.time.{ Instant, LocalDate }
import java.util.UUID

final case class User(
    id: UUID,
    name: String,
    surname: String,
    age: Int,
    city: String,
    gender: Option[String],
    birthdate: Option[LocalDate],
    biography: Option[String],
    hobbies: List[String]
)

final case class RegisterUserCommand(
    name: String,
    surname: String,
    age: Int,
    city: String,
    password: String,
    gender: Option[String],
    birthdate: Option[LocalDate],
    biography: Option[String],
    hobbies: List[String]
)

final case class Id(id: UUID)
