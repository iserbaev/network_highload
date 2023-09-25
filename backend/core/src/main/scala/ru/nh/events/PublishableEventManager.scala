package ru.nh.events

import cats.data.{ Chain, OptionT }
import cats.effect.IO
import cats.syntax.foldable._

/** EventManager extension that allows recording incoming event from request to journaled
  * storage and in parallel adding to underlying eventBuffer
  *
  * Consistency and monotonic read events from log - this extension is better fit for
  * situations where a 'worker' sends events for the same event key via a bidirectional
  * long-lived channel, that avoid loss event updates by concurrently writing from another
  * node and guarantees monotonic read for subscribers
  *
  * while for commands the ordering is not so strongly important, unlike delivery
  * guarantees
  *
  * @tparam A
  *   incoming entity to write to journaled storage
  * @tparam K
  *   event key
  * @tparam E
  *   event type
  */
abstract class PublishableEventManager[A, K, From, E] extends EventManager[K, From, E] {

  protected def recordValueToEventLog(key: K, value: A): OptionT[IO, E]

  def publish(key: K, value: A): IO[Option[E]] =
    recordValueToEventLog(key, value)
      .semiflatTap(publishRecorded(key, _, resend = false))
      .value

  def publishRecorded(key: K, event: E, resend: Boolean): IO[Unit] =
    buildTopicEvent(key, event)
      .flatMap(buffer.updateState(_, resend))

  def publishRecordedBatch(events: Chain[(K, E)], resend: Boolean): IO[Unit] =
    events
      .foldMapM { case (k, e) => buildTopicEvent(k, e).map(Chain.one) }
      .flatMap(_.traverse_(buffer.updateState(_, resend)))
}
