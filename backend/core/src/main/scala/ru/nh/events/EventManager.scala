package ru.nh.events

import cats.Show
import cats.data.{ NonEmptyChain, OptionT }
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import ru.nh.events.EventBuffer.{ Subscription, TopicEvent }
import ru.nh.post.PostAccessor
import ru.nh.post.PostAccessor.PostRow

import java.util.UUID
import scala.concurrent.duration._

/** Service that can be used as an in-memory hot cache, that periodically fetches new
  * events from a journaled storage for keys, that were requested during subscription
  *
  * @tparam K
  *   event key
  * @tparam E
  *   event type
  */
abstract class EventManager[K, From, E] {

  def buffer: EventBuffer[K, E]
  protected def buildTopicEvent(key: K, event: E): IO[TopicEvent[K, E]]

  protected def getPivot: NonEmptyChain[Subscription[K, E]] => From

  protected def getLatestFromEventLog(key: K): OptionT[IO, TopicEvent[K, E]]

  protected def streamUpdatesFromEventLog(
      keys: NonEmptyChain[K],
      from: From,
      limit: Int
  ): Stream[IO, TopicEvent[K, E]]

  def subscribe(key: K): Stream[IO, E] =
    buffer.subscribe(key)(getLatestFromEventLog(key))

  def takeLast(key: K): IO[Option[TopicEvent[K, E]]] =
    buffer
      .getSubscription(key)
      .flatMap(s => OptionT.fromOption(s.lastEvent))
      .orElse(getLatestFromEventLog(key))
      .value
      .flatTap(buffer.addSubscription(key, _))

  def takeLastChanges(keys: NonEmptyChain[K]): IO[Map[K, E]] =
    keys
      .traverse { key =>
        takeLast(key).map(_.map(bc => (key, bc.eventValue)))
      }
      .map(_.collect { case Some((key, change)) => (key, change) }.toList.toMap)

  protected def backgroundBufferSync(
      tickInterval: FiniteDuration,
      limit: Int,
      resendUpdates: Boolean
  ): Resource[IO, this.type] =
    Stream
      .awakeEvery[IO](tickInterval)
      .flatMap(_ =>
        Stream
          .eval(buffer.snapshot)
          .flatMap { snapshot =>
            NonEmptyChain.fromSeq(snapshot.toSeq).traverse_ { kvSequence =>
              streamUpdatesFromEventLog(kvSequence.map(_._1), getPivot(kvSequence.map(_._2)), limit)
                .through(buffer.sink(resendUpdates))
            }
          }
      )
      .compile
      .drain
      .background
      .as(this)

}

object EventManager {

  type UserPosts = EventManager[UUID, Long, PostRow]

  def userPosts(accessor: PostAccessor[IO], tickInterval: FiniteDuration, limit: Int)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, EventManager[UUID, Long, PostRow]] = {
    def toEvent(row: PostRow) = TopicEvent(
      row.userId,
      row,
      completed = false,
      row.index,
      row.createdAt
    )

    instance[UUID, Long, PostRow](
      (ids, from, limit) => Stream.evalSeq(accessor.getPostsLog(ids, from, limit)).map(toEvent),
      id => accessor.getLastPost(id).map(toEvent),
      _.map(_.idx).minimum,
      (_, r) => toEvent(r).pure[IO],
      tickInterval,
      limit,
      resendUpdates = false
    )
  }

  def instance[K: Show, From, E](
      stream: (NonEmptyChain[K], From, Int) => Stream[IO, TopicEvent[K, E]],
      getLastEvent: K => OptionT[IO, TopicEvent[K, E]],
      pivot: NonEmptyChain[Subscription[K, E]] => From,
      buildEvent: (K, E) => IO[TopicEvent[K, E]],
      tickInterval: FiniteDuration,
      limit: Int,
      resendUpdates: Boolean
  )(implicit log: LoggerFactory[IO]): Resource[IO, EventManager[K, From, E]] =
    EventBuffer
      .resource[K, E]
      .flatMap { buf =>
        new EventManager[K, From, E] {
          protected def getPivot: NonEmptyChain[Subscription[K, E]] => From =
            pivot

          def buffer: EventBuffer[K, E] = buf

          protected def buildTopicEvent(key: K, event: E): IO[TopicEvent[K, E]] = buildEvent(key, event)

          protected def getLatestFromEventLog(key: K): OptionT[IO, TopicEvent[K, E]] = getLastEvent(key)

          protected def streamUpdatesFromEventLog(keys: NonEmptyChain[K], from: From, limit: Int)
              : Stream[IO, TopicEvent[K, E]] = stream(keys, from, limit)
        }.backgroundBufferSync(tickInterval, limit, resendUpdates)
      }

}
