package ru.nh.digital_wallet

import cats.NonEmptyTraverse
import cats.data.OptionT
import cats.syntax.all._
import ru.nh.digital_wallet.BalanceAccessor.{ BalanceCommandLogRow, BalanceEventLogRow }

import java.time.Instant
import java.util.UUID

trait BalanceAccessor[F[_]] {
  def logTransferCommand(cmd: TransferCommand): F[BalanceCommandLogRow]
  def logTransferCommandsBatch[R[_]: NonEmptyTraverse](cmd: R[TransferCommand]): F[Vector[BalanceCommandLogRow]]
  def getLastCmdLog(transactionId: UUID): OptionT[F, BalanceCommandLogRow]
  def getCmdLogs[R[_]: NonEmptyTraverse](keys: R[UUID], from: Instant, limit: Int): F[Vector[BalanceCommandLogRow]]

  def logTransferEvent(e: TransferEvent): F[BalanceEventLogRow]
  def logTransferEventBatch[R[_]: NonEmptyTraverse](e: R[TransferEvent]): F[Vector[BalanceEventLogRow]]
  def getLastEventLog(accountId: String): OptionT[F, BalanceEventLogRow]
  def getEventLogs(accountId: String): F[Vector[BalanceEventLogRow]]
  def getEventLog(accountId: String, transactionId: UUID): F[Vector[BalanceEventLogRow]]
  def getEventLogs[R[_]: NonEmptyTraverse](keys: R[String], from: Instant, limit: Int): F[Vector[BalanceEventLogRow]]

  def getBalanceSnapshot(accountId: String): F[Option[BalanceSnapshot]]

  def upsertBalanceSnapshot(s: BalanceSnapshot): F[Unit]

  def upsertBalanceSnapshotBatch[R[_]: NonEmptyTraverse](e: R[BalanceSnapshot]): F[Unit]

}

object BalanceAccessor {
  import io.circe._

  final case class BalanceCommandLogRow(
      transactionId: UUID,
      fromAccount: String,
      toAccount: String,
      amount: Int,
      currencyType: String,
      changeIndex: Long,
      createdAt: Instant
  ) {
    def toEventFrom =
      TransferEvent(
        fromAccount,
        transactionId,
        none,
        spend = amount.some,
        s"Transfer from $fromAccount to $toAccount by $transactionId",
        changeIndex
      )
    def toEventTo =
      TransferEvent(
        toAccount,
        transactionId,
        mint = amount.some,
        none,
        s"Transfer to $toAccount from $fromAccount by $transactionId",
        changeIndex
      )
  }
  object BalanceCommandLogRow {
    implicit val balanceCommandLogSnakeCaseDecoder: Decoder[BalanceCommandLogRow] = (c: HCursor) =>
      for {
        transactionId <- c.downField("transaction_id").as[String]
        accountIdFrom <- c.downField("account_id_from").as[String]
        accountIdTo   <- c.downField("account_id_to").as[String]
        amount        <- c.downField("amount").as[Int]
        currencyType  <- c.downField("currency_code_letter").as[String]
        changeIndex   <- c.downField("change_index").as[Long]
        createdAt     <- c.downField("created_at").as[Instant]
      } yield {
        BalanceCommandLogRow(
          UUID.fromString(transactionId),
          accountIdFrom,
          accountIdTo,
          amount,
          currencyType,
          changeIndex,
          createdAt
        )
      }
  }

  final case class BalanceEventLogRow(
      accountId: String,
      transactionId: UUID,
      mintChange: Option[Int],
      spendChange: Option[Int],
      description: String,
      changeIndex: Long,
      createdAt: Instant
  ) {
    def toEvent: TransferEvent =
      TransferEvent(accountId, transactionId, mintChange, spendChange, description, changeIndex)
  }

  object BalanceEventLogRow {
    implicit val balanceEventLogSnakeCaseDecoder: Decoder[BalanceEventLogRow] = (c: HCursor) =>
      for {
        accountId     <- c.downField("account_id").as[String]
        transactionId <- c.downField("transaction_id").as[UUID]
        mintChange    <- c.downField("mint_change").as[Option[Int]]
        spendChange   <- c.downField("spend_change").as[Option[Int]]
        changeDesc    <- c.downField("change_description").as[String]
        changeIndex   <- c.downField("change_index").as[Long]
        createdAt     <- c.downField("created_at").as[Instant]
      } yield {
        BalanceEventLogRow(
          accountId,
          transactionId,
          mintChange,
          spendChange,
          changeDesc,
          changeIndex,
          createdAt
        )
      }
  }
}
