package ru.nh.db.tarantool

import cats.data.Chain
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import io.circe.Decoder
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{ Method, Request, Uri }
import org.typelevel.log4cats.LoggerFactory
import ru.nh.conversation.ConversationAccessor
import ru.nh.db.tarantool.TarantoolHttpClient.{ LogConversationRequest, LogPrivateMessageRequest }
import ru.nh.http.ClientsSupport
import ru.nh.message.MessageAccessor
import ru.nh.{ GroupMessage, PrivateMessage }

import java.time.Instant
import java.util.UUID
import scala.util.{ Failure, Success }

class TarantoolHttpClient(
    host: String,
    port: Int,
    clientsSupport: ClientsSupport
) extends ConversationAccessor[IO]
    with MessageAccessor[IO] {

  private val baseUrl =
    Uri(Uri.Scheme.http.some, Uri.Authority(host = Uri.RegName(host), port = port.some).some)

  def logConversation(participant: UUID, privateParticipant: Option[UUID]): IO[UUID] = {
    val request = Request[IO](
      method = Method.POST,
      uri = baseUrl / "conversation"
    ).withEntity(LogConversationRequest(participant, privateParticipant.isDefined, privateParticipant))

    clientsSupport
      .runQueryRequest[String](request)
      .map(UUID.fromString)
  }

  def getPrivateConversation(firstPerson: UUID, participant: UUID): IO[Option[ConversationAccessor.ConversationRow]] = {
    val request = Request[IO](
      method = Method.GET,
      uri = baseUrl / "conversation" / firstPerson / participant
    )
    implicit val customDecoder: Decoder[ConversationAccessor.ConversationRow] =
      TarantoolHttpClient.customConversionDecoder

    clientsSupport
      .runQueryRequest[List[ConversationAccessor.ConversationRow]](request)
      .map { res =>
        res.headOption
      }
  }

  def getConversations(participant: UUID, limit: Int): IO[Chain[ConversationAccessor.ConversationRow]] = ???

  def logMessageToGroup(sender: UUID, conversationId: UUID, conversationIndex: Long, message: String): IO[Unit] = ???

  def logPrivateMessage(
      sender: UUID,
      to: UUID,
      conversationId: UUID,
      conversationIndex: Long,
      message: String
  ): IO[Unit] = {
    val request = Request[IO](
      method = Method.POST,
      uri = baseUrl / "dialogs"
    ).withEntity(LogPrivateMessageRequest(conversationId, conversationIndex, sender, to, message))

    clientsSupport
      .runRequest(request)
  }

  def getGroupMessages(conversationId: UUID): IO[Chain[GroupMessage]] = ???

  def getPrivateMessages(conversationId: UUID): IO[Chain[PrivateMessage]] = {
    val request = Request[IO](
      method = Method.GET,
      uri = baseUrl / "dialogs" / conversationId
    )

    implicit val customDecoder: Decoder[PrivateMessage] = TarantoolHttpClient.customPrivateMessageDecoder

    clientsSupport
      .runQueryRequest[List[PrivateMessage]](request)
      .map(Chain.fromSeq)
  }

}

object TarantoolHttpClient {
  import io.circe._
  import io.circe.generic.semiauto._

  final case class LogConversationRequest(
      participant: UUID,
      private_conversation: Boolean,
      private_conversation_participant: Option[UUID]
  )

  object LogConversationRequest {
    implicit val lcrDecoder: Decoder[LogConversationRequest]          = deriveDecoder[LogConversationRequest]
    implicit val lcrEncoder: Encoder.AsObject[LogConversationRequest] = deriveEncoder[LogConversationRequest]
  }

  final case class LogPrivateMessageRequest(
      conversation_id: UUID,
      conversation_index: Long,
      message_from: UUID,
      message_to: UUID,
      message: String
  )
  object LogPrivateMessageRequest {
    implicit val lpmrDecoder: Decoder[LogPrivateMessageRequest]          = deriveDecoder[LogPrivateMessageRequest]
    implicit val lpmrEncoder: Encoder.AsObject[LogPrivateMessageRequest] = deriveEncoder[LogPrivateMessageRequest]
  }

  private implicit val decodeBoolOrString: Decoder[Either[Boolean, String]] =
    Decoder[String].map(Right(_)).or(Decoder[Boolean].map(Left(_)))

  private implicit val decodeIntOrString: Decoder[Either[Int, String]] =
    Decoder[String].map(Right(_)).or(Decoder[Int].map(Left(_)))

  private[tarantool] val customConversionDecoder: Decoder[ConversationAccessor.ConversationRow] =
    Decoder
      .decodeList[Either[Boolean, String]]
      .map { res =>
        res
          .map {
            case Left(value)  => Some(value.toString)
            case Right(value) => Some(value)
          }
      }
      .emapTry {
        case List(
              Some(id),
              Some(participant),
              Some(privateConversation),
              Some(privateConversationParticipant),
              Some(createdAt)
            ) =>
          Success(
            ConversationAccessor.ConversationRow(
              UUID.fromString(id),
              UUID.fromString(participant),
              privateConversation.toBoolean,
              UUID.fromString(privateConversationParticipant).some,
              Instant.parse(createdAt)
            )
          )
        case _ =>
          Failure(new IllegalArgumentException(s"Can't decode conversion"))
      }

  private[tarantool] val customPrivateMessageDecoder: Decoder[PrivateMessage] =
    Decoder
      .decodeList[Either[Int, String]]
      .map { res =>
        res
          .map {
            case Left(value)  => Some(value.toString)
            case Right(value) => Some(value)
          }
      }
      .emapTry {
        case List(
              Some(conversationId),
              Some(conversationIndex),
              Some(sender),
              Some(recipient),
              Some(message),
              Some(createdAt)
            ) =>
          Success(
            PrivateMessage(
              UUID.fromString(conversationId),
              conversationIndex.toLong,
              UUID.fromString(sender),
              UUID.fromString(recipient),
              message,
              Instant.parse(createdAt)
            )
          )

        case _ =>
          Failure(new IllegalArgumentException(s"Can't decode conversion"))
      }

  def resource(host: String, port: Int)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, TarantoolHttpClient] =
    Resource
      .eval(L.fromClass(classOf[TarantoolHttpClient]))
      .flatMap { log =>
        ClientsSupport.createClient(log).map(c => new TarantoolHttpClient(host, port, c))
      }

}
