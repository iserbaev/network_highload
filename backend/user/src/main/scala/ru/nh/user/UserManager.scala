package ru.nh.user

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.{ RegisterUserCommand, User, UserService }

import java.util.UUID

class UserManager(
    val accessor: UserAccessor[IO]
)(
    implicit log: Logger[IO]
) extends UserService {
  def register(userInfo: RegisterUserCommand): IO[User] =
    accessor.save(userInfo).map(_.toUser(userInfo.hobbies)) <* log.debug(show"save ${userInfo.name}")

  def get(id: UUID): IO[Option[User]] =
    accessor.getUser(id).flatTap(u => log.debug(s"For $id got $u"))

  def search(firstNamePrefix: String, lastNamePrefix: String): IO[Option[User]] =
    accessor
      .search(firstNamePrefix, lastNamePrefix)
      .flatMap(_.traverse(row => accessor.getHobbies(row.userId).map(row.toUser)))

  def addFriend(userId: UUID, friendId: UUID): IO[Unit] =
    accessor.addFriend(userId, friendId)

  def getFriends(userId: UUID): IO[List[UUID]] =
    accessor.getFriends(userId)

  def deleteFriend(userId: UUID, friendId: UUID): IO[Unit] =
    accessor.deleteFriend(userId, friendId)
}

object UserManager {
  def apply(accessor: UserAccessor[IO])(implicit L: LoggerFactory[IO]): Resource[IO, UserManager] =
    Resource
      .eval {
        L.fromClass(classOf[UserManager])
          .flatTap(_.info(s"Allocating UserManager with ${accessor} store."))
          .map { implicit log =>
            new UserManager(accessor)(log)
          }
      }

}
