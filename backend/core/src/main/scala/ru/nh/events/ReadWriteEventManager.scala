package ru.nh.events

import cats.NonEmptyTraverse
import cats.data.Chain
import cats.effect.IO
import cats.effect.std.Queue
import cats.syntax.all._
import fs2.{ Chunk, Stream }

import scala.concurrent.duration.FiniteDuration

/** EventManager extension that allows recording incoming event from request to journaled
  * storage and in parallel add to underlying eventBuffer
  *
  * This extension is better fit for situations where a 'worker' sends events for the same
  * event key via a bidirectional long-lived channel, that avoid loss event updates by
  * concurrently writing from another node and guarantees monotonic read for subscribers,
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
abstract class ReadWriteEventManager[A, K, E] extends ReadEventManager[K, E] {
  override def store: ReadWriteEventStore[IO, A, K, E]

  def apiQueue: Queue[IO, E]

  def publish(key: K, value: A): IO[Option[E]] =
    store
      .recordValueToEventLog(key, value)
      .semiflatTap(publishRecorded(_, resend = false))
      .value

  def publishRecorded(event: E, resend: Boolean): IO[Unit] =
    apiQueue.offer(event) <* buffer.updateState(buildTopicEvent(event), resend)

  def publishBatch[R[_]: NonEmptyTraverse](values: R[(K, A)]): IO[Vector[E]] =
    store
      .recordValuesBatch(values)
      .flatTap(publishRecordedBatch(_, resend = false))

  def publishRecordedBatch(events: Vector[E], resend: Boolean): IO[Unit] =
    events.traverse_(apiQueue.tryOffer) <*
      buffer.batchUpdateState(Chain.fromIterableOnce(events.map(buildTopicEvent)), resend)

  protected def streamApiQueueUpdates: Stream[IO, E] =
    Stream
      .fromQueueUnterminated(apiQueue)

  override def streamQueuesGrouped(chunkSize: Int, timeout: FiniteDuration): Stream[IO, Chunk[E]] =
    streamDbQueueUpdates
      .merge(streamApiQueueUpdates)
      .groupWithin(chunkSize, timeout)
}
