package ru.nh.digital_wallet

import java.time.Instant
import java.util.UUID

final case class TransferCommand(
    fromAccount: String,
    toAccount: String,
    amount: Int,
    currencyType: String,
    transactionId: UUID
)

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
