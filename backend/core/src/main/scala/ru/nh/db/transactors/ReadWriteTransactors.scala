package ru.nh.db.transactors

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import org.typelevel.log4cats.Logger

final class ReadWriteTransactors[F[_]] private (
    val readXA: DoobieTransactor[F],
    val writeXA: DoobieTransactor[F]
)

object ReadWriteTransactors {
  def build(
      read: TransactorSettings,
      write: TransactorSettings,
      poolNamePrefix: String
  )(
      implicit logger: Logger[IO]
  ): Resource[IO, ReadWriteTransactors[IO]] =
    (
      DoobieTransactor.build(read, poolNamePrefix ++ "ReadPool", readOnly = true),
      DoobieTransactor.build(write, poolNamePrefix ++ "WritePool", readOnly = false)
    ).mapN((readXa, writeXa) => new ReadWriteTransactors(readXa, writeXa))

  def buildMetered(
      read: TransactorSettings,
      write: TransactorSettings,
      poolNamePrefix: String,
      metricsTrackerFactory: MetricsTrackerFactory
  )(
      implicit logger: Logger[IO]
  ): Resource[IO, ReadWriteTransactors[IO]] =
    (
      DoobieTransactor.buildMetered(
        read,
        poolNamePrefix ++ "ReadPool",
        metricsTrackerFactory,
        readOnly = true
      ),
      DoobieTransactor.buildMetered(
        write,
        poolNamePrefix ++ "WritePool",
        metricsTrackerFactory,
        readOnly = false
      )
    ).mapN((readXa, writeXa) => new ReadWriteTransactors(readXa, writeXa))
}
