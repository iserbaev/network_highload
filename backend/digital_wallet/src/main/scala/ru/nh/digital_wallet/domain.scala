package ru.nh.digital_wallet

import java.time.Instant
import java.util.UUID

final case class TransferCommand(
    fromAccount: String,
    toAccount: String,
    amount: Int,
    currencyType: String,
    transactionId: UUID
) {
  def fromEvent: TransferEvent =
    TransferEvent(
      accountId = fromAccount,
      transactionId = transactionId,
      mint = None,
      spend = Some(amount),
      description = s"Spend $amount from [$fromAccount] to [$toAccount] according to $transactionId"
    )

  def toEvent: TransferEvent =
    TransferEvent(
      accountId = fromAccount,
      transactionId = transactionId,
      mint = Some(amount),
      spend = None,
      description = s"Mint $amount to [$toAccount] from [$fromAccount] according to $transactionId"
    )
}

final case class TransferCommandResponse(
    fromAccountBalance: BalanceSnapshot,
    toAccountBalance: BalanceSnapshot
)

final case class TransferEvent(
    accountId: String,
    transactionId: UUID,
    mint: Option[Int],
    spend: Option[Int],
    description: String
)

final case class BalanceSnapshot(
    accountId: String,
    mintSum: Int,
    spendSum: Int,
    lastChangeIndex: Int,
    lastModifiedAt: Instant
)
