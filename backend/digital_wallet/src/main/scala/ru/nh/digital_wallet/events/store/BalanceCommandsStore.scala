package ru.nh.digital_wallet.events.store

import cats.NonEmptyTraverse
import cats.data.{ Chain, OptionT }
import cats.effect.IO
import cats.syntax.all._
import ru.nh.digital_wallet.BalanceAccessor._
import ru.nh.digital_wallet.{ BalanceAccessor, TransferCommand }
import ru.nh.events.ReadWriteEventStore

import java.time.Instant
import java.util.UUID

class BalanceCommandsStore private (accessor: BalanceAccessor[IO])
    extends ReadWriteEventStore[IO, TransferCommand, UUID, BalanceCommandLogRow] {
  def recordValueToEventLog(key: UUID, value: TransferCommand): OptionT[IO, BalanceCommandLogRow] =
    OptionT.liftF {
      accessor.logTransferCommand(value)
    }

  def recordValuesBatch[R[_]: NonEmptyTraverse](values: R[(UUID, TransferCommand)]): IO[Vector[BalanceCommandLogRow]] =
    accessor.logTransferCommandsBatch(values.map(_._2))

  override def getLastEventLog(key: UUID): OptionT[IO, BalanceCommandLogRow] = accessor.getLastCmdLog(key)

  override def getEventLogs(key: UUID): IO[Chain[BalanceCommandLogRow]] = ???

  override def getEventLogs[R[_]: NonEmptyTraverse](
      keys: R[UUID],
      lastModifiedAt: Instant,
      limit: Int
  ): IO[Vector[BalanceCommandLogRow]] = ???
}

object BalanceCommandsStore {
  def apply(accessor: BalanceAccessor[IO]) = new BalanceCommandsStore(accessor)
}
