package ru.nh.user

import cats.data.Chain

import java.util.UUID

trait ConversationService[F[_]] {

  def createConversation(participant: UUID, privateParticipant: Option[UUID]): F[Conversation]

  def addParticipant(conversationId: UUID, participant: UUID): F[Unit]

  def getPrivateConversation(firstPerson: UUID, participant: UUID): F[Conversation]

  def getConversations(participant: UUID, limit: Int): F[Chain[Conversation]]

}
