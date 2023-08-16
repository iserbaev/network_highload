package ru.nh.user.http

import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.http.ErrorResponse
import ru.nh.user.{ RegisterUserCommand, User, UserId }
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

  val getUserProfile: SecuredEndpoint[UUID, User] =
    securedEndpoint.get
      .in("get")
      .in(path[UUID]("id"))
      .out(jsonBody[User])

  val searchUserProfile: SecuredEndpoint[(String, String), User] =
    securedEndpoint.get
      .in("search")
      .in(query[String]("first_name").and(query[String]("last_name")))
      .out(jsonBody[User])

  val addFriend: SecuredEndpoint[UUID, StatusCode] =
    securedEndpoint.put
      .in("friend" / "set")
      .in(path[UUID]("user_id"))
      .out(
        statusCode
          .description(StatusCode.Ok, "Пользователь успешно указал своего друга")
      )

  val deleteFriend: SecuredEndpoint[UUID, StatusCode] =
    securedEndpoint.put
      .in("friend" / "delete")
      .in(path[UUID]("user_id"))
      .out(statusCode.description(StatusCode.Ok, "Пользователь успешно удалил из друзей пользователя"))
}
