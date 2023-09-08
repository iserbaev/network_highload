package ru.nh.conversation

import cats.data.Chain
import cats.~>
import ru.nh.Conversation
import ru.nh.conversation.ConversationAccessor.{ ConversationAccessorMapK, ConversationRow }

import java.time.Instant
import java.util.UUID

trait ConversationAccessor[F[_]] {
  def logConversation(participant: UUID, privateParticipant: Option[UUID]): F[ConversationRow]

  def addParticipant(conversationId: UUID, participant: UUID): F[Unit]

  def getPrivateConversation(firstPerson: UUID, participant: UUID): F[ConversationRow]

  def getConversations(participant: UUID, limit: Int): F[Chain[ConversationRow]]
  def mapK[G[_]](read: F ~> G, write: F ~> G): ConversationAccessor[G] =
    new ConversationAccessorMapK(this, read, write)
}

object ConversationAccessor {
  final case class ConversationRow(
      id: UUID,
      participant: UUID,
      privateConversation: Boolean,
      privateConversationParticipant: Option[UUID],
      createdAt: Instant
  ) {
    def toConversation: Conversation =
      new Conversation(id, participant, privateConversation, privateConversationParticipant)
  }

  private[user] final class ConversationAccessorMapK[F[_], G[_]](
      underlying: ConversationAccessor[F],
      read: F ~> G,
      write: F ~> G
  ) extends ConversationAccessor[G] {
    def logConversation(participant: UUID, privateParticipant: Option[UUID]): G[ConversationRow] =
      write(underlying.logConversation(participant, privateParticipant))

    def addParticipant(conversationId: UUID, participant: UUID): G[Unit] =
      write(underlying.addParticipant(conversationId, participant))

    def getPrivateConversation(firstPerson: UUID, participant: UUID): G[ConversationRow] =
      read(underlying.getPrivateConversation(firstPerson, participant))

    def getConversations(participant: UUID, limit: Int): G[Chain[ConversationRow]] =
      read(underlying.getConversations(participant, limit))
  }
}
