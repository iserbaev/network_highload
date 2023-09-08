package ru.nh.message

import cats.data.Chain
import cats.~>
import ru.nh.{ Conversation, Message }
import ru.nh.conversation.ConversationAccessor.{ ConversationAccessorMapK, ConversationRow }
import ru.nh.message.MessageAccessor.MessageAccessorMapK

import java.time.Instant
import java.util.UUID

trait MessageAccessor[F[_]] {
  def logMessage(sender: UUID, conversationId: UUID, message: String): F[Message]

  def getMessages(conversationId: UUID): F[Chain[Message]]

  def mapK[G[_]](read: F ~> G, write: F ~> G): MessageAccessor[G] =
    new MessageAccessorMapK(this, read, write)
}

object MessageAccessor {

  private[user] final class MessageAccessorMapK[F[_], G[_]](
      underlying: MessageAccessor[F],
      read: F ~> G,
      write: F ~> G
  ) extends MessageAccessor[G] {
    def logMessage(sender: UUID, conversationId: UUID, message: String): G[Message] =
      write(underlying.logMessage(sender, conversationId, message))

    def getMessages(conversationId: UUID): G[Chain[Message]] =
      read(underlying.getMessages(conversationId))
  }
}
