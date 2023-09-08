package ru.nh.conversation.http

import cats.data.{ NonEmptyList, NonEmptySet }
import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.conversation.http.ConversationEndpointDescriptions.DialogMessage
import ru.nh.http._
import ru.nh.{ ConversationService, MessageService }
import sttp.model.StatusCode

import java.util.UUID

class ConversationEndpoints(
    authService: AuthService,
    conversationService: ConversationService,
    messageService: MessageService
)(
    implicit L: LoggerFactory[IO]
) {

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[ConversationEndpoints])

  private val endpointDescriptions = new ConversationEndpointDescriptions(authService)

  val addDialog: SEndpoint = endpointDescriptions.addDialog
    .serverLogic { auth => params =>
      conversationService
        .getPrivateConversation(UUID.fromString(auth.userId), params._1)
        .flatMap {
          case Some(value) => value.id.pure[IO]
          case None =>
            conversationService
              .createConversation(UUID.fromString(auth.userId), params._1.some)
        }
        .flatMap(conversationId =>
          messageService
            .addMessage(UUID.fromString(auth.userId), conversationId, params._2.text)
            .as(conversationId)
        )
        .attempt
        .flatTap(e => log.debug(s"Create conversation result $e"))
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.as(StatusCode.Ok)
        }
    }

  val listDialog: SEndpoint = endpointDescriptions.listDialog
    .serverLogic { auth => id =>
      conversationService
        .getPrivateConversation(UUID.fromString(auth.userId), id)
        .flatMap(
          IO.fromOption(_)(
            new NoSuchElementException(s"Conversation not found for (${UUID.fromString(auth.userId)}, $id)")
          )
        )
        .flatMap(c => messageService.getMessages(c.id).tupleLeft(c))
        .flatTap { case (conversation, _) =>
          log.debug(s"Received private conversation for (${auth.userId}, $id}) [$conversation]")
        }
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap { case (_, messages) =>
            val participants = NonEmptySet.of(UUID.fromString(auth.userId), id)

            messages.map(m => DialogMessage(m, participants)).toList.asRight
          }
        }
    }

  val all: NonEmptyList[SEndpoint] = NonEmptyList.of(
    addDialog,
    listDialog
  )
}
