package ru.nh.digital_wallet

import cats.effect.IO
import fs2.Stream

trait WalletService {
  def publishTransferCommand(cmd: TransferCommand): IO[Either[Throwable, TransferCommandResponse]]

  def publishTransferEvent(e: TransferEvent): IO[Either[Throwable, BalanceSnapshot]]

  def balanceStream(accountId: String): Stream[IO, BalanceSnapshot]

  def getBalance(accountId: String): IO[Option[BalanceSnapshot]]

}
