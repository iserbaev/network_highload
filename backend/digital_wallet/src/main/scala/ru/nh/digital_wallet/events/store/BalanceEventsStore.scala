package ru.nh.digital_wallet.events.store

import cats.NonEmptyTraverse
import cats.data.{ Chain, OptionT }
import cats.effect.IO
import cats.syntax.all._
import ru.nh.digital_wallet.BalanceAccessor.BalanceEventLogRow
import ru.nh.digital_wallet.{ BalanceAccessor, TransferEvent }
import ru.nh.events.ReadWriteEventStore

import java.time.Instant

class BalanceEventsStore private [events] (val accessor: BalanceAccessor[IO])
    extends ReadWriteEventStore[IO, TransferEvent, String, BalanceEventLogRow] {
  def recordValueToEventLog(key: String, value: TransferEvent): OptionT[IO, BalanceEventLogRow] =
    OptionT.liftF(accessor.logTransferEvent(value))

  def recordValuesBatch[R[_]: NonEmptyTraverse](values: R[(String, TransferEvent)]): IO[Vector[BalanceEventLogRow]] =
    accessor.logTransferEventBatch(values.map(_._2))

  def getLastEventLog(key: String): OptionT[IO, BalanceEventLogRow] = accessor.getLastEventLog(key)

  def getEventLogs(key: String): IO[Chain[BalanceEventLogRow]] = ???

  def getEventLogs[R[_]: NonEmptyTraverse](
      keys: R[String],
      lastModifiedAt: Instant,
      limit: Int
  ): IO[Vector[BalanceEventLogRow]] = ???
}

object BalanceEventsStore {
  def apply(accessor: BalanceAccessor[IO]) = new BalanceEventsStore(accessor)
}
