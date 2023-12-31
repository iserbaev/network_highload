package ru.nh.digital_wallet

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import fs2.Stream
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.digital_wallet.BalanceAccessor.BalanceEventLogRow
import ru.nh.digital_wallet.events.{ BalanceCommands, BalanceEvents }

import java.util.UUID
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

class WalletService private (
    balanceEvents: BalanceEvents,
    balanceCommands: BalanceCommands,
    accessor: BalanceAccessor[IO],
    phaseStatusAccessor: PhaseStatusAccessor[IO]
)(implicit log: Logger[IO]) {
  def publishTransferCommand(cmd: TransferCommand): IO[Either[Throwable, TransferCommandResponse]] =
    balanceCommands
      .publish(cmd.transactionId, cmd)
      .flatMap {
        case Some(value) =>
          val phaseStatusInit = IO.realTimeInstant
            .flatMap { i =>
              phaseStatusAccessor.upsert(
                PhaseStatus(cmd.transactionId, cmd.fromAccount, cmd.toAccount, false, none, false, none, i, false)
              )
            }
            .flatTap(c => log.info(s"Phase status record stored $c"))

          val from = balanceEvents
            .subscribe(value.fromAccount)
            .collectFirst {
              case e if e.transactionId == value.transactionId => e
            }
            .compile
            .lastOrError
            .flatTap { e =>
              phaseStatusAccessor.setFromCompleted(e.transactionId, e.createdAt) <*
                log.info(show"Completed FROM ${e.toEvent}")
            }

          val to = balanceEvents
            .subscribe(value.toAccount)
            .collectFirst {
              case e if e.transactionId == value.transactionId => e
            }
            .compile
            .lastOrError
            .flatTap { e =>
              phaseStatusAccessor.setToCompleted(e.transactionId, e.createdAt) <*
                log.info(show"Completed TO ${e.toEvent}")
            }

          log.info(show"Transfer command stored $cmd") *>
            phaseStatusInit *>
            (from, to).mapN((f, t) => TransferCommandResponse(f.toEvent, t.toEvent))
        case None =>
          IO.raiseError(new RuntimeException(s"Publish command failed $cmd"))
      }
      .attempt
      .flatTap {
        _.leftTraverse(_ =>
          compensate(cmd.transactionId, cmd.fromAccount) *>
            compensate(cmd.transactionId, cmd.toAccount)
        )
      } <* phaseStatusAccessor
      .completePhase(cmd.transactionId)
      .flatTap(_ => log.info(s"Phase status done [${cmd.transactionId}]"))

  def publishTransferEvent(e: TransferEvent): IO[Either[Throwable, TransferEvent]] =
    balanceEvents
      .publish(e.accountId, e)
      .flatMap(IO.fromOption(_)(new RuntimeException(s"Publish event failed $e")))
      .flatTap(_ => log.info(show"Transfer completed $e"))
      .attempt
      .flatTap(_.leftTraverse(er => log.error(er)(show"Failed transfer event $e")))
      .map(_.map(_.toEvent))

  def balanceStream(accountId: String): Stream[IO, BalanceSnapshot] =
    Stream
      .awakeEvery[IO](3.seconds)
      .evalMap(_ => getBalance(accountId))
      .flatMap(Stream.fromOption(_))

  def getBalance(accountId: String): IO[Option[BalanceSnapshot]] =
    balanceEvents.balanceStatuses
      .snapshot(accountId)
      .value
      .flatMap {
        case Some(value) =>
          value.some.pure[IO]
        case None =>
          accessor
            .getBalanceSnapshot(accountId)
            .flatTap(_.traverse_(balanceEvents.balanceStatuses.add(accountId, _)))
      }

  private def compensate(transactionId: UUID, accountId: String): IO[Unit] =
    accessor.getEventLog(accountId, transactionId).flatMap { events =>
      def program(value: BalanceEventLogRow): IO[Unit] =
        log.info(show"Compensate transfer ${value.toEvent}") *>
          accessor.logTransferEvent(value.toEvent.revert).void

      if (events.length == 1) program(events.head) else IO.unit
    }

  def phaseStatusHeartbeat(ttl: FiniteDuration): Resource[IO, Unit] = {
    val program = phaseStatusAccessor
      .getNonCompletedPhases(ttl, 1000)
      .flatMap { seq =>
        log.info(s"Compensate non done phases [${seq.map(_.transactionId).mkString(",")}]").whenA(seq.nonEmpty) *>
          seq.traverse { phaseStatus =>
            compensate(phaseStatus.transactionId, phaseStatus.fromAccount) *>
              compensate(phaseStatus.transactionId, phaseStatus.toAccount) *>
              phaseStatusAccessor.completePhase(phaseStatus.transactionId)
          }
      }

    Stream
      .awakeEvery[IO](ttl)
      .evalMap(_ => program)
      .compile
      .drain
      .background
      .void
  }

}

object WalletService {
  def resource(
      balanceCommands: BalanceCommands,
      accessor: BalanceAccessor[IO],
      phaseStatusAccessor: PhaseStatusAccessor[IO]
  )(implicit L: LoggerFactory[IO]): Resource[IO, WalletService] =
    Resource
      .eval(L.fromClass(classOf[WalletService]))
      .map { implicit log =>
        new WalletService(balanceCommands.balanceEvents, balanceCommands, accessor, phaseStatusAccessor)
      }
      .flatTap(_.phaseStatusHeartbeat(5.second))
}
