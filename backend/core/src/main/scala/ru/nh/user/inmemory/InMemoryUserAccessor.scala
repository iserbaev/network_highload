package ru.nh.user.inmemory

import cats.effect.{ IO, Ref }
import cats.syntax.all._
import cats.{ Functor, Reducible }
import ru.nh.user.UserAccessor.UserRow
import ru.nh.user.{ RegisterUserCommand, User, UserAccessor }

import java.util.UUID

class InMemoryUserAccessor (
    users: Ref[IO, Map[UUID, UserRow]],
    hobbies: Ref[IO, Map[UUID, List[String]]],
    friends: Ref[IO, Map[UUID, Set[UUID]]]
) extends UserAccessor[IO] {
  def save(u: RegisterUserCommand): IO[UserRow] =
    (IO.realTimeInstant, IO.randomUUID).flatMapN { (now, id) =>
      val userRow = UserRow(id, now, u.name, u.surname, u.age, u.city, u.gender, u.biography, u.birthdate)
      (users.update(_.updated(id, userRow)), hobbies.update(_.updated(id, u.hobbies))).tupled.as(userRow)
    }

  def saveBatch[R[_]: Reducible: Functor](u: R[RegisterUserCommand]): IO[Unit] =
    u.traverse_(save)

  def getUserRow(userId: UUID): IO[Option[UserRow]] =
    users.get.map(_.get(userId))

  def getUser(userId: UUID): IO[Option[User]] =
    getUserRow(userId).flatMap(_.traverse(row => getHobbies(userId).map(row.toUser)))

  def getHobbies(userId: UUID): IO[List[String]] =
    hobbies.get.map(_.getOrElse(userId, List.empty))

  def search(firstNamePrefix: String, lastNamePrefix: String): IO[Option[UserRow]] =
    users.get.map(
      _.find { case (_, row) => row.name.contains(firstNamePrefix) && row.surname.contains(lastNamePrefix) }.map(_._2)
    )

  def addFriend(userId: UUID, friendId: UUID): IO[Unit] =
    friends.update(_.updatedWith(userId)(_.map(_ + friendId).orElse(Set(friendId).some))).void

  def deleteFriend(userId: UUID, friendId: UUID): IO[Unit] =
    friends.update(_.updatedWith(userId)(_.map(_ - friendId))).void
  def getFriends(userId: UUID): IO[List[UUID]] =
    friends.get.map(_.getOrElse(userId, Set.empty).toList)
}
