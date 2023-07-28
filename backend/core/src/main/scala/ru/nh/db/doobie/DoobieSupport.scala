package ru.nh.db.doobie

import cats.arrow.FunctionK
import cats.effect.{ IO, Resource, Temporal }
import cats.syntax.all._
import cats.~>
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import doobie._
import org.typelevel.log4cats.Logger
import ru.nh.db.jdbc.JdbcSupport
import ru.nh.db.jdbc.JdbcSupport.PoolConfig

import scala.annotation.unused
import scala.concurrent.duration.FiniteDuration

object DoobieSupport extends RetrySupport {
  final case class TransactorSettings private (
      connection: JdbcSupport.ConnectionConfig,
      pool: JdbcSupport.PoolConfig,
      transactionRetry: TransactionRetryConfig
  ) {
    def withConnection(connection: JdbcSupport.ConnectionConfig): TransactorSettings = copy(connection = connection)
    def withPool(pool: PoolConfig): TransactorSettings                               = copy(pool = pool)
    def withTransactionRetry(transactionRetry: TransactionRetryConfig): TransactorSettings =
      copy(transactionRetry = transactionRetry)

  }

  object TransactorSettings {
    def apply(
        connection: JdbcSupport.ConnectionConfig,
        pool: JdbcSupport.PoolConfig,
        transactionRetry: TransactionRetryConfig
    ): TransactorSettings =
      new TransactorSettings(connection, pool, transactionRetry)

    @unused
    private def unapply(c: TransactorSettings): TransactorSettings = c
  }

  final case class TransactionRetryConfig private (retryCount: Int, baseInterval: FiniteDuration) {
    def withRetryCount(retryCount: Int): TransactionRetryConfig                = copy(retryCount = retryCount)
    def withBaseInterval(baseInterval: FiniteDuration): TransactionRetryConfig = copy(baseInterval = baseInterval)
  }
  object TransactionRetryConfig {
    def apply(retryCount: Int, baseInterval: FiniteDuration): TransactionRetryConfig =
      new TransactionRetryConfig(retryCount, baseInterval)

    @unused
    private def unapply(c: TransactionRetryConfig): TransactionRetryConfig = c
  }

  final case class DoobieTransactor[F[_]](xa: Transactor[F], retryConfig: TransactionRetryConfig, readOnly: Boolean) {
    def read[A](cio: ConnectionIO[A])(implicit logger: Logger[F], F: Temporal[F]): F[A] =
      retryConnection(cio)(
        xa,
        retryConfig.retryCount,
        retryConfig.baseInterval
      )(defaultTransactionRetryCondition)

    def write[A](cio: ConnectionIO[A])(implicit logger: Logger[F], F: Temporal[F]): F[A] =
      F.raiseError(new NotImplementedError()).whenA(readOnly) *>
        retryConnection(cio)(
          xa,
          retryConfig.retryCount,
          retryConfig.baseInterval
        )(defaultTransactionRetryCondition)

    def readTx(implicit logger: Logger[F], F: Temporal[F]): ConnectionIO ~> F =
      new FunctionK[ConnectionIO, F] {
        def apply[A](fa: ConnectionIO[A]): F[A] =
          read(fa)
      }

    def writeTx(implicit logger: Logger[F], F: Temporal[F]): ConnectionIO ~> F =
      new FunctionK[ConnectionIO, F] {
        def apply[A](fa: ConnectionIO[A]): F[A] =
          write(fa)
      }

  }

  final case class ReadWriteTransactors[F[_]](
      read: DoobieTransactor[F],
      write: DoobieTransactor[F]
  )

  def buildMeteredTransactor(
      db: TransactorSettings,
      poolName: String,
      metricsTrackerFactory: MetricsTrackerFactory,
      readOnly: Boolean
  )(
      implicit logger: Logger[IO]
  ): Resource[IO, DoobieTransactor[IO]] =
    JdbcSupport
      .buildHikariTransactor(db.connection, db.pool, poolName, logger) { conf =>
        JdbcSupport.withMetrics(metricsTrackerFactory)(conf): Unit
        conf.setReadOnly(readOnly)
        conf
      }
      .map(DoobieTransactor(_, db.transactionRetry, readOnly))

  def buildTransactor(db: TransactorSettings, poolName: String, readOnly: Boolean)(
      implicit logger: Logger[IO]
  ): Resource[IO, DoobieTransactor[IO]] =
    JdbcSupport
      .buildHikariTransactor(db.connection, db.pool, poolName, logger) { conf =>
        conf.setReadOnly(readOnly)
        conf
      }
      .map(DoobieTransactor(_, db.transactionRetry, readOnly))

  def buildReadWriteTransactors(
      read: TransactorSettings,
      write: TransactorSettings,
      poolNamePrefix: String
  )(
      implicit logger: Logger[IO]
  ): Resource[IO, ReadWriteTransactors[IO]] =
    (
      buildTransactor(read, poolNamePrefix ++ "ReadPool", readOnly = true),
      buildTransactor(write, poolNamePrefix ++ "WritePool", readOnly = false)
    ).mapN((readXa, writeXa) => ReadWriteTransactors(readXa, writeXa))

  def buildMeteredReadWriteTransactors(
      read: TransactorSettings,
      write: TransactorSettings,
      poolNamePrefix: String,
      metricsTrackerFactory: MetricsTrackerFactory
  )(
      implicit logger: Logger[IO]
  ): Resource[IO, ReadWriteTransactors[IO]] =
    (
      buildMeteredTransactor(read, poolNamePrefix ++ "ReadPool", metricsTrackerFactory, readOnly = true),
      buildMeteredTransactor(write, poolNamePrefix ++ "WritePool", metricsTrackerFactory, readOnly = false)
    ).mapN((readXa, writeXa) => ReadWriteTransactors(readXa, writeXa))
}
