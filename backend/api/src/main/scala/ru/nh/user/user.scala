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

final case class Post(id: UUID, text: String, author_user_id: UUID, createdAt: Instant)
object Post {
  implicit val ordering: Ordering[Post] = Ordering.by[Post, Instant](_.createdAt)
}
