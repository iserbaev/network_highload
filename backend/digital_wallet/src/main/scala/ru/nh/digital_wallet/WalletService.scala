package ru.nh.digital_wallet

import cats.effect.{ IO, Resource }
import fs2.Stream

trait WalletService {
  def publishTransferCommand(cmd: TransferCommand): IO[Either[Throwable, TransferCommandResponse]]

  def publishTransferEvent(e: TransferEvent): IO[Either[Throwable, BalanceSnapshot]]

  def balanceStream(accountId: String): Stream[IO, BalanceSnapshot]

  def getBalance(accountId: String): IO[Option[BalanceSnapshot]]

}

object WalletService {
  def noop: Resource[IO, WalletService] = Resource.pure {
    new WalletService {
      override def publishTransferCommand(cmd: TransferCommand): IO[Either[Throwable, TransferCommandResponse]] =
        IO.stub

      override def publishTransferEvent(e: TransferEvent): IO[Either[Throwable, BalanceSnapshot]] = IO.stub

      override def balanceStream(accountId: String): Stream[IO, BalanceSnapshot] = Stream.empty

      override def getBalance(accountId: String): IO[Option[BalanceSnapshot]] = IO.stub
    }
  }
}
