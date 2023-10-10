package ru.nh.conversation.db

import cats.data.Chain
import cats.effect.{ IO, Resource }
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.message.MessageAccessor
import ru.nh.{ GroupMessage, PrivateMessage }

import java.util.UUID

class PostgresMessageAccessor extends MessageAccessor[ConnectionIO] {
  import ru.nh.db.ensureUpdated

  def logMessageToGroup(
      sender: UUID,
      conversationId: UUID,
      conversationIndex: Int,
      message: String
  ): ConnectionIO[Unit] = ensureUpdated {
    sql"""INSERT INTO group_message_log(sender, conversation_id, conversation_index, message)
         |VALUES ($sender, $conversationId, $conversationIndex, $message)
         """.stripMargin.update.run
  }

  def getGroupMessages(conversationId: UUID): ConnectionIO[Chain[GroupMessage]] =
    sql"""SELECT conversation_id, conversation_index, sender, message, created_at
         |FROM group_message_log
         |WHERE conversation_id = $conversationId
         """.stripMargin
      .query[GroupMessage]
      .to[List]
      .map(Chain.fromSeq)

  def logPrivateMessage(
      sender: UUID,
      to: UUID,
      conversationId: UUID,
      conversationIndex: Int,
      message: String
  ): ConnectionIO[Unit] =
    ensureUpdated {
      sql"""INSERT INTO private_message_log(message_from, message_to, conversation_id, conversation_index, message)
           |VALUES ($sender, $to, $conversationId, $conversationIndex, $message)
           """.stripMargin.update.run
    }

  def getPrivateMessages(conversationId: UUID): ConnectionIO[Chain[PrivateMessage]] =
    sql"""SELECT conversation_id, conversation_index, message_from, message_to, message, created_at
         |FROM private_message_log
         |WHERE conversation_id = $conversationId
         """.stripMargin
      .query[PrivateMessage]
      .to[List]
      .map(Chain.fromSeq)
}

object PostgresMessageAccessor {
  import ru.nh.db.transactors._
  import ru.nh.db.transactors.syntax._

  def resource: Resource[IO, PostgresMessageAccessor] = Resource.eval {
    IO {
      new PostgresMessageAccessor
    }
  }

  def inIO(rw: ReadWriteTransactors[IO])(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, MessageAccessor[IO]] = Resource.suspend {
    L.fromClass(classOf[PostgresMessageAccessor]).map { implicit log =>
      resource.map(_.messageMapK(rw.readK, rw.writeK))
    }
  }
}
