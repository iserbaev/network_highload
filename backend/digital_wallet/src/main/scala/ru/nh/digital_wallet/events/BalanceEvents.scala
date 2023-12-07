package ru.nh.digital_wallet.events

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.db.PgListener
import ru.nh.digital_wallet.BalanceAccessor.BalanceEventLogRow
import ru.nh.digital_wallet.events.store.BalanceEventsStore
import ru.nh.digital_wallet.{ BalanceAccessor, TransferEvent }
import ru.nh.events.{ EventBuffer, ReadEventManager, ReadWriteEventManager }

import scala.concurrent.duration.FiniteDuration

class BalanceEvents(
    val store: BalanceEventsStore,
    val buffer: EventBuffer[String, BalanceEventLogRow],
    val dbQueue: Queue[IO, BalanceEventLogRow],
    val apiQueue: Queue[IO, BalanceEventLogRow]
) extends ReadWriteEventManager[TransferEvent, String, BalanceEventLogRow] {
  protected def buildTopicEvent(event: BalanceEventLogRow): EventBuffer.TopicEvent[String, BalanceEventLogRow] =
    EventBuffer.TopicEvent(
      event.accountId,
      event,
      completed = false,
      event.changeIndex,
      event.createdAt
    )
}

object BalanceEvents {

  def apply(
      accessor: BalanceAccessor[IO],
      tickInterval: FiniteDuration,
      updatesChannelTick: FiniteDuration,
      updatesChannelListener: PgListener[BalanceEventLogRow],
      limit: Int,
  )(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, BalanceEvents] =
    (
      Resource.eval(Queue.unbounded[IO, BalanceEventLogRow]),
      Resource.eval(Queue.unbounded[IO, BalanceEventLogRow]),
      EventBuffer.resource[String, BalanceEventLogRow]
    )
      .flatMapN { (dbQueue, apiQueue, buffer) =>
        val em = new BalanceEvents(BalanceEventsStore(accessor), buffer, dbQueue, apiQueue)

        val listenUpdates = ReadEventManager
          .backgroundPeriodicTask(updatesChannelTick) {
            updatesChannelListener
              .listen()
              .through(_.evalTap(dbQueue.offer))
              .compile
              .drain
          }

        val syncEventsFromQueues =
          em.streamQueuesGrouped(limit, tickInterval)
            .broadcastThrough(
              em.bufferPipe(false)
            )
            .compile
            .drain
            .background
            .void

        listenUpdates *> syncEventsFromQueues
          .as(em)
      }
}
