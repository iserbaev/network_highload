package ru.nh.db

import cats.effect._
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.postgres._
import fs2.Stream
import org.postgresql._
import org.typelevel.log4cats.Logger
import ru.nh.db.transactors.ReadWriteTransactors

class PgListener private (channelName: String) {
  import PgListener._

  /** A resource that listens on a channel and unlistens when we're done. */
  def channel: Resource[ConnectionIO, Unit] =
    Resource.make(startListen)(_ => stopListen)

  def startListen: ConnectionIO[Unit] =
    PHC.pgListen(channelName) *> HC.commit

  def stopListen: ConnectionIO[Unit] =
    PHC.pgUnlisten(channelName) *> HC.commit

  /** Stream of PGNotifications on the specified channel, for polling at the specified
    * rate. Note that this stream, when run, will commit the current transaction.
    */
  def pgNotificationStream: Stream[ConnectionIO, PGNotification] =
    Stream.evalSeq(PHC.pgGetNotifications <* HC.commit)

  def notificationStream: Stream[ConnectionIO, String] =
    pgNotificationStream
      .mapChunks(_.map(_.getParameter))

  def decodedNotificationStream: Stream[ConnectionIO, ChannelMessage] =
    notificationStream.flatMap { s =>
      Stream.fromEither[ConnectionIO](io.circe.parser.decode[ChannelMessage](s))
    }
}

object PgListener {
  import io.circe._
  final case class ChannelMessage(cmd: Option[String], event: Option[String])
  object ChannelMessage {
    implicit val decoder: Decoder[ChannelMessage] = (c: HCursor) =>
      for {
        cmdRow   <- c.downField("command").as[Option[String]]
        eventRow <- c.downField("event").as[Option[String]]
      } yield ChannelMessage(cmdRow, eventRow)
  }
  def apply(channelName: String): PgListener =
    new PgListener(channelName)

  def channelResource(channelName: String, rw: ReadWriteTransactors[IO])(
      implicit logger: Logger[IO]
  ): Resource[IO, Unit] =
    PgListener(channelName).channel.mapK[IO](rw.readXA.readK)
}
