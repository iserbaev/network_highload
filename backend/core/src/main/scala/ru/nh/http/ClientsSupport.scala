package ru.nh.http

import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.Decoder
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.headers.{ Accept, Authorization, `Content-Type` }
import org.http4s.{ AuthScheme, Credentials, Headers, MediaType, Request }
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.DurationInt

class ClientsSupport(client: Client[IO])(implicit logger: Logger[IO]) {
  def jwtHeader(authToken: String): Headers =
    Headers(
      Authorization(Credentials.Token(AuthScheme.Bearer, authToken)),
      `Content-Type`(MediaType.application.json),
      Accept(MediaType.application.json)
    )

  def runRequest(request: Request[IO]): IO[Unit] =
    client
      .run(request)
      .use { response =>
        if (response.status.isSuccess)
          logger
            .info(
              s"Outgoing HTTP request: status=${response.status.code} method=${request.method.name} uri=${request.uri}"
            )
        else {
          logger
            .error(
              s"Outgoing HTTP request failed: status=${response.status.code} method=${request.method.name} uri=${request.uri}"
            ) *> IO.raiseError(new Exception(s"Outgoing request failed with status=${response.status.code}"))
        }
      }

  def runQueryRequest[Resp](request: Request[IO])(implicit decoder: Decoder[Resp]): IO[Resp] =
    client
      .run(request)
      .use { response =>
        if (response.status.isSuccess)
          logger
            .info(
              s"Outgoing HTTP request: status=${response.status.code} method=${request.method.name} uri=${request.uri}"
            ) *> response.as[Resp]
        else {
          logger
            .error(
              s"Outgoing HTTP request failed: status=${response.status.code} method=${request.method.name} uri=${request.uri}"
            ) *> IO.raiseError(new Exception(s"Outgoing request failed with status=${response.status.code}"))
        }
      }

}

object ClientsSupport {
  def createClient(implicit log: Logger[IO]): Resource[IO, ClientsSupport] =
    BlazeClientBuilder[IO]
      .withRequestTimeout(180.seconds)
      .withResponseHeaderTimeout(170.seconds)
      .withIdleTimeout(190.seconds)
      .withMaxWaitQueueLimit(1024)
      .resource
      .map(new ClientsSupport(_))
}
