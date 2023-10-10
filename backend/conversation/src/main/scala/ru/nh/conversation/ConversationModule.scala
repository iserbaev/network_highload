package ru.nh.conversation

import cats.data.{ Chain, NonEmptyList }
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.auth.AuthService
import ru.nh.conversation.db.{ PostgresConversationAccessor, PostgresMessageAccessor }
import ru.nh.conversation.http.ConversationEndpoints
import ru.nh.db.PostgresModule
import ru.nh.db.tarantool.TarantoolModule
import ru.nh.http.SEndpoint
import ru.nh.message.MessageAccessor
import ru.nh.{ Conversation, ConversationService, MessageService }

import java.util.UUID

trait ConversationModule {
  def service: ConversationService

  def messageService: MessageService

  def endpoints: NonEmptyList[SEndpoint]
}

object ConversationModule {
  def postgres(postgresModule: PostgresModule, authService: AuthService)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, ConversationModule] =
    (PostgresConversationAccessor.inIO(postgresModule.rw), PostgresMessageAccessor.inIO(postgresModule.rw)).flatMapN {
      (c, m) =>
        build(c, m, authService)
    }

  def tarantool(tarantoolModule: TarantoolModule, authService: AuthService)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, ConversationModule] =
    build(tarantoolModule.client, tarantoolModule.client, authService)

  private def build(c: ConversationAccessor[IO], m: MessageAccessor[IO], authService: AuthService)(
      implicit L: LoggerFactory[IO]
  ) =
    LiveMessageService.resource(m).map { ms =>
      new ConversationModule {
        val service: ConversationService = new ConversationService {
          def createConversation(participant: UUID, privateParticipant: Option[UUID]): IO[UUID] =
            c.logConversation(participant, privateParticipant)

          def getPrivateConversation(firstPerson: UUID, participant: UUID): IO[Option[Conversation]] =
            c.getPrivateConversation(firstPerson, participant)
              .map(_.map(_.toConversation))

          def getConversations(participant: UUID, limit: Int): IO[Chain[Conversation]] =
            c.getConversations(participant, limit).map(_.map(_.toConversation))
        }

        val messageService: MessageService = ms

        def endpoints: NonEmptyList[SEndpoint] =
          new ConversationEndpoints(authService, service, messageService).all
      }
    }

}
