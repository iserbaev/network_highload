package ru.nh.user

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }

import java.util.UUID

class UserManager(val accessor: UserAccessor[IO])(implicit log: Logger[IO]) extends UserService {
  def register(userInfo: RegisterUserCommand): IO[User] =
    accessor.save(userInfo).map(_.toUser(userInfo.hobbies)) <* log.debug(show"save ${userInfo.name}")

  def get(id: UUID): IO[Option[User]] =
    accessor.getUser(id).flatTap(u => log.debug(s"For $id got $u"))

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
