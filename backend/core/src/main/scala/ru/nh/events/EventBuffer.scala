package ru.nh.events

import cats.Show
import cats.data.{ Chain, OptionT }
import cats.effect.kernel.Ref
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import fs2.concurrent.Topic
import fs2.{ Chunk, Pipe }
import org.typelevel.log4cats.{ Logger, LoggerFactory, SelfAwareStructuredLogger }
import ru.nh.events.EventBuffer._

import java.time.Instant

/** In-memory buffer, that implements publish-subscribe pattern using fs2.Topic with
  * pull-semantic for subscribers
  *
  * Topic can be removed on first event with completed flag (event based) or by invoking
  * cleanUnusedSubscriptions function (time based)
  *
  * fs2.Topic allows you to distribute events from an arbitrary number of publishers to an
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
class EventBuffer[K: Show, E] private[events] (val state: State[K, E])(implicit log: Logger[IO]) {

  def subscriptions: IO[Vector[Subscription[K, E]]] = state.get.map(_.values.toVector)

  def sink(resendUpdates: Boolean): Pipe[IO, TopicEvent[K, E], Nothing] =
    _.foreach(updateState(_, resendUpdates))

  def batchSink(resendUpdates: Boolean): Pipe[IO, Chunk[TopicEvent[K, E]], Nothing] =
    _.foreach(chunk => batchUpdateState(chunk.toChain, resendUpdates))

  def getSubscription(key: K): OptionT[IO, Subscription[K, E]] =
    OptionT(state.get.map(_.get(key)))

  def addSubscription(key: K, lastEvent: Option[TopicEvent[K, E]]): IO[Subscription[K, E]] = {
    def add(map: Map[K, Subscription[K, E]]): IO[(Map[K, Subscription[K, E]], IO[Subscription[K, E]])] = {
      val getSubscription: IO[Subscription[K, E]] = lastEvent match {
        case Some(event) =>
          Topic[IO, E].map { topic =>
            Subscription(key, event.updateIndex, event.lastModifiedAt, topic)
          }
        case None =>
          for {
            topic <- Topic[IO, E]
            now   <- IO.realTimeInstant
          } yield Subscription(key, Int.MinValue, now, topic)
      }

      getSubscription.map { subscription =>
        val updatedState = map.updated(key, subscription)
        val effect       = IO.pure(subscription)

        (updatedState, effect)
      }
    }

    retryUpdateState(!_.contains(key), add)
      .orElseF(state.get.map(_.get(key)))
      .getOrElseF(IO.raiseError(new IllegalStateException(show"Add subscription failed [$key]")))
  }

  def updateState(event: TopicEvent[K, E], resend: Boolean): IO[Unit] =
    retryUpdateState(_.nonEmpty, update(_, event, resend)).value.void

  private def update(
      map: Map[K, Subscription[K, E]],
      event: TopicEvent[K, E],
      resend: Boolean
  ): IO[(Map[K, Subscription[K, E]], IO[Unit])] =
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
              map.updated(subscription.key, subscription.copy(idx = event.updateIndex))
            val effect =
              subscription.updates.publish1(event.eventValue).void
            (update, effect)
          }
        }
        .getOrElse((map, IO.unit))
    }

  def batchUpdateState(events: Chain[TopicEvent[K, E]], resend: Boolean): IO[Unit] =
    events.traverse_(updateState(_, resend)).void

  def removeSubscriptions(key: K): IO[Unit] = {
    def remove(map: Map[K, Subscription[K, E]]): IO[(Map[K, Subscription[K, E]], IO[Unit])] = IO {
      val updatedState = map.removed(key)
      val effect       = map.get(key).traverse_(_.updates.close)

      (updatedState, effect)
    }

    retryUpdateState(_.contains(key), remove).value.void *>
      log.debug(show"Finalized subscription for [$key].")
  }

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

  def cleanUnusedSubscriptions: IO[Unit] =
    state.get.flatMap { snapshot =>
      snapshot.toList.traverse_ { case (key, subscription) =>
        subscription.updates.subscribers.head
          .evalMap { subscribersCount =>
            removeSubscriptions(key).whenA(subscribersCount == 0)
          }
          .compile
          .drain
      }
    }
}

object EventBuffer {
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
      updates: Topic[IO, E]
  )

  type State[K, E] = Ref[IO, Map[K, Subscription[K, E]]]

  val SubscriptionMaxQueued = 128

  def resource[K: Show, E](implicit L: LoggerFactory[IO]): Resource[IO, EventBuffer[K, E]] =
    Resource.eval(IO.ref(Map.empty[K, Subscription[K, E]])).flatMap { state =>
      implicit val log: SelfAwareStructuredLogger[IO] = L.getLoggerFromClass(classOf[EventBuffer[K, E]])

      Resource.make(new EventBuffer(state).pure[IO]) { buffer =>
        buffer.state.get.flatTap { map =>
          map.parUnorderedTraverse(_.updates.close) *> map.removedAll(map.keys).pure[IO]
        }.void
      }
    }
}
