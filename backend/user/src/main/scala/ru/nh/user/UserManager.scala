package ru.nh.user

import cats.effect.std.Supervisor
import cats.effect.{IO, Resource}
import cats.syntax.all._
import fs2.concurrent.Channel
import org.typelevel.log4cats.{Logger, LoggerFactory}
import ru.nh.{Post, PostService, RegisterUserCommand, User, UserService}
import ru.nh.cache.EventManager
import ru.nh.cache.EventManager.UserPosts
import ru.nh.post.PostAccessor
import ru.nh.post.PostAccessor.PostRow
import ru.nh.PostService.PostFeed

import java.util.UUID
import scala.concurrent.duration.DurationInt

class UserManager(
    val accessor: UserAccessor[IO],
    val postAccessor: PostAccessor[IO],
    val userPosts: UserPosts,
    val supervisor: Supervisor[IO]
)(
    implicit log: Logger[IO]
) extends UserService
    with PostService {
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
    postAccessor.addPost(userId, text)

  def getPost(postId: UUID): IO[Option[Post]] =
    postAccessor.getPost(postId).map(_.map(_.toPost))

  def updatePost(postId: UUID, text: String): IO[Unit] =
    postAccessor.updatePost(postId, text)

  def deletePost(postId: UUID): IO[Unit] =
    postAccessor.deletePost(postId)

  def postFeed(userId: UUID, offset: Int, limit: Int): Resource[IO, PostFeed] = Resource.suspend {
    Channel.unbounded[IO, PostRow].map { posts =>
      val logic =
        fs2.Stream
          .evalSeq(accessor.getFriends(userId))
          .parEvalMapUnordered(1000) { friendId =>
            userPosts
              .subscribe(friendId)
              .through(posts.sendAll)
              .compile
              .drain
          }

      // Thus, we manually start our logic fiber with supervision
      // and close both `updates` and `commands` channels on release.
      // After channels close we wait for our logic fiber to send
      // all registered updates and observe gRPC stream close from
      // a server side.
      Resource
        .makeCase(supervisor.supervise(logic.compile.drain)) { (logicFiber, ec) =>
          posts.close *>
            logicFiber.cancel.attempt *>
            log.debug(s"Finalized post feed [$userId]: $ec.")
        }
        .as(PostFeed(posts.stream.drop(offset.toLong).take(limit.toLong).map(_.toPost)))
    }
  }
}

object UserManager {
  def apply(accessor: UserAccessor[IO], postAccessor: PostAccessor[IO])(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, UserManager] =
    (
      Resource
        .eval {
          L.fromClass(classOf[UserManager])
            .flatTap(_.info(s"Allocating UserManager with ${accessor} store."))
        },
      EventManager.userPosts(postAccessor, 5.seconds),
      Supervisor[IO]
    )
      .mapN { (log, em, s) =>
        new UserManager(accessor, postAccessor, em, s)(log)
      }

}
