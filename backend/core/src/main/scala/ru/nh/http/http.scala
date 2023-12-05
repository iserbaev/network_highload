package ru.nh

import cats.effect.IO
import cats.syntax.all._
import ru.nh.auth.AuthService
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.headers.CacheDirective
import sttp.model.{ Header, StatusCode }
import sttp.tapir.EndpointIO.FixedHeader
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.{ PartialServerEndpoint, ServerEndpoint }
import sttp.tapir.{ Endpoint, EndpointInput, endpoint, header, statusCode }

import scala.concurrent.duration.FiniteDuration

package object http {
  import json.all._
  import tapirImplicits._

  type SEndpoint          = ServerEndpoint[Fs2Streams[IO], IO]
  type BaseEndpoint[I, O] = Endpoint[Unit, I, (StatusCode, Option[ErrorResponse]), O, Any]
  type SecuredEndpoint[I, O] =
    PartialServerEndpoint[String, AuthService.Auth, I, (StatusCode, Option[ErrorResponse]), O, Any, IO]

  val NoCacheControlHeader: FixedHeader[Unit]  = header(Header.cacheControl(CacheDirective.NoCache))
  val XAccelBufferingHeader: FixedHeader[Unit] = header("X-Accel-Buffering", "no")

  def baseEndpoint(resource: String, resourcePath: EndpointInput[Unit]): BaseEndpoint[Unit, Unit] = endpoint
    .in(resourcePath)
    .tag(resource)
    .errorOut(statusCode)
    .errorOut(jsonBody[Option[ErrorResponse]])

  def securedEndpoint(
      resource: String,
      resourcePath: EndpointInput[Unit],
      authService: AuthService
  ): SecuredEndpoint[Unit, Unit] = baseEndpoint(resource, resourcePath)
    .securityIn(sttp.tapir.auth.bearer[String]())
    .serverSecurityLogic(
      authService
        .authorize(_)
        .attempt
        .map {
          _.leftMap {
            case _: NoSuchElementException   => (StatusCode.NotFound, none[ErrorResponse])
            case _: IllegalArgumentException => (StatusCode.BadRequest, none[ErrorResponse])
            case _                           => (StatusCode.BadRequest, none[ErrorResponse])
          }.flatMap {
            case Some(value) => value.asRight[(StatusCode, Option[ErrorResponse])]
            case None        => (StatusCode.BadRequest, none[ErrorResponse]).asLeft[AuthService.Auth]
          }
        }
    )

  def healthCheckRoute(checks: IO[Unit], timeout: FiniteDuration): SEndpoint = {
    val resource: String                  = "healthz"
    val resourcePath: EndpointInput[Unit] = resource
    val description = endpoint
      .in(resourcePath)
      .tag(resource)
      .errorOut(statusCode)
      .out(statusCode)

    description.serverLogic { _ =>
      checks.timeout(timeout).attempt.map {
        _.leftMap {
          case _: IllegalArgumentException => StatusCode.BadRequest
          case _                           => StatusCode.InternalServerError
        }.flatMap { _ =>
          StatusCode.Ok.asRight
        }
      }
    }
  }
}
