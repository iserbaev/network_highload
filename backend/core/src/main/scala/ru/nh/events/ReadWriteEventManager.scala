package ru.nh.events

import cats.data.Chain
import cats.effect.IO
import cats.effect.std.Queue
import cats.syntax.traverse._
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

  def publishBatch(values: Chain[(K, A)]): IO[Chain[E]] =
    values
      .traverse { case (key, value) => store.recordValueToEventLog(key, value) }
      .semiflatTap(publishRecordedBatch(_, resend = false))
      .getOrElse(Chain.empty)

  def publishRecordedBatch(events: Chain[E], resend: Boolean): IO[Unit] =
    apiQueue.tryOfferN(events.toList).void <* buffer.batchUpdateState(events.map(buildTopicEvent), resend)

  protected def streamApiQueueUpdates: Stream[IO, E] =
    Stream
      .fromQueueUnterminated(apiQueue)

  override def streamQueuesGrouped(chunkSize: Int, timeout: FiniteDuration): Stream[IO, Chunk[E]] =
    streamDbQueueUpdates
      .merge(streamApiQueueUpdates)
      .groupWithin(chunkSize, timeout)
}
