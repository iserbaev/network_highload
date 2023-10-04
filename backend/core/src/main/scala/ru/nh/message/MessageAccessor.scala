package ru.nh.message

import cats.data.Chain
import cats.~>
import ru.nh.message.MessageAccessor.MessageAccessorMapK
import ru.nh.{ GroupMessage, PrivateMessage }

import java.util.UUID

trait MessageAccessor[F[_]] {
  def logMessageToGroup(sender: UUID, conversationId: UUID, message: String): F[Unit]

  def logPrivateMessage(sender: UUID, to: UUID, conversationId: UUID, message: String): F[Unit]

  def getGroupMessages(conversationId: UUID): F[Chain[GroupMessage]]

  def getPrivateMessages(conversationId: UUID): F[Chain[PrivateMessage]]

  def mapK[G[_]](read: F ~> G, write: F ~> G): MessageAccessor[G] =
    new MessageAccessorMapK(this, read, write)
}

object MessageAccessor {

  private[message] final class MessageAccessorMapK[F[_], G[_]](
      underlying: MessageAccessor[F],
      read: F ~> G,
      write: F ~> G
  ) extends MessageAccessor[G] {
    def logMessageToGroup(sender: UUID, conversationId: UUID, message: String): G[Unit] =
      write(underlying.logMessageToGroup(sender, conversationId, message))

    def logPrivateMessage(sender: UUID, to: UUID, conversationId: UUID, message: String): G[Unit] =
      write(underlying.logPrivateMessage(sender, to, conversationId, message))

    def getGroupMessages(conversationId: UUID): G[Chain[GroupMessage]] =
      read(underlying.getGroupMessages(conversationId))

    def getPrivateMessages(conversationId: UUID): G[Chain[PrivateMessage]] =
      read(underlying.getPrivateMessages(conversationId))
  }
}
