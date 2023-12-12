package ru.nh.digital_wallet.events

import cats.data.{ Chain, NonEmptyChain }
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.syntax.all._
import fs2.{ Chunk, Pipe }
import org.typelevel.log4cats.LoggerFactory
import ru.nh.digital_wallet.BalanceAccessor.BalanceEventLogRow
import ru.nh.digital_wallet.events.store.BalanceEventsStore
import ru.nh.digital_wallet.{ BalanceAccessor, BalanceSnapshot, TransferEvent }
import ru.nh.events.{ EventBuffer, ReadEventManager, ReadWriteEventManager, SnapshotManager }

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

class BalanceEvents(
    val store: BalanceEventsStore,
    val buffer: EventBuffer[String, BalanceEventLogRow],
    val dbQueue: Queue[IO, BalanceEventLogRow],
    val apiQueue: Queue[IO, BalanceEventLogRow],
    val balanceStatuses: SnapshotManager[String, BalanceEventLogRow, BalanceSnapshot]
) extends ReadWriteEventManager[TransferEvent, String, BalanceEventLogRow] {
  protected def buildTopicEvent(event: BalanceEventLogRow): EventBuffer.TopicEvent[String, BalanceEventLogRow] =
    EventBuffer.TopicEvent(
      event.accountId,
      event,
      completed = false,
      event.changeIndex,
      event.createdAt
    )

  def updateBalanceStatusToDatabasePipe: Pipe[IO, Chunk[BalanceSnapshot], Unit] =
    _.evalMap { chunk =>
      NonEmptyChain
        .fromChain(chunk.toChain)
        .traverse_(nec => store.accessor.upsertBalanceSnapshotBatch(nec))
    }
}

object BalanceEvents {

  def apply(
      accessor: BalanceAccessor[IO],
      tickInterval: FiniteDuration,
      updatesChannelTick: FiniteDuration,
      updatesChannelListener: () => fs2.Stream[IO, BalanceEventLogRow],
      limit: Int,
      eventBufferTtl: FiniteDuration
  )(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, BalanceEvents] =
    (
      Resource.eval(Queue.unbounded[IO, BalanceEventLogRow]),
      Resource.eval(Queue.unbounded[IO, BalanceEventLogRow]),
      EventBuffer.resource[String, BalanceEventLogRow],
      SnapshotManager.resource[String, BalanceEventLogRow, BalanceSnapshot](snapshots.balanceSnapshotBuilder)
    )
      .flatMapN { (dbQueue, apiQueue, buffer, bs) =>
        val em = new BalanceEvents(BalanceEventsStore(accessor), buffer, dbQueue, apiQueue, bs)

        val memoizedSnapshots: Pipe[IO, Chunk[BalanceEventLogRow], Unit] =
          _.evalMap(_.traverse_ { b =>
            bs.snapshot(b.accountId)
              .value
              .flatMap {
                case Some(_) =>
                  bs.updateWith(b.accountId, b)
                case None =>
                  accessor.getEventLogs(b.accountId).flatMap { events =>
                    bs.build(b.accountId, Chain.fromSeq(events))
                  }
              }
          })

        val listenUpdates = ReadEventManager
          .backgroundPeriodicTask(updatesChannelTick) {
            updatesChannelListener()
              .through(_.evalTap(dbQueue.offer))
              .compile
              .drain
          }

        val saveBalanceStatusTask =
          bs.snapshots
            .groupWithin(limit, 5.seconds)
            .through(em.updateBalanceStatusToDatabasePipe)
            .compile
            .drain
            .background
            .void

        val syncEventsFromQueues =
          em.streamQueuesGrouped(limit, tickInterval)
            .broadcastThrough(
              em.bufferPipe(false),
              memoizedSnapshots
            )
            .compile
            .drain
            .background
            .void

        ReadEventManager.cleanEventBufferPeriodically(eventBufferTtl, buffer) *>
          listenUpdates *> syncEventsFromQueues *> saveBalanceStatusTask
            .as(em)
      }
}
