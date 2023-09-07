package ru.nh.user

import cats.data.Chain

import java.util.UUID

trait MessageService[F[_]] {

  def addMessage(sender: UUID, conversationId: UUID, message: String): F[Message]

  def getMessages(conversationId: UUID): F[Chain[Message]]

}
