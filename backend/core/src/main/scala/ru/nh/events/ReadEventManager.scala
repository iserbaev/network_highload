package ru.nh.events

import cats.Order
import cats.data.{ Chain, OptionT }
import cats.effect.std.Queue
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import fs2.{ Chunk, Pipe, Stream }
import ru.nh.events.EventBuffer.{ SubscriptionMaxQueued, TopicEvent }

import scala.concurrent.duration._

/** Service that can be used as a hot cache, that periodically fetches new events from a
  * journaled storage for keys, that were requested during subscription
  *
  * @tparam K
  *   event key
  * @tparam E
  *   event type
  */
abstract class ReadEventManager[K, E] extends BatchSupport {

  def dbQueue: Queue[IO, E]

  def buffer: EventBuffer[K, E]
  def store: ReadEventStore[IO, K, E]
  protected def buildTopicEvent(event: E): TopicEvent[K, E]

  def subscribe(key: K): Stream[IO, E] =
    ReadEventManager.subscribe(buffer, key)(store.getLastEventLog(key).map(buildTopicEvent))

  def updateDbQueue(batchSize: Int): IO[Unit] = Stream
    .evalSeq {
      buffer.subscriptions.flatMap { subs =>
        groupBatchesAndFoldMap(Chain.fromIterableOnce(subs), batchSize) { batch =>
          val fromMin = batch.minimumBy(_.from)(Order.fromOrdering).from
          store.getEventLogs(batch.map(_.key), fromMin, batchSize)
        }
      }
    }
    .evalMap(dbQueue.offer)
    .compile
    .drain

  protected def streamDbQueueUpdates: Stream[IO, E] =
    Stream
      .fromQueueUnterminated(dbQueue)

  def streamQueuesGrouped(chunkSize: Int, timeout: FiniteDuration): Stream[IO, Chunk[E]] =
    streamDbQueueUpdates.groupWithin(chunkSize, timeout)

  def bufferPipe(resend: Boolean): Pipe[IO, Chunk[E], Unit] =
    _.map(_.map(buildTopicEvent)).through(buffer.batchSink(resend))
}

object ReadEventManager extends BatchSupport {
  def backgroundPeriodicTask(tickInterval: FiniteDuration)(task: IO[Unit]): Resource[IO, Unit] =
    Stream
      .awakeEvery[IO](tickInterval)
      .evalMap(_ => task)
      .compile
      .drain
      .background
      .void

  def backgroundSyncFromStorageToDBQueue[K, E](
      tickInterval: FiniteDuration,
      batchSize: Int,
      em: ReadEventManager[K, E]
  ): Resource[IO, Unit] =
    backgroundPeriodicTask(tickInterval)(em.updateDbQueue(batchSize))

  /** This method must be used when Event doesn't have lifecycle state, i.e. completed
    * flag always == 'false'
    */
  def cleanEventBufferPeriodically[K, E](
      tickInterval: FiniteDuration,
      buffer: EventBuffer[K, E],
  ): Resource[IO, Unit] =
    backgroundPeriodicTask(tickInterval)(buffer.cleanUnusedSubscriptions)

  private[events] def subscribe[K, E](buffer: EventBuffer[K, E], key: K)(
      lastEventFromLog: OptionT[IO, TopicEvent[K, E]]
  ): Stream[IO, E] =
    Stream.force {
      buffer
        .getSubscription(key)
        .semiflatMap(sub => lastEventFromLog.value.tupleLeft(sub))
        .map {
          case (_, lastEvent) if lastEvent.exists(_.completed) =>
            Stream.fromOption[IO](lastEvent).map(_.eventValue)
          case (subscription, lastEvent) =>
            Stream.fromOption(lastEvent.map(_.eventValue)) ++
              subscription.updates.subscribe(SubscriptionMaxQueued)
        }
        .getOrElse {
          Stream.eval(lastEventFromLog.value).flatMap {
            case Some(event) if event.completed =>
              Stream.emit(event.eventValue)
            case opt =>
              Stream.eval(buffer.addSubscription(key, opt)).flatMap { subscription =>
                Stream.fromOption(opt.map(_.eventValue)) ++
                  subscription.updates.subscribe(SubscriptionMaxQueued)
              }
          }
        }

    }

}
