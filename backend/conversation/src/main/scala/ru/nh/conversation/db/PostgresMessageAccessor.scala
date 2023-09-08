package ru.nh.conversation.db

import cats.data.Chain
import cats.effect.{ IO, Resource }
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.Message
import ru.nh.message.MessageAccessor

import java.util.UUID

class PostgresMessageAccessor extends MessageAccessor[ConnectionIO] {
  import ru.nh.db.ensureUpdated

  def logMessage(sender: UUID, conversationId: UUID, message: String): ConnectionIO[Unit] = ensureUpdated {
    sql"""INSERT INTO message_log(sender, conversation_id, message)
         |VALUES ($sender, $conversationId, $message)
         """.stripMargin.update.run
  }

  def getMessages(conversationId: UUID): ConnectionIO[Chain[Message]] =
    sql"""SELECT sender, conversation_id, conversation_index, message, created_at
         |FROM message_log
         |WHERE conversation_id = $conversationId
         """.stripMargin
      .query[Message]
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
      resource.map(_.mapK(rw.readK, rw.writeK))
    }
  }
}
