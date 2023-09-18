package ru.nh.post

import cats.effect.std.Supervisor
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import fs2.concurrent.Channel
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.PostService.PostFeed
import ru.nh._
import ru.nh.events.EventManager
import ru.nh.events.EventManager.UserPosts
import ru.nh.post.PostAccessor.PostRow

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PostManager(
    val postAccessor: PostAccessor[IO],
    val userPosts: UserPosts,
    val supervisor: Supervisor[IO]
)(
    implicit log: Logger[IO]
) extends PostService {

  def addPost(userId: UUID, text: String): IO[UUID] =
    postAccessor.addPost(userId, text)

  def getPost(postId: UUID): IO[Option[Post]] =
    postAccessor.getPost(postId).map(_.map(_.toPost))

  def updatePost(postId: UUID, text: String): IO[Unit] =
    postAccessor.updatePost(postId, text)

  def deletePost(postId: UUID): IO[Unit] =
    postAccessor.deletePost(postId)

  def postFeed(userId: UUID, friends: List[UUID], offset: Int, limit: Int): Resource[IO, PostFeed] = Resource.suspend {
    Channel.unbounded[IO, PostRow].map { posts =>
      val logic =
        fs2.Stream
          .evalSeq(IO(friends))
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

object PostManager {
  def apply(accessor: PostAccessor[IO])(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, PostManager] =
    (
      Resource
        .eval {
          L.fromClass(classOf[PostManager])
            .flatTap(_.info(s"Allocating PostManager with ${accessor} store."))
        },
      EventManager.userPosts(accessor, 5.seconds, 100),
      Supervisor[IO]
    )
      .mapN { (log, em, s) =>
        new PostManager(accessor, em, s)(log)
      }

}
