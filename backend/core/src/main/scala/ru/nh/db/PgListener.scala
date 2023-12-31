package ru.nh.db

import cats.effect._
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.postgres._
import fs2.Stream
import io.circe.Decoder
import org.postgresql._
import org.typelevel.log4cats.Logger
import ru.nh.db.transactors.ReadWriteTransactors

class PgListener[E: Decoder](rw: ReadWriteTransactors[IO]) {
  private def pgNotificationStream: Stream[ConnectionIO, PGNotification] =
    Stream.evalSeq(PHC.pgGetNotifications <* HC.commit)

  private def notificationStream: Stream[ConnectionIO, String] =
    pgNotificationStream
      .mapChunks(_.map(_.getParameter))

  private def decodedNotificationStream: Stream[ConnectionIO, E] =
    notificationStream.flatMap { s =>
      io.circe.parser.decode[E](s) match {
        case Left(value) =>
          println(value)
          Stream.empty
        case Right(value) =>
          Stream.emit(value)
      }
    }

  def listen(): Stream[IO, E] =
    decodedNotificationStream.transact(rw.readXA.xa)

  def listenFiltered[T](f: E => Option[T]): Stream[IO, T] =
    listen().flatMap(e => Stream.fromOption(f(e)))
}

object PgListener {
  import io.circe._
  final case class ChannelMessage[C, E](cmd: Option[C], event: Option[E])
  object ChannelMessage {
    implicit def decoder[C: Decoder, E: Decoder]: Decoder[ChannelMessage[C, E]] = (c: HCursor) =>
      for {
        cmdRow   <- c.downField("command").as[Option[C]]
        eventRow <- c.downField("event").as[Option[E]]
      } yield ChannelMessage(cmdRow, eventRow)
  }

  private[nh] def channelResource(channelName: String): Resource[ConnectionIO, Unit] =
    Resource
      .make(startListen(channelName))(_ => stopListen(channelName))

  private[nh] def startListen(channelName: String): ConnectionIO[Unit] =
    PHC.pgListen(channelName) *> HC.commit

  private[nh] def stopListen(channelName: String): ConnectionIO[Unit] =
    PHC.pgUnlisten(channelName) *> HC.commit

  def resource[E: Decoder](
      channelName: String,
      rw: ReadWriteTransactors[IO]
  )(implicit log: Logger[IO]): Resource[IO, PgListener[E]] =
    channelResource(channelName).mapK(rw.readXA.readK).as(new PgListener[E](rw))

  def channelEvents[C: Decoder, E: Decoder](
      channelName: String,
      rw: ReadWriteTransactors[IO]
  )(implicit log: Logger[IO]): Resource[IO, PgListener[ChannelMessage[C, E]]] =
    resource[ChannelMessage[C, E]](channelName, rw)
}
