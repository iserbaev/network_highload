package ru.nh.digital_wallet

import cats.Show
import cats.syntax.all._

import java.time.Instant
import java.util.UUID

final case class TransferCommand(
    transactionId: UUID,
    fromAccount: String,
    toAccount: String,
    amount: Int,
    currencyType: String
)

object TransferCommand {
  implicit val tcShow: Show[TransferCommand] = { tc =>
    show"[${tc.transactionId}] command to transfer ${tc.fromAccount} => ${tc.toAccount} ${tc.amount} ${tc.currencyType}"
  }
}

final case class TransferCommandResponse(
    fromAccountBalance: TransferEvent,
    toAccountBalance: TransferEvent
)

final case class TransferEvent(
    accountId: String,
    transactionId: UUID,
    mint: Option[Int],
    spend: Option[Int],
    description: String,
    changeIndex: Long,
)

object TransferEvent {
  implicit val teShow: Show[TransferEvent] = { te =>
    show"[${te.transactionId}] transfer event ${te.accountId} / spend ${te.spend} / mint ${te.mint} / idx ${te.changeIndex}"
  }
}

final case class BalanceSnapshot(
    accountId: String,
    lastChangeIndex: Long,
    mintSum: Int,
    spendSum: Int,
    lastModifiedAt: Instant
)

final case class PhaseStatus(
    transactionId: UUID,
    fromTransferCompleted: Boolean,
    fromTransferCreatedAt: Option[Instant],
    toTransferCompleted: Boolean,
    toTransferCreatedAt: Option[Instant],
    createdAt: Instant
)
