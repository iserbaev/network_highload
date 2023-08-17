package ru.nh.user.http

import cats.effect.IO
import cats.syntax.all._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.typelevel.log4cats.{Logger, LoggerFactory}
import ru.nh.auth.AuthService
import ru.nh.http.ErrorResponse
import ru.nh.user.http.UserEndpointDescriptions.{PostCreate, PostUpdate}
import ru.nh.user.{Id, Post, RegisterUserCommand, User}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint

import java.nio.charset.StandardCharsets
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

  val registerUser: BaseEndpoint[RegisterUserCommand, Id] =
    baseEndpoint.post
      .in("register")
      .description("Register user")
      .in(jsonBody[RegisterUserCommand])
      .out(jsonBody[Id])

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

  val addPost: SecuredEndpoint[PostCreate, Id] =
    securedEndpoint.post
      .in("post" / "create")
      .in(jsonBody[PostCreate])
      .out(jsonBody[Id])

  val updatePost: SecuredEndpoint[PostUpdate, StatusCode] =
    securedEndpoint.put
      .in("post" / "update")
      .in(jsonBody[PostUpdate])
      .out(statusCode.description(StatusCode.Ok, "Успешно изменен пост"))

  val deletePost: SecuredEndpoint[UUID, StatusCode] =
    securedEndpoint.put
      .in("post" / "delete")
      .in(path[UUID]("id"))
      .out(statusCode.description(StatusCode.Ok, "Успешно удален пост"))

  val getPost: SecuredEndpoint[UUID, Post] =
    securedEndpoint.get
      .in("post" / "get")
      .in(path[UUID]("id"))
      .out(jsonBody[Post])

  val postFeed =
    securedEndpoint.get
      .in("post" / "feed")
      .in(path[Int]("offset").and(path[Int]("limit")))
//      .out(NoCacheControlHeader)
//      .out(XAccelBufferingHeader)
      .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

}

object UserEndpointDescriptions {
  final case class PostCreate(text: String)
  object PostCreate {
    implicit val decoder: Decoder[PostCreate]          = deriveDecoder[PostCreate]
    implicit val encoder: Encoder.AsObject[PostCreate] = deriveEncoder[PostCreate]
  }

  final case class PostUpdate(id: UUID, text: String)
  object PostUpdate {
    implicit val decoder: Decoder[PostUpdate]          = deriveDecoder[PostUpdate]
    implicit val encoder: Encoder.AsObject[PostUpdate] = deriveEncoder[PostUpdate]
  }
}
