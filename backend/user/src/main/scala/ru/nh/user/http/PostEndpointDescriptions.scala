package ru.nh.user.http

import cats.effect.IO
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.http._
import ru.nh.{ Id, Post }
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import java.nio.charset.StandardCharsets
import java.util.UUID

class PostEndpointDescriptions(val authService: AuthService)(implicit L: LoggerFactory[IO]) {
  import PostEndpointDescriptions.{ PostCreate, PostUpdate }
  import ru.nh.http.json.all._
  import tapirImplicits._

  val resource: String                  = "user"
  val resourcePath: EndpointInput[Unit] = resource

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[UserEndpointDescriptions])

  implicit val postCreateSchema: Schema[PostCreate] = Schema.derived[PostCreate]
  implicit val postUpdateSchema: Schema[PostUpdate] = Schema.derived[PostUpdate]

  private val secured = securedEndpoint(resource, resourcePath, authService)

  val addPost: SecuredEndpoint[PostCreate, Id] =
    secured.post
      .in("post" / "create")
      .in(jsonBody[PostCreate])
      .out(jsonBody[Id])

  val updatePost: SecuredEndpoint[PostUpdate, StatusCode] =
    secured.put
      .in("post" / "update")
      .in(jsonBody[PostUpdate])
      .out(statusCode.description(StatusCode.Ok, "Успешно изменен пост"))

  val deletePost: SecuredEndpoint[UUID, StatusCode] =
    secured.put
      .in("post" / "delete")
      .in(path[UUID]("id"))
      .out(statusCode.description(StatusCode.Ok, "Успешно удален пост"))

  val getPost: SecuredEndpoint[UUID, Post] =
    secured.get
      .in("post" / "get")
      .in(path[UUID]("id"))
      .out(jsonBody[Post])

  val postFeed =
    secured.get
      .in("post" / "feed")
      .in(path[Int]("offset").and(path[Int]("limit")))
      .out(NoCacheControlHeader)
      .out(XAccelBufferingHeader)
      .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

}

object PostEndpointDescriptions {
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
