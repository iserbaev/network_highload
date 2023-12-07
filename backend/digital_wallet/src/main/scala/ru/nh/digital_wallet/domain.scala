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
  def fromEvent(index: Long): TransferEvent =
    TransferEvent(
      accountId = fromAccount,
      transactionId = transactionId,
      changeIndex = index,
      mint = None,
      spend = Some(amount),
      description = s"Spend $amount from [$fromAccount] to [$toAccount] according to $transactionId"
    )

  def toEvent(index: Long): TransferEvent =
    TransferEvent(
      accountId = fromAccount,
      transactionId = transactionId,
      changeIndex = index,
      mint = Some(amount),
      spend = None,
      description = s"Mint $amount to [$toAccount] from [$fromAccount] according to $transactionId"
    )
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

final case class BalanceSnapshot(
    accountId: String,
    lastChangeIndex: Long,
    mintSum: Int,
    spendSum: Int,
    lastModifiedAt: Instant
)
