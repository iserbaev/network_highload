package ru.nh.conversation.http

import cats.effect.IO
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.PrivateMessage
import ru.nh.auth.AuthService
import ru.nh.conversation.http.ConversationEndpointDescriptions._
import ru.nh.http._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class ConversationEndpointDescriptions(val authService: AuthService)(implicit L: LoggerFactory[IO]) {
  import ru.nh.http.json.all._

  val resource: String                  = "dialog"
  val resourcePath: EndpointInput[Unit] = resource

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[ConversationEndpointDescriptions])

  implicit val dialogMessageTextSchema: Schema[DialogMessageText] = Schema.derived[DialogMessageText]
  implicit val dialogMessageSchema: Schema[PrivateMessage]        = Schema.derived[PrivateMessage]
  private def secured                                             = securedEndpoint(resource, resourcePath, authService)

  val addDialog: SecuredEndpoint[(UUID, DialogMessageText), StatusCode] =
    secured.post
      .in(path[UUID]("user_id"))
      .in("send")
      .in(jsonBody[DialogMessageText])
      .out(statusCode)

  val listDialog: SecuredEndpoint[UUID, List[PrivateMessage]] =
    secured.post
      .in(path[UUID]("user_id"))
      .in("list")
      .out(jsonBody[List[PrivateMessage]])

}

object ConversationEndpointDescriptions {
  final case class DialogMessageText(text: String)

  object DialogMessageText {
    implicit val decoder: Decoder[DialogMessageText]          = deriveDecoder[DialogMessageText]
    implicit val encoder: Encoder.AsObject[DialogMessageText] = deriveEncoder[DialogMessageText]
  }
}
