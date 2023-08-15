package ru.nh.db.transactors

import cats.effect.{ MonadCancelThrow, Temporal }
import cats.~>
import doobie.ConnectionIO
import doobie.implicits._
import org.typelevel.log4cats.Logger

object syntax {
  implicit def readWriteTransactorsSyntax[F[_]](rw: ReadWriteTransactors[F]): ReadWriteTransactorsOps[F] =
    new ReadWriteTransactorsOps[F](rw)

  implicit def doobieTransactorSyntax[F[_]](tr: DoobieTransactor[F]): DoobieTransactorOps[F] =
    new DoobieTransactorOps[F](tr)
}

final class ReadWriteTransactorsOps[F[_]](private val tx: ReadWriteTransactors[F]) extends AnyVal {
  def read[A](cio: ConnectionIO[A])(implicit logger: Logger[F], F: Temporal[F]): F[A] =
    tx.readXA.read(cio)

  def write[A](cio: ConnectionIO[A])(implicit logger: Logger[F], F: Temporal[F]): F[A] =
    tx.writeXA.write(cio)

  def readK(implicit logger: Logger[F], F: Temporal[F]): ConnectionIO ~> F =
    tx.readXA.readK

  def writeK(implicit logger: Logger[F], F: Temporal[F]): ConnectionIO ~> F =
    tx.writeXA.writeK
}

final class DoobieTransactorOps[F[_]](private val tr: DoobieTransactor[F]) extends AnyVal {
  def tx[A](cio: ConnectionIO[A])(implicit F: MonadCancelThrow[F]): F[A] =
    cio.transact(tr.xa)

  def transact[A](cio: ConnectionIO[A])(implicit F: MonadCancelThrow[F]): F[A] =
    cio.transact(tr.xa)
}
