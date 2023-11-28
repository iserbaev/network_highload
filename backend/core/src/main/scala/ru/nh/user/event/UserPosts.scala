package ru.nh.user.event

import cats.effect.std.Queue
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.events.{ EventBuffer, ReadEventManager }
import ru.nh.post.PostAccessor
import ru.nh.post.PostAccessor.PostRow

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

class UserPosts(val store: UserPostsEventStore, val buffer: EventBuffer[UUID, PostRow], val dbQueue: Queue[IO, PostRow])
    extends ReadEventManager[UUID, PostRow] {
  protected def buildTopicEvent(event: PostRow): EventBuffer.TopicEvent[UUID, PostRow] =
    EventBuffer.TopicEvent(
      event.userId,
      event,
      completed = false,
      event.index,
      event.createdAt
    )
}

object UserPosts {
  def apply(
      accessor: PostAccessor[IO],
      tickInterval: FiniteDuration,
      eventBufferTtl: FiniteDuration,
      limit: Int
  )(implicit L: LoggerFactory[IO]): Resource[IO, UserPosts] =
    (
      Resource.eval(Queue.unbounded[IO, PostRow]),
      EventBuffer.resource[UUID, PostRow]
    )
      .flatMapN { (dbQueue, buffer) =>
        val em = new UserPosts(UserPostsEventStore(accessor), buffer, dbQueue)

        val syncFromUpdatesLogToDbQueue = ReadEventManager.backgroundSyncFromStorageToDBQueue(tickInterval, limit, em)

        val syncEventsFromQueues =
          em.streamQueuesGrouped(limit, tickInterval)
            .through(em.bufferPipe(true))
            .compile
            .drain
            .background
            .void

        ReadEventManager.cleanEventBufferPeriodically(eventBufferTtl, buffer) *>
          syncFromUpdatesLogToDbQueue *>
          syncEventsFromQueues
            .as(em)
      }
}
