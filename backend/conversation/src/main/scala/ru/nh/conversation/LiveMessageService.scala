package ru.nh.conversation

import cats.data.Chain
import cats.effect.{ IO, Ref, Resource }
import cats.syntax.all._
import ru.nh.message.MessageAccessor
import ru.nh.{ GroupMessage, MessageService, PrivateMessage }

import java.util.UUID

class LiveMessageService(ma: MessageAccessor[IO], counters: Ref[IO, Map[UUID, Long]]) extends MessageService {
  def addGroupMessage(sender: UUID, conversationId: UUID, message: String): IO[Unit] = {
    def tryAdd = counters.get.flatMap {
      _.get(conversationId).traverse { last =>
        ma.logMessageToGroup(sender, conversationId, last + 1, message).attempt.flatMap {
          _.traverseTap { _ =>
            counters.update(_.updated(conversationId, last + 1))
          }
        }
      }
    }

    def updateCounter = getPrivateMessages(conversationId).flatMap { msgs =>
      counters.update(
        _.updated(conversationId, msgs.maximumByOption(_.conversationIndex).map(_.conversationIndex).getOrElse(1))
      )
    }

    tryAdd.flatMap {
      case Some(Left(err)) if Option(err.getMessage).exists(_.contains("Duplicate key exists in unique index")) =>
        updateCounter >> tryAdd
      case None =>
        updateCounter >> tryAdd
      case others =>
        others.pure[IO]
    }.void
  }

  def addPrivateMessage(sender: UUID, to: UUID, conversationId: UUID, message: String): IO[Unit] = {
    def tryAdd = counters.get.flatMap {
      _.get(conversationId).traverse { last =>
        ma.logPrivateMessage(sender, to, conversationId, last + 1, message).attempt.flatMap {
          _.traverseTap { _ =>
            counters.update(_.updated(conversationId, last + 1))
          }
        }
      }
    }

    def updateCounter = getPrivateMessages(conversationId).flatMap { msgs =>
      counters.update(
        _.updated(conversationId, msgs.maximumByOption(_.conversationIndex).map(_.conversationIndex).getOrElse(1))
      )
    }

    tryAdd.flatMap {
      case Some(Left(err)) if Option(err.getMessage).exists(_.contains("Duplicate key exists in unique index")) =>
        updateCounter >> tryAdd
      case None =>
        updateCounter >> tryAdd
      case others =>
        others.pure[IO]
    }.void
  }

  def getGroupMessages(conversationId: UUID): IO[Chain[GroupMessage]] =
    ma.getGroupMessages(conversationId)

  def getPrivateMessages(conversationId: UUID): IO[Chain[PrivateMessage]] =
    ma.getPrivateMessages(conversationId)
}

object LiveMessageService {
  def resource(messageAccessor: MessageAccessor[IO]): Resource[IO, LiveMessageService] =
    Resource.eval(IO.ref(Map.empty[UUID, Long]).map(new LiveMessageService(messageAccessor, _)))
}
