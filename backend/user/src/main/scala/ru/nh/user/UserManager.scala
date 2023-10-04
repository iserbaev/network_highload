package ru.nh.user

import cats.effect.std.Supervisor
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.cache.{ AsyncCache, Caffeine }
import ru.nh.user.UserManager.SearchProfileRequest
import ru.nh.{ Friends, RegisterUserCommand, User, UserService }

import java.util.UUID
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

class UserManager(
    val accessor: UserAccessor[IO],
    val friendsAccessor: FriendsAccessor[IO],
    searchProfilesCache: AsyncCache[SearchProfileRequest, List[User]],
    supervisor: Supervisor[IO]
)(
    implicit log: Logger[IO]
) extends UserService {
  def register(userInfo: RegisterUserCommand): IO[User] =
    accessor.save(userInfo).map(_.toUser(userInfo.hobbies)) <* log.debug(show"save ${userInfo.name}")

  def get(id: UUID): IO[Option[User]] =
    accessor.getUser(id).flatTap(u => log.debug(s"For $id got $u"))

  def search(firstNamePrefix: String, lastNamePrefix: String, limit: Int): IO[List[User]] = {
    def program(firstNamePrefix: String, lastNamePrefix: String, limit: Int) =
      accessor
        .search(firstNamePrefix, lastNamePrefix, limit)
        .flatMap(_.traverse(row => accessor.getHobbies(row.userId).map(row.toUser)))

    searchProfilesCache.getF(
      SearchProfileRequest(firstNamePrefix, lastNamePrefix, limit),
      req => program(req.firstNamePrefix, req.lastNamePrefix, req.limit)
    )
  }

  def addFriends(f: Friends): IO[Unit] =
    supervisor.supervise(friendsAccessor.addFriends(f)).void

  def getFriends(userId: UUID): IO[List[Friends]] =
    friendsAccessor.getFriends(userId)

  def deleteFriend(f: Friends): IO[Unit] =
    friendsAccessor.deleteFriend(f)
}

object UserManager {
  final case class SearchProfileRequest(firstNamePrefix: String, lastNamePrefix: String, limit: Int)

  def apply(accessor: UserAccessor[IO], friendsAccessor: FriendsAccessor[IO], cacheInvalidationTtl: FiniteDuration)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, UserManager] =
    (
      Caffeine().expireAfterWrite(cacheInvalidationTtl).buildAsync[SearchProfileRequest, List[User]],
      Resource.eval(L.fromClass(classOf[UserManager])),
      Supervisor[IO]
    ).mapN { (cache, log, supervisor) =>
      new UserManager(accessor, friendsAccessor, cache, supervisor)(log)
    }

  def apply(accessor: UserAccessor[IO], friendsAccessor: FriendsAccessor[IO])(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, UserManager] =
    apply(accessor, friendsAccessor, 5.minutes)

}
