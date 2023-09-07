package ru.nh.user

import cats.data.Chain
import cats.effect.IO

import java.util.UUID

trait MessageService {

  def addMessage(sender: UUID, conversationId: UUID, message: String): IO[Message]

  def getMessages(conversationId: UUID): IO[Chain[Message]]

}
