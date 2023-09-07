package ru.nh.cache

import cats.data.{ NonEmptyVector, OptionT }
import cats.effect.{ IO, Ref, Resource }
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Topic
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.post.PostAccessor
import ru.nh.post.PostAccessor.PostRow

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._

/** In-memory key-value store used as cache, message-broker with publish-subscribe
  * pattern,
  *
  * invalidation by event state topic cleared on first event with completed flag
  *
  * underlying used fs2.Topic concurrency primitive
  *
  * cache updates by background task running with given interval
  *
  * @param log
  *   log
  * @tparam K
  *   event key type parameter
  * @tparam E
  *   event value type parameter
  */
abstract class EventManager[K, E](implicit log: Logger[IO]) {
  import EventManager._

  protected def tickPeriod: FiniteDuration

  protected def getLatestEvent(key: K): OptionT[IO, TopicEvent[K, E]]
  protected def buildTopicEvent(key: K, event: E): IO[TopicEvent[K, E]]
  protected def state: State[K, E]
  protected def streamUpdates(ids: NonEmptyVector[K], fromIndex: Long): Stream[IO, TopicEvent[K, E]]

  private def retryUpdateState[R](
      updateIf: Map[K, Subscription[K, E]] => Boolean,
      updateState: Map[K, Subscription[K, E]] => IO[(Map[K, Subscription[K, E]], IO[R])]
  ): OptionT[IO, R] =
    OptionT(state.access.flatMap { case (map, trySet) =>
      IO(updateIf(map))
        .ifM(
          updateState(map)
            .flatMap { case (state, effect) =>
              trySet(state).ifM(effect.option, retryUpdateState(updateIf, updateState).value)
            },
          Option.empty[R].pure[IO]
        )
    })

  def subscribe(key: K): Stream[IO, E] = Stream
    .force {
      log.debug(s"Start subscription for [$key]") *>
        getLatestEvent(key).value.map {
          case Some(event) if event.completed => Stream.emit(event.v)
          case opt =>
            Stream.eval(addSubscription(key, opt)).flatMap { subscription =>
              Stream.fromOption(opt.map(_.v)) ++
                subscription.updates.subscribe(SubscriptionMaxQueued)
            }
        }
    }
    .onFinalizeCase(e => log.debug(s"Finalized subscription [$key], $e"))

  def subscribeFrom(key: K, offset: Int): Stream[IO, E] =
    Stream
      .eval(log.debug(s"Start subscription for [$key], offset $offset") *> addSubscription(key, none))
      .flatMap(_.updates.subscribe(SubscriptionMaxQueued).drop(offset.toLong))
      .onFinalizeCase(e => log.debug(s"Finalized subscription [$key], $e"))

  private def addSubscription(key: K, lastEvent: Option[TopicEvent[K, E]]): IO[Subscription[K, E]] = {
    def add(map: Map[K, Subscription[K, E]]): IO[(Map[K, Subscription[K, E]], IO[Subscription[K, E]])] = {
      val getSubscription: IO[Subscription[K, E]] = map.get(key) match {
        case Some(sub) => IO.pure(sub)
        case None =>
          lastEvent match {
            case Some(event) =>
              Topic[IO, E].map { topic =>
                Subscription(key, event.index, event.lastModifiedAt, topic)
              }
            case None =>
              for {
                topic <- Topic[IO, E]
                now   <- IO.realTimeInstant
              } yield Subscription(key, Int.MinValue, now, topic)
          }
      }

      getSubscription.map { subscription =>
        val updatedState = map.updated(key, subscription)
        val effect       = IO.pure(subscription)

        (updatedState, effect)
      }
    }

    retryUpdateState(_ => true, add)
      .getOrElseF(IO.raiseError(new IllegalStateException(s"Add subscription failed [$key]")))
  }

  private def updateState(event: TopicEvent[K, E], resend: Boolean): IO[Unit] = {
    def update(map: Map[K, Subscription[K, E]]): IO[(Map[K, Subscription[K, E]], IO[Unit])] =
      IO.delay {
        map
          .get(event.key)
          .filter(sub => resend || sub.index < event.index)
          .map { subscription =>
            if (event.completed) {
              val update = map.removed(event.key)
              val effect = subscription.updates.publish1(event.v) *> subscription.updates.close.void
              (update, effect)
            } else {
              val update = map.updated(subscription.key, subscription.copy(index = event.index))
              val effect = subscription.updates.publish1(event.v).void
              (update, effect)
            }
          }
          .getOrElse((map, IO.unit))
      }

    retryUpdateState(_.nonEmpty, update).value.void
  }

  def buildStream: Stream[IO, TopicEvent[K, E]] =
    Stream
      .eval(state.get)
      .flatMap { m =>
        val ids = m.keys
        if (ids.nonEmpty) {
          val minIndex = m.values.map(_.index).min
          streamUpdates(NonEmptyVector.of(ids.head, ids.tail.toSeq: _*), minIndex)
        } else {
          Stream.empty
        }
      }

  def removeSubscriptions(key: K): IO[Unit] = {
    def remove(map: Map[K, Subscription[K, E]]): IO[(Map[K, Subscription[K, E]], IO[Unit])] = IO {
      val updatedState = map.removed(key)
      val effect       = map.get(key).traverse_(_.updates.close)

      (updatedState, effect)
    }

    retryUpdateState(_.contains(key), remove).value.void *>
      log.debug(s"Finalized subscription for [$key].")
  }

  private def ticks: Stream[IO, Unit] =
    Stream
      .awakeEvery[IO](tickPeriod) >> buildStream.evalMapChunk(updateState(_, resend = false))

  def withStateSync: Resource[IO, EventManager[K, E]] = ticks.compile.drain.background.void.as(this)

}

object EventManager {

  type UserPosts = EventManager[UUID, PostRow]

  final case class TopicEvent[K, A](
      key: K,
      v: A,
      completed: Boolean,
      index: Long,
      lastModifiedAt: Instant
  )

  case class Subscription[K, E](key: K, index: Long, from: Instant, updates: Topic[IO, E])

  type State[K, E] = Ref[IO, Map[K, Subscription[K, E]]]

  val SubscriptionMaxQueued = 128

  def userPosts(accessor: PostAccessor[IO], tickInterval: FiniteDuration)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, EventManager[UUID, PostRow]] =
    Resource.eval(L.fromClass(classOf[UserPosts])).flatMap { implicit log =>
      def toEvent(row: PostRow) = TopicEvent(
        row.userId,
        row,
        completed = false,
        row.index,
        row.createdAt
      )

      instance[UUID, PostRow](
        (ids, from) => Stream.evalSeq(accessor.getPostsLog(ids, from, SubscriptionMaxQueued)).map(toEvent),
        (_, value) => IO(toEvent(value)),
        id => accessor.getLastPost(id).map(toEvent),
        tickInterval
      )
    }

  def instance[K, E](
      stream: (NonEmptyVector[K], Long) => Stream[IO, TopicEvent[K, E]],
      buildEvent: (K, E) => IO[TopicEvent[K, E]],
      getLastEvent: K => OptionT[IO, TopicEvent[K, E]],
      tickInterval: FiniteDuration
  )(implicit log: Logger[IO]): Resource[IO, EventManager[K, E]] =
    Resource
      .eval(Ref[IO].of(Map.empty[K, Subscription[K, E]]))
      .flatMap { subsState =>
        new EventManager[K, E] {
          override protected def tickPeriod: FiniteDuration                     = tickInterval
          protected def getLatestEvent(key: K): OptionT[IO, TopicEvent[K, E]]   = getLastEvent(key)
          protected def buildTopicEvent(key: K, event: E): IO[TopicEvent[K, E]] = buildEvent(key, event)
          protected def streamUpdates(ids: NonEmptyVector[K], fromIndex: Long): Stream[IO, TopicEvent[K, E]] =
            stream(ids, fromIndex)
          protected def state: State[K, E] = subsState
        }.withStateSync
      }

}
