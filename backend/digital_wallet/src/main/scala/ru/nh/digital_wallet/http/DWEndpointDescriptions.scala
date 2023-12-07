package ru.nh.digital_wallet
package http

import cats.effect.IO
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.digital_wallet.TransferCommand
import ru.nh.http._
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.serverSentEventsBody

class DWEndpointDescriptions(implicit L: LoggerFactory[IO]) {
  import DWJson._

  val resource: String                  = "wallet"
  val resourcePath: EndpointInput[Unit] = resource

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[DWEndpointDescriptions])

  private val endpoint = baseEndpoint(resource, resourcePath)

  val transferCmdPost: BaseEndpoint[TransferCommand, TransferCommandResponse] =
    endpoint.post
      .in("balance_transfer_command")
      .in(jsonBody[TransferCommand])
      .out(jsonBody[TransferCommandResponse])

  val transferEventPost: BaseEndpoint[TransferEvent, TransferEvent] =
    endpoint.post
      .in("balance_transfer_event")
      .in(jsonBody[TransferEvent])
      .out(jsonBody[TransferEvent])

  val accountBalanceStream =
    endpoint.get
      .in("balance" / "stream")
      .in(path[String]("account_id"))
      .out(NoCacheControlHeader)
      .out(XAccelBufferingHeader)
      .out(serverSentEventsBody[IO])

}
