package ru.nh.events

import cats.Show
import cats.data.{ Chain, NonEmptyChain, NonEmptyVector, OptionT }
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

/** In-memory message broker, that implements publish-subscribe pattern using fs2.Topic
  * with pull-semantic for subscribers
  *
  * Messages fetches periodically by invoking 'streamUpdates' method for keys that was
  * requested by subscribers
  *
  * topic removed on first event with completed flag
  *
  * Topic allows you to distribute As published by an arbitrary number of publishers to an
  * arbitrary number of subscribers. Topic has built-in back-pressure support implemented
  * as the maximum number of elements (maxQueued) that a subscriber is allowed to enqueue.
  * Once that bound is hit, any publishing action will semantically block until the
  * lagging subscriber consumes some of its queued elements.
  *
  * @param log
  *   log
  * @tparam K
  *   event key type parameter
  * @tparam E
  *   event value type parameter
  */
abstract class EventManager[K: Show, From, E](implicit log: Logger[IO]) {
  import EventManager._

  protected def getLatestEvent(key: K): OptionT[IO, TopicEvent[K, E]]
  protected def state: State[K, E]
  protected def lastStateEvent: NonEmptyChain[Subscription[K, E]] => From

  // ids should not contain duplicates
  protected def streamUpdates(keys: NonEmptyVector[K], from: From, limit: Int): Stream[IO, TopicEvent[K, E]]

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

  def subscribe(key: K): Stream[IO, E] = Stream.force {
    getSubscription(key).value.map {
      case Some(subscription) if subscription.lastEvent.exists(_.completed) =>
        Stream.fromOption[IO](subscription.lastEvent).map(_.eventValue)
      case Some(subscription) =>
        Stream.fromOption(subscription.lastEvent.map(_.eventValue)) ++
          subscription.updates.subscribe(SubscriptionMaxQueued)
      case _ =>
        Stream.eval(getLatestEvent(key).value).flatMap {
          case Some(event) if event.completed =>
            Stream.emit(event.eventValue)
          case opt =>
            Stream.eval(addSubscription(key, opt)).flatMap { subscription =>
              Stream.fromOption(opt.map(_.eventValue)) ++
                subscription.updates.subscribe(SubscriptionMaxQueued)
            }
        }
    }
  }

  def tryTakeLast(key: K): IO[Option[TopicEvent[K, E]]] =
    getSubscription(key)
      .flatMap(s => OptionT.fromOption(s.lastEvent))
      .orElse(getLatestEvent(key))
      .value
      .flatTap(addSubscription(key, _))

  def takeLastChanges(keys: NonEmptyChain[K]): IO[Map[K, E]] =
    keys
      .traverse { key =>
        tryTakeLast(key).map(_.map(bc => (key, bc.eventValue)))
      }
      .map(_.collect { case Some((key, change)) => (key, change) }.toList.toMap)

  private def getSubscription(key: K) =
    OptionT(state.get.map(_.get(key)))

  private def addSubscription(key: K, lastEvent: Option[TopicEvent[K, E]]): IO[Subscription[K, E]] = {
    def add(map: Map[K, Subscription[K, E]]): IO[(Map[K, Subscription[K, E]], IO[Subscription[K, E]])] = {
      val getSubscription: IO[Subscription[K, E]] = lastEvent match {
        case Some(event) =>
          Topic[IO, E].map { topic =>
            Subscription(key, event.updateIndex, event.lastModifiedAt, topic, event.some)
          }
        case None =>
          for {
            topic <- Topic[IO, E]
            now   <- IO.realTimeInstant
          } yield Subscription(key, Int.MinValue, now, topic, none)
      }

      getSubscription.map { subscription =>
        val updatedState = map.updated(key, subscription)
        val effect       = IO.pure(subscription)

        (updatedState, effect)
      }
    }

    retryUpdateState(_ => true, add)
      .getOrElseF(IO.raiseError(new IllegalStateException(show"Add subscription failed [$key]")))
  }

  protected final def updateState(event: TopicEvent[K, E], resend: Boolean): IO[Unit] = {
    def update(map: Map[K, Subscription[K, E]]): IO[(Map[K, Subscription[K, E]], IO[Unit])] =
      IO.delay {
        map
          .get(event.key)
          .filter(sub => resend || sub.idx < event.updateIndex)
          .map { subscription =>
            if (event.completed) {
              val update = map.removed(event.key)
              val effect = subscription.updates.publish1(event.eventValue) *> subscription.updates.close.void
              (update, effect)
            } else {
              val update =
                map.updated(subscription.key, subscription.copy(idx = event.updateIndex, lastEvent = event.some))
              val effect = subscription.updates.publish1(event.eventValue).void
              (update, effect)
            }
          }
          .getOrElse((map, IO.unit))
      }

    retryUpdateState(_.nonEmpty, update).value.void
  }

  def buildStream(limit: Int): Stream[IO, TopicEvent[K, E]] =
    Stream
      .eval(state.get)
      .flatMap { m =>
        val keys = m.keys
        if (keys.nonEmpty) {
          val from = lastStateEvent(NonEmptyChain.fromChainUnsafe(Chain.fromIterableOnce(m.values)))
          streamUpdates(NonEmptyVector.of(keys.head, keys.tail.toSeq: _*), from, limit)
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
      log.debug(show"Finalized subscription for [$key].")
  }

  protected def startSyncInBackground(
      tickInterval: FiniteDuration,
      limit: Int,
      resendUpdates: Boolean
  ): Resource[IO, this.type] =
    Stream
      .awakeEvery[IO](tickInterval)
      .flatMap(_ => buildStream(limit).evalMapChunk(updateState(_, resend = resendUpdates)))
      .compile
      .drain
      .background
      .as(this)

}

object EventManager {

  final case class TopicEvent[K, E](
      key: K,
      eventValue: E,
      completed: Boolean,
      updateIndex: Long,
      lastModifiedAt: Instant
  )

  case class Subscription[K, E](
      key: K,
      idx: Long,
      from: Instant,
      updates: Topic[IO, E],
      lastEvent: Option[TopicEvent[K, E]]
  )

  type State[K, E] = Ref[IO, Map[K, Subscription[K, E]]]

  val SubscriptionMaxQueued = 128

  type UserPosts = EventManager[UUID, Long, PostRow]

  def userPosts(accessor: PostAccessor[IO], tickInterval: FiniteDuration, limit: Int)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, EventManager[UUID, Long, PostRow]] =
    Resource.eval(L.fromClass(classOf[UserPosts])).flatMap { implicit log =>
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
        tickInterval,
        limit
      )
    }

  def instance[K: Show, From, E](
      stream: (NonEmptyVector[K], From, Int) => Stream[IO, TopicEvent[K, E]],
      getLastEvent: K => OptionT[IO, TopicEvent[K, E]],
      fromFunction: NonEmptyChain[Subscription[K, E]] => From,
      tickInterval: FiniteDuration,
      limit: Int
  )(implicit log: Logger[IO]): Resource[IO, EventManager[K, From, E]] =
    Resource
      .eval(Ref[IO].of(Map.empty[K, Subscription[K, E]]))
      .flatMap { subsState =>
        new EventManager[K, From, E] {
          protected def getLatestEvent(key: K): OptionT[IO, TopicEvent[K, E]] =
            getLastEvent(key)

          protected def lastStateEvent: NonEmptyChain[Subscription[K, E]] => From =
            fromFunction

          protected def streamUpdates(keys: NonEmptyVector[K], from: From, limit: Int): Stream[IO, TopicEvent[K, E]] =
            stream(keys, from, limit)

          protected def state: State[K, E] = subsState
        }.startSyncInBackground(tickInterval, limit, resendUpdates = false)
      }

}
