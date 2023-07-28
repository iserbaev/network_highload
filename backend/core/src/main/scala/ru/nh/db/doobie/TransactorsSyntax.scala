package ru.nh.db.doobie

import cats.effect.Temporal
import doobie.ConnectionIO
import org.typelevel.log4cats.Logger
import ru.nh.db.doobie.DoobieSupport.ReadWriteTransactors

final class TransactorsSyntax[F[_]](private val tx: ReadWriteTransactors[F]) extends AnyVal {
  def read[A](cio: ConnectionIO[A])(implicit logger: Logger[F], F: Temporal[F]): F[A] =
    tx.read.read(cio)

  def write[A](cio: ConnectionIO[A])(implicit logger: Logger[F], F: Temporal[F]): F[A] =
    tx.write.write(cio)
}
