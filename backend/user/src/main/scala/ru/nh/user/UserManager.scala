package ru.nh.user

import cats.data.Chain
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }

import java.util.UUID

class UserManager(val accessor: UserAccessor[IO])(implicit log: Logger[IO]) extends UserService {
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

  def deleteFriend(userId: UUID, friendId: UUID): IO[Unit] =
    accessor.deleteFriend(userId, friendId)

  def addPost(userId: UUID, text: String): IO[UUID] =
    accessor.addPost(userId, text)

  def getPost(postId: UUID): IO[Option[Post]] =
    accessor.getPost(postId).map(_.map(_.toPost))

  def updatePost(postId: UUID, text: String): IO[Unit] =
    accessor.updatePost(postId, text)

  def deletePost(postId: UUID): IO[Unit] =
    accessor.deletePost(postId)

  def postFeed(userId: UUID, offset: Int, limit: Int): IO[Chain[Post]] =
    accessor.postFeed(userId, offset, limit).map(_.map(_.toPost))
}

object UserManager {
  def apply(accessor: UserAccessor[IO])(implicit L: LoggerFactory[IO]): Resource[IO, UserService] = Resource.eval {
    L.fromClass(classOf[UserManager])
      .flatTap(_.info(s"Allocating UserManager with ${accessor} store."))
      .map { implicit log =>
        new UserManager(accessor)
      }
  }
}
