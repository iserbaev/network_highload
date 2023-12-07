package ru.nh.digital_wallet.http

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.digital_wallet.WalletService
import ru.nh.http._
import sttp.model.StatusCode

import scala.concurrent.duration.FiniteDuration

class DWEndpoints(
    service: WalletService,
    val sseHeartbeatPeriod: FiniteDuration
)(
    implicit L: LoggerFactory[IO]
) extends SseSupport {
  import ru.nh.digital_wallet.DWJson._

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[DWEndpoints])

  private val endpointDescriptions = new DWEndpointDescriptions()

  val transferCmdPost: SEndpoint = endpointDescriptions.transferCmdPost
    .serverLogic { cmd =>
      val program = service
        .publishTransferCommand(cmd)
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex =>
              (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, cmd.transactionId.toString, 0).some)
          }
        }

      IO(cmd.currencyType.length > 3).ifM(
        IO((StatusCode.BadRequest, none).asLeft),
        program
      )
    }

  val transferEventPost: SEndpoint = endpointDescriptions.transferEventPost
    .serverLogic { event =>
      service
        .publishTransferEvent(event)
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex =>
              (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, event.transactionId.toString, 0).some)
          }
        }
    }

  val accountBalanceStream: SEndpoint = endpointDescriptions.accountBalanceStream
    .serverLogicSuccess { accountId =>
      log
        .debug(s"Start http account balance stream for [${accountId}]") *>
        IO.delay {
          service
            .balanceStream(accountId)
            .through(ssePipe)
            .onFinalizeCase(ec => log.debug(s"Finalized account balance stream for [${accountId}], $ec"))
        }
    }

  val all: NonEmptyList[SEndpoint] = NonEmptyList.of(
    transferCmdPost,
    transferEventPost,
    accountBalanceStream
  )
}
