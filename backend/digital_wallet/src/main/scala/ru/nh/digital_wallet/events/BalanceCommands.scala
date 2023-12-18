package ru.nh.digital_wallet.events

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.digital_wallet.BalanceAccessor.BalanceCommandLogRow
import ru.nh.digital_wallet.events.store.BalanceCommandsStore
import ru.nh.digital_wallet.{ BalanceAccessor, TransferCommand }
import ru.nh.events.{ EventBuffer, ReadEventManager, ReadWriteEventManager }

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

class BalanceCommands(
    val store: BalanceCommandsStore,
    val buffer: EventBuffer[UUID, BalanceCommandLogRow],
    val dbQueue: Queue[IO, BalanceCommandLogRow],
    val apiQueue: Queue[IO, BalanceCommandLogRow],
    val balanceEvents: BalanceEvents
) extends ReadWriteEventManager[TransferCommand, UUID, BalanceCommandLogRow] {
  protected def buildTopicEvent(event: BalanceCommandLogRow): EventBuffer.TopicEvent[UUID, BalanceCommandLogRow] =
    EventBuffer.TopicEvent(
      event.transactionId,
      event,
      completed = false,
      event.changeIndex,
      event.createdAt
    )

  override def publishRecorded(event: BalanceCommandLogRow, resend: Boolean): IO[Unit] =
    super.publishRecorded(event, resend) <*
      balanceEvents.publish(event.fromAccount, event.toEventFrom) <*
      balanceEvents.publish(event.toAccount, event.toEventTo)

  private def publishEvents(cmd: BalanceCommandLogRow) =
    balanceEvents.publish(cmd.fromAccount, cmd.toEventFrom) <*
      balanceEvents.publish(cmd.toAccount, cmd.toEventTo)

  override def publishRecordedBatch(events: Vector[BalanceCommandLogRow], resend: Boolean): IO[Unit] =
    super.publishRecordedBatch(events, resend) <* events.traverse_(publishEvents)
}

object BalanceCommands {

  def apply(
      accessor: BalanceAccessor[IO],
      tickInterval: FiniteDuration,
      useUpdatesChannel: Boolean,
      updatesChannelTick: FiniteDuration,
      updatesChannelListener: () => fs2.Stream[IO, BalanceCommandLogRow],
      limit: Int,
      eventBufferTtl: FiniteDuration,
      balanceEvents: BalanceEvents
  )(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, BalanceCommands] =
    (
      Resource.eval(Queue.unbounded[IO, BalanceCommandLogRow]),
      Resource.eval(Queue.unbounded[IO, BalanceCommandLogRow]),
      EventBuffer.resource[UUID, BalanceCommandLogRow]
    )
      .flatMapN { (dbQueue, apiQueue, buffer) =>
        val em = new BalanceCommands(BalanceCommandsStore(accessor), buffer, dbQueue, apiQueue, balanceEvents)

        val listenUpdates = ReadEventManager
          .backgroundPeriodicTask(updatesChannelTick) {
            updatesChannelListener()
              .through(_.evalTap(dbQueue.offer))
              .compile
              .drain
          }

        val syncFromUpdatesLogToDbQueue = ReadEventManager.backgroundSyncFromStorageToDBQueue(tickInterval, limit, em)

        val syncEventsFromQueues =
          em.streamQueuesGrouped(limit, tickInterval)
            .broadcastThrough(
              em.bufferPipe(false)
            )
            .compile
            .drain
            .background
            .void

        ReadEventManager.cleanEventBufferPeriodically(eventBufferTtl, buffer) *>
          (if (useUpdatesChannel) listenUpdates else syncFromUpdatesLogToDbQueue) *>
          syncEventsFromQueues
            .as(em)
      }
}
