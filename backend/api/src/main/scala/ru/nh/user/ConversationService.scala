package ru.nh.user

import cats.data.Chain
import cats.effect.IO

import java.util.UUID

trait ConversationService {

  def createConversation(participant: UUID, privateParticipant: Option[UUID]): IO[Conversation]

  def addParticipant(conversationId: UUID, participant: UUID): IO[Unit]

  def getPrivateConversation(firstPerson: UUID, participant: UUID): IO[Conversation]

  def getConversations(participant: UUID, limit: Int): IO[Chain[Conversation]]

}
