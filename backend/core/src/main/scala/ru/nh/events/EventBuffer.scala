package ru.nh.events

import cats.Show
import cats.data.OptionT
import cats.effect.kernel.Ref
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import fs2.concurrent.Topic
import fs2.{ Pipe, Stream }
import org.typelevel.log4cats.{ Logger, LoggerFactory, SelfAwareStructuredLogger }
import ru.nh.events.EventBuffer._

import java.time.Instant

/** In-memory buffer, that implements publish-subscribe pattern using fs2.Topic with
  * pull-semantic for subscribers
  *
  * topic can be removed on first event with completed flag (event based) or by invoking
  * removeSubscription function (time based)
  *
  * fs2.Topic allows you to distribute As published by an arbitrary number of publishers
  * to an arbitrary number of subscribers. Topic has built-in back-pressure support
  * implemented as the maximum number of elements (maxQueued) that a subscriber is allowed
  * to enqueue. Once that bound is hit, any publishing action will semantically block
  * until the lagging subscriber consumes some of its queued elements.
  *
  * @param log
  *   log
  * @tparam K
  *   event key type parameter
  * @tparam E
  *   event value type parameter
  */
class EventBuffer[K: Show, E] private[events] (val state: State[K, E])(implicit log: Logger[IO]) {

  def subscriptions: IO[List[Subscription[K, E]]] = state.get.map(_.values.toList)

  def sink(resendUpdates: Boolean): Pipe[IO, TopicEvent[K, E], Unit] =
    _.evalMap(updateState(_, resendUpdates))

  def subscribe(key: K)(lastEventFromLog: OptionT[IO, TopicEvent[K, E]]): Stream[IO, E] = Stream.force {
    getSubscription(key).value.map {
      case Some(subscription) if subscription.lastEvent.exists(_.completed) =>
        Stream.fromOption[IO](subscription.lastEvent).map(_.eventValue)
      case Some(subscription) =>
        Stream.fromOption(subscription.lastEvent.map(_.eventValue)) ++
          subscription.updates.subscribe(SubscriptionMaxQueued)
      case _ =>
        Stream.eval(lastEventFromLog.value).flatMap {
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

  def getSubscription(key: K): OptionT[IO, Subscription[K, E]] =
    OptionT(state.get.map(_.get(key)))

  def addSubscription(key: K, lastEvent: Option[TopicEvent[K, E]]): IO[Subscription[K, E]] = {
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

  def updateState(event: TopicEvent[K, E], resend: Boolean): IO[Unit] = {
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
      updates: Topic[IO, E],
      lastEvent: Option[TopicEvent[K, E]]
  )

  type State[K, E] = Ref[IO, Map[K, Subscription[K, E]]]

  val SubscriptionMaxQueued = 128

  def resource[K: Show, E](implicit L: LoggerFactory[IO]): Resource[IO, EventBuffer[K, E]] =
    Resource.eval(IO.ref(Map.empty[K, Subscription[K, E]])).map { state =>
      implicit val log: SelfAwareStructuredLogger[IO] = L.getLoggerFromClass(classOf[EventBuffer[K, E]])
      new EventBuffer(state)
    }
}
