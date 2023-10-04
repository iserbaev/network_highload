package ru.nh

import cats.data.Chain
import cats.effect.IO

import java.util.UUID

trait MessageService {

  def addGroupMessage(sender: UUID, conversationId: UUID, message: String): IO[Unit]
  def addPrivateMessage(sender: UUID, to: UUID, conversationId: UUID, message: String): IO[Unit]

  def getGroupMessages(conversationId: UUID): IO[Chain[GroupMessage]]

  def getPrivateMessages(conversationId: UUID): IO[Chain[PrivateMessage]]

}
