package ru.nh.user.inmemory

import cats.effect.{IO, Ref}
import cats.syntax.all._
import ru.nh.user.UserAccessor.UserRow
import ru.nh.user.{RegisterUserCommand, User, UserAccessor}

import java.util.UUID

class InMemoryUserAccessor(users: Ref[IO, Map[UUID, UserRow]], hobbies: Ref[IO, Map[UUID, List[String]]])
    extends UserAccessor[IO] {
  def save(u: RegisterUserCommand): IO[UserRow] =
    (IO.realTimeInstant, IO.randomUUID).flatMapN { (now, id) =>
      val userRow = UserRow(id, now, u.name, u.surname, u.age, u.gender, u.city, u.password)
      (users.update(_.updated(id, userRow)), hobbies.update(_.updated(id, u.hobbies))).tupled.as(userRow)
    }

  def getUserRow(userId: UUID): IO[Option[UserRow]] =
    users.get.map(_.get(userId))

  def getUser(userId: UUID): IO[Option[User]] =
    getUserRow(userId).flatMap(_.traverse(row => getHobbies(userId).map(row.toUser)))

  def getHobbies(userId: UUID): IO[List[String]] =
    hobbies.get.map(_.getOrElse(userId, List.empty))
}
