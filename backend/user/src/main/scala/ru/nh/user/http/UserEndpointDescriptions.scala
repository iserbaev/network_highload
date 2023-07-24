package ru.nh.user.http

import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.http.ErrorResponse
import ru.nh.user.{ RegisterUserCommand, UserId, UserProfile }
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint

import java.util.UUID

class UserEndpointDescriptions(val authService: AuthService)(implicit L: LoggerFactory[IO]) {
  import ru.nh.http.json.all._
  import tapirImplicits._

  val resource: String                  = "user"
  val resourcePath: EndpointInput[Unit] = resource

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[UserEndpointDescriptions])

  type BaseEndpoint[I, O] = Endpoint[Unit, I, (StatusCode, Option[ErrorResponse]), O, Any]
  type SecuredEndpoint[I, O] =
    PartialServerEndpoint[String, AuthService.Auth, I, (StatusCode, Option[ErrorResponse]), O, Any, IO]

  def baseEndpoint: BaseEndpoint[Unit, Unit] = endpoint
    .in(resourcePath)
    .tag(resource)
    .errorOut(statusCode)
    .errorOut(jsonBody[Option[ErrorResponse]])

  def securedEndpoint: SecuredEndpoint[Unit, Unit] = baseEndpoint
    .securityIn(auth.bearer[String]())
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

  val registerUser: BaseEndpoint[RegisterUserCommand, UserId] =
    baseEndpoint.post
      .in("register")
      .description("Register user")
      .in(jsonBody[RegisterUserCommand])
      .out(jsonBody[UserId])

  val getUserProfile: SecuredEndpoint[UUID, UserProfile] =
    securedEndpoint.get
      .in("get")
      .in(path[UUID]("id"))
      .out(jsonBody[UserProfile])

}
