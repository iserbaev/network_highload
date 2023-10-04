package ru.nh.conversation

import cats.data.{ Chain, NonEmptyList }
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.auth.AuthService
import ru.nh.conversation.db.{ PostgresConversationAccessor, PostgresMessageAccessor }
import ru.nh.conversation.http.ConversationEndpoints
import ru.nh.db.PostgresModule
import ru.nh.http.SEndpoint
import ru.nh.{ Conversation, ConversationService, GroupMessage, MessageService, PrivateMessage }

import java.util.UUID

trait ConversationModule {
  def service: ConversationService

  def messageService: MessageService

  def endpoints: NonEmptyList[SEndpoint]
}

object ConversationModule {
  def resource(postgresModule: PostgresModule, authService: AuthService)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, ConversationModule] =
    (PostgresConversationAccessor.inIO(postgresModule.rw), PostgresMessageAccessor.inIO(postgresModule.rw)).mapN {
      (c, m) =>
        new ConversationModule {
          val service: ConversationService = new ConversationService {
            def createConversation(participant: UUID, privateParticipant: Option[UUID]): IO[UUID] =
              c.logConversation(participant, privateParticipant)

            def addParticipant(conversationId: UUID, participant: UUID): IO[Unit] =
              c.addParticipant(conversationId, participant)

            def getPrivateConversation(firstPerson: UUID, participant: UUID): IO[Option[Conversation]] =
              c.getPrivateConversation(firstPerson, participant)
                .map(_.map(_.toConversation))

            def getConversations(participant: UUID, limit: Int): IO[Chain[Conversation]] =
              c.getConversations(participant, limit).map(_.map(_.toConversation))
          }

          val messageService: MessageService = new MessageService {
            def addGroupMessage(sender: UUID, conversationId: UUID, message: String): IO[Unit] =
              m.logMessageToGroup(sender, conversationId, message)

            def getGroupMessages(conversationId: UUID): IO[Chain[GroupMessage]] =
              m.getGroupMessages(conversationId)

            def addPrivateMessage(sender: UUID, to: UUID, conversationId: UUID, message: String): IO[Unit] =
              m.logPrivateMessage(sender, to, conversationId, message)

            def getPrivateMessages(conversationId: UUID): IO[Chain[PrivateMessage]] =
              m.getPrivateMessages(conversationId)
          }

          def endpoints: NonEmptyList[SEndpoint] =
            new ConversationEndpoints(authService, service, messageService).all
        }
    }
}
