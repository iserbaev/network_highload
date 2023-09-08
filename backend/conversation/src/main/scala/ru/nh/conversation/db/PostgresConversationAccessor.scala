package ru.nh.conversation.db

import cats.data.Chain
import cats.effect.{ IO, Resource }
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.conversation.ConversationAccessor
import ru.nh.conversation.ConversationAccessor.ConversationRow

import java.util.UUID

class PostgresConversationAccessor extends ConversationAccessor[ConnectionIO] {
  import ru.nh.db.ensureUpdated

  def logConversation(participant: UUID, privateParticipant: Option[UUID]): ConnectionIO[UUID] = {
    val privateConversation = privateParticipant.nonEmpty

    sql"""INSERT INTO conversation_log(participant, private_conversation, private_conversation_participant)
         |VALUES ($participant, $privateConversation, $privateParticipant)
         |RETURNING id
         """.stripMargin.update
      .withGeneratedKeys[UUID]("id")
      .compile
      .lastOrError
  }

  def addParticipant(conversationId: UUID, participant: UUID): ConnectionIO[Unit] = ensureUpdated {
    sql"""INSERT INTO conversation_log(id, participant, private_conversation)
         |VALUES ($conversationId, $participant, false)
           """.stripMargin.update.run
  }

  def getPrivateConversation(firstPerson: UUID, participant: UUID): ConnectionIO[Option[ConversationRow]] =
    sql"""SELECT id, participant, private_conversation, private_conversation_participant, created_at
         |FROM conversation_log
         |WHERE participant = $firstPerson
         |  AND private_conversation_participant = $participant
         """.stripMargin
      .query[ConversationRow]
      .option

  def getConversations(participant: UUID, limit: Int): ConnectionIO[Chain[ConversationRow]] =
    sql"""SELECT id, participant, private_conversation, private_conversation_participant, created_at
         |FROM conversation_log
         |WHERE participant = $participant
         |LIMIT $limit""".stripMargin
      .query[ConversationRow]
      .to[List]
      .map(Chain.fromSeq)
}

object PostgresConversationAccessor {
  import ru.nh.db.transactors._
  import ru.nh.db.transactors.syntax._
  def resource: Resource[IO, PostgresConversationAccessor] = Resource.eval {
    IO {
      new PostgresConversationAccessor
    }
  }

  def inIO(rw: ReadWriteTransactors[IO])(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, ConversationAccessor[IO]] = Resource.suspend {
    L.fromClass(classOf[PostgresConversationAccessor]).map { implicit log =>
      resource.map(_.mapK(rw.readK, rw.writeK))
    }
  }
}
