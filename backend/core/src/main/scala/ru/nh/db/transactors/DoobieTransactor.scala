package ru.nh.db.transactors

import cats.arrow.FunctionK
import cats.effect.{ IO, Resource, Temporal }
import cats.syntax.all._
import cats.~>
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import doobie._
import org.typelevel.log4cats.Logger
import ru.nh.db.jdbc.JdbcSupport

final class DoobieTransactor[F[_]] private[transactors] (
    val xa: Transactor[F],
    retryConfig: TransactionRetryConfig,
    readOnly: Boolean
) {
  import RetrySupport._

  def read[A](cio: ConnectionIO[A])(implicit logger: Logger[F], F: Temporal[F]): F[A] =
    retryConnection(cio)(
      xa,
      retryConfig.retryCount,
      retryConfig.baseInterval
    )(defaultTransactionRetryCondition)

  def write[A](cio: ConnectionIO[A])(implicit logger: Logger[F], F: Temporal[F]): F[A] =
    F.raiseError(new IllegalStateException("Write not allowed in read-only transaction")).whenA(readOnly) *>
      retryConnection(cio)(
        xa,
        retryConfig.retryCount,
        retryConfig.baseInterval
      )(defaultTransactionRetryCondition)

  def readK(implicit logger: Logger[F], F: Temporal[F]): ConnectionIO ~> F =
    new FunctionK[ConnectionIO, F] {
      def apply[A](fa: ConnectionIO[A]): F[A] =
        read(fa)
    }

  def writeK(implicit logger: Logger[F], F: Temporal[F]): ConnectionIO ~> F =
    new FunctionK[ConnectionIO, F] {
      def apply[A](fa: ConnectionIO[A]): F[A] =
        write(fa)
    }
}

object DoobieTransactor {
  def buildMetered(
      db: TransactorSettings,
      poolName: String,
      metricsTrackerFactory: MetricsTrackerFactory,
      readOnly: Boolean
  )(
      implicit logger: Logger[IO]
  ): Resource[IO, DoobieTransactor[IO]] = {
    val withMetrics  = JdbcSupport.withMetrics(metricsTrackerFactory)
    val withReadOnly = JdbcSupport.withReadOnly(readOnly)

    JdbcSupport
      .buildHikariTransactor(db.connection, db.pool, poolName, logger) {
        withMetrics.andThen(withReadOnly)
      }
      .map(new DoobieTransactor(_, db.transactionRetry, readOnly))
  }

  def build(db: TransactorSettings, poolName: String, readOnly: Boolean)(
      implicit logger: Logger[IO]
  ): Resource[IO, DoobieTransactor[IO]] = {
    val withReadOnly = JdbcSupport.withReadOnly(readOnly)

    JdbcSupport
      .buildHikariTransactor(db.connection, db.pool, poolName, logger)(withReadOnly)
      .map(new DoobieTransactor(_, db.transactionRetry, readOnly))
  }
}
