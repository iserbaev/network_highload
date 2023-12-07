package ru.nh.digital_wallet

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import fs2.Stream
import ru.nh.digital_wallet.events.{ BalanceCommands, BalanceEvents }

import scala.concurrent.duration.DurationInt

class WalletService private (
    balanceEvents: BalanceEvents,
    balanceCommands: BalanceCommands,
    accessor: BalanceAccessor[IO]
) {
  def publishTransferCommand(cmd: TransferCommand): IO[Either[Throwable, TransferCommandResponse]] =
    balanceCommands
      .publish(cmd.transactionId, cmd)
      .flatMap {
        case Some(value) =>
          val from = balanceEvents
            .subscribe(value.fromAccount)
            .collectFirst {
              case e if e.transactionId == value.transactionId => e
            }
            .compile
            .lastOrError

          val to = balanceEvents
            .subscribe(value.fromAccount)
            .collectFirst {
              case e if e.transactionId == value.transactionId => e
            }
            .compile
            .lastOrError

          (from, to).mapN((f, t) => TransferCommandResponse(f.toEvent, t.toEvent))
        case None =>
          IO.raiseError(new RuntimeException(s"Publish command failed $cmd"))
      }
      .attempt

  def publishTransferEvent(e: TransferEvent): IO[Either[Throwable, TransferEvent]] =
    balanceEvents
      .publish(e.accountId, e)
      .flatMap(IO.fromOption(_)(new RuntimeException(s"Publish event failed $e")))
      .attempt
      .map(_.map(_.toEvent))

  def balanceStream(accountId: String): Stream[IO, BalanceSnapshot] =
    Stream
      .awakeEvery[IO](3.seconds)
      .evalMap(_ => getBalance(accountId))
      .flatMap(Stream.fromOption(_))

  def getBalance(accountId: String): IO[Option[BalanceSnapshot]] =
    accessor.getBalanceSnapshot(accountId)

}

object WalletService {
  def resource(
      balanceEvents: BalanceEvents,
      balanceCommands: BalanceCommands,
      accessor: BalanceAccessor[IO]
  ): Resource[IO, WalletService] =
    Resource.pure(new WalletService(balanceEvents, balanceCommands, accessor))
}
