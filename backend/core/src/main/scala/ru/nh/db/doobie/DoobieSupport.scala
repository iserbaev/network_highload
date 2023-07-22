package ru.nh.db.doobie

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import doobie._
import doobie.hikari.HikariTransactor
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.user.db.flyway.{ FlywaySupport, MixedTransactions }
import ru.nh.user.db.jdbc.JdbcSupport

import scala.concurrent.duration.FiniteDuration
object DoobieSupport extends DoobieTransformations with RetrySupport {
  final case class DBSettings(
      database: Database,
      read: JdbcSupport.PoolConfig,
      write: JdbcSupport.PoolConfig,
      metrics: HikariMetrics,
      socketTimeout: FiniteDuration,
      keepAliveTimeout: FiniteDuration,
      transactionRetry: TransactionRetryConfig
  )
  final case class Database(
      connection: JdbcSupport.ConnectionConfig,
      migrations: DbMigrations
  )

  final case class TransactionRetryConfig(retryCount: Int, baseInterval: FiniteDuration)

  final case class DbMigrations(locations: List[String], mixed: MixedTransactions)

  final case class HikariMetrics(enabled: Boolean)

  final case class Transactors(write: Transactor[IO], read: Transactor[IO])

  def buildTransactors(
      config: DBSettings,
      poolNamePrefix: String,
      metricsTrackerFactory: MetricsTrackerFactory,
      logger: Logger[IO],
  ): Resource[IO, Transactors] = {
    def buildTransactor(poolName: String, pool: JdbcSupport.PoolConfig): Resource[IO, HikariTransactor[IO]] =
      JdbcSupport.buildHikariTransactor(config.database.connection, pool, poolName, logger) { hikariConfig =>
        if (config.metrics.enabled) JdbcSupport.withMetrics(metricsTrackerFactory)(hikariConfig): Unit
        hikariConfig.addDataSourceProperty("socketTimeout", config.socketTimeout.toMillis)
        hikariConfig.setKeepaliveTime(config.keepAliveTimeout.toMillis)
        hikariConfig.setConnectionTestQuery("SELECT 1")
        hikariConfig
      }

    (
      buildTransactor(poolNamePrefix ++ "WritePool", config.write),
      buildTransactor(poolNamePrefix ++ "ReadPool", config.read)
    ).mapN(Transactors)
  }

  def migrate(dbConfig: DBSettings)(
      implicit L: LoggerFactory[IO]
  ): IO[Unit] =
    L.fromClass(classOf[DoobieSupport.type]).flatMap { implicit log =>
      FlywaySupport
        .migrate(
          dbConfig.database.connection,
          dbConfig.database.migrations.locations,
          dbConfig.database.migrations.mixed
        )
        .void
    }

  def validate(dbConfig: DBSettings)(
      implicit L: LoggerFactory[IO]
  ): IO[Unit] =
    L.fromClass(classOf[DoobieSupport.type]).flatMap { implicit log =>
      FlywaySupport
        .validate(
          dbConfig.database.connection,
          dbConfig.database.migrations.locations,
          dbConfig.database.migrations.mixed
        )
        .void
    }
}
