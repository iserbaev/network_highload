package ru.nh.digital_wallet

import cats.NonEmptyTraverse
import ru.nh.digital_wallet.BalanceAccessor.{BalanceCommandLogRow, BalanceEventLogRow}

import java.time.Instant
import java.util.UUID

trait BalanceAccessor[F[_]] {
  def logTransferCommand(cmd: TransferCommand): F[BalanceCommandLogRow]
  def logTransferCommandsBatch[R[_]: NonEmptyTraverse](cmd: R[TransferCommand]): F[Vector[BalanceCommandLogRow]]

  def logTransferEvent(e: TransferEvent): F[BalanceEventLogRow]
  def logTransferEventBatch[R[_]: NonEmptyTraverse](e: R[TransferEvent]): F[Vector[BalanceEventLogRow]]

  def getBalanceSnapshot(accountId: UUID): F[Option[BalanceSnapshot]]

}

object BalanceAccessor {
  final case class BalanceCommandLogRow(
      transactionId: UUID,
      fromAccount: String,
      toAccount: String,
      amount: Int,
      currencyType: String,
      changeIndex: Long,
      createdAt: Instant
  )

  final case class BalanceEventLogRow(
      accountId: String,
      transactionId: UUID,
      mintChange: Option[Int],
      spendChange: Option[Int],
      description: String,
      changeIndex: Long,
      createdAt: Instant
  )
}
