package ru.nh.user.db.jdbc

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import doobie.hikari.HikariTransactor
import doobie.util.log.LogHandler
import org.typelevel.log4cats.Logger
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._
import ru.nh.user.db.flyway.FlywaySupport

import scala.annotation.unused
import scala.concurrent.duration._

object JdbcSupport extends JdbcSupport {

  final case class ConnectionConfig private (
      jdbcDriverName: String,
      jdbcUrl: String,
      user: String,
      password: String,
  ) extends FlywaySupport.DataSourceConfig {
    def withJdbcDriverName(jdbcDriverName: String): ConnectionConfig = copy(jdbcDriverName = jdbcDriverName)
    def withJdbcUrl(jdbcUrl: String): ConnectionConfig               = copy(jdbcUrl = jdbcUrl)
    def withUser(user: String): ConnectionConfig                     = copy(user = user)
    def withPassword(password: String): ConnectionConfig             = copy(password = password)
  }

  object ConnectionConfig {
    def apply(jdbcDriverName: String, jdbcUrl: String, user: String, password: String): ConnectionConfig =
      new ConnectionConfig(jdbcDriverName, jdbcUrl, user, password)

    @unused
    private def unapply(c: ConnectionConfig): ConnectionConfig = c
  }

  final case class PoolConfig private (
      connectionMaxPoolSize: Int,
      connectionIdlePoolSize: Int,
      connectionTimeout: FiniteDuration,
      connectionIdleTimeout: Option[FiniteDuration],
      connectionMaxLifetime: Option[FiniteDuration],
      leakDetectionThreshold: Option[FiniteDuration],
      socketTimeout: Option[FiniteDuration],
      keepAliveTimeout: Option[FiniteDuration]
  ) {
    def withConnectionMaxPoolSize(connectionMaxPoolSize: Int): PoolConfig =
      copy(connectionMaxPoolSize = connectionMaxPoolSize)
    def withConnectionIdlePoolSize(connectionIdlePoolSize: Int): PoolConfig =
      copy(connectionIdlePoolSize = connectionIdlePoolSize)
    def withConnectionTimeout(connectionTimeout: FiniteDuration): PoolConfig =
      copy(connectionTimeout = connectionTimeout)
    def withConnectionIdleTimeout(connectionIdleTimeout: Option[FiniteDuration]): PoolConfig =
      copy(connectionIdleTimeout = connectionIdleTimeout)
    def withConnectionMaxLifetime(connectionMaxLifetime: Option[FiniteDuration]): PoolConfig =
      copy(connectionMaxLifetime = connectionMaxLifetime)
    def withLeakDetectionThreshold(leakDetectionThreshold: Option[FiniteDuration]): PoolConfig =
      copy(leakDetectionThreshold = leakDetectionThreshold)
    def withSocketTimeout(socketTimeout: Option[FiniteDuration]): PoolConfig =
      copy(socketTimeout = socketTimeout)
    def withKeepAliveTimeout(keepAliveTimeout: Option[FiniteDuration]): PoolConfig =
      copy(keepAliveTimeout = keepAliveTimeout)
  }

  object PoolConfig {
    def apply(leakDetectionThreshold: Option[FiniteDuration]): PoolConfig = PoolConfig(
      connectionMaxPoolSize = 8,
      connectionIdlePoolSize = 1,
      connectionTimeout = 1.minute,
      connectionIdleTimeout = none,
      connectionMaxLifetime = none,
      leakDetectionThreshold,
    )

    def apply(
        connectionMaxPoolSize: Int,
        connectionIdlePoolSize: Int,
        connectionTimeout: FiniteDuration,
        connectionIdleTimeout: Option[FiniteDuration],
        connectionMaxLifetime: Option[FiniteDuration],
        leakDetectionThreshold: Option[FiniteDuration]
    ): PoolConfig =
      new PoolConfig(
        connectionMaxPoolSize,
        connectionIdlePoolSize,
        connectionTimeout,
        connectionIdleTimeout,
        connectionMaxLifetime,
        leakDetectionThreshold,
        none,
        none
      )

    def apply(
        connectionMaxPoolSize: Int,
        connectionIdlePoolSize: Int,
        connectionTimeout: FiniteDuration,
        connectionIdleTimeout: Option[FiniteDuration],
        connectionMaxLifetime: Option[FiniteDuration],
        leakDetectionThreshold: Option[FiniteDuration],
        socketTimeout: Option[FiniteDuration]
    ): PoolConfig =
      new PoolConfig(
        connectionMaxPoolSize,
        connectionIdlePoolSize,
        connectionTimeout,
        connectionIdleTimeout,
        connectionMaxLifetime,
        leakDetectionThreshold,
        socketTimeout,
        none
      )

    def apply(
        connectionMaxPoolSize: Int,
        connectionIdlePoolSize: Int,
        connectionTimeout: FiniteDuration,
        connectionIdleTimeout: Option[FiniteDuration],
        connectionMaxLifetime: Option[FiniteDuration],
        leakDetectionThreshold: Option[FiniteDuration],
        socketTimeout: Option[FiniteDuration],
        keepAliveTimeout: Option[FiniteDuration]
    ): PoolConfig =
      new PoolConfig(
        connectionMaxPoolSize,
        connectionIdlePoolSize,
        connectionTimeout,
        connectionIdleTimeout,
        connectionMaxLifetime,
        leakDetectionThreshold,
        socketTimeout,
        keepAliveTimeout
      )

    @unused
    private def unapply(p: PoolConfig): PoolConfig = p
  }

  implicit val PoolConfigReader: ConfigReader[PoolConfig]             = deriveReader[PoolConfig]
  implicit val ConnectionConfigReader: ConfigReader[ConnectionConfig] = deriveReader[ConnectionConfig]

}

trait JdbcSupport extends FlywaySupport {

  import JdbcSupport.{ ConnectionConfig, PoolConfig }

  def hikariTransactor(
      connection: ConnectionConfig,
      pool: PoolConfig,
      poolName: String,
      logger: Logger[IO],
  ): Resource[IO, HikariTransactor[IO]] =
    buildHikariTransactor(connection, pool, poolName, Log4CatsLogHandler(logger).some)(identity)

  def hikariTransactor(
      connection: ConnectionConfig,
      pool: PoolConfig,
      poolName: String,
      logHandler: Option[LogHandler[IO]],
  ): Resource[IO, HikariTransactor[IO]] =
    buildHikariTransactor(connection, pool, poolName, logHandler)(identity)

  def buildHikariTransactor(
      connection: ConnectionConfig,
      pool: PoolConfig,
      poolName: String,
      logger: Logger[IO],
  )(f: HikariConfig => HikariConfig): Resource[IO, HikariTransactor[IO]] =
    buildHikariTransactor(connection, pool, poolName, Log4CatsLogHandler(logger).some)(f)

  def buildHikariTransactor(
      connection: ConnectionConfig,
      pool: PoolConfig,
      poolName: String,
      logHandler: Option[LogHandler[IO]],
  )(f: HikariConfig => HikariConfig): Resource[IO, HikariTransactor[IO]] = Resource.suspend {
    makeHikariConfig(connection, pool, poolName)
      .map(f)
      .map(HikariTransactor.fromHikariConfig[IO](_, logHandler))
  }

  def withMetrics(metricsTrackerFactory: MetricsTrackerFactory): HikariConfig => HikariConfig = { hc =>
    hc.setMetricsTrackerFactory(metricsTrackerFactory)
    hc
  }

  def makeHikariConfig(connection: ConnectionConfig, pool: PoolConfig, poolName: String): IO[HikariConfig] = IO {
    val conf = new HikariConfig()

    conf.setMaximumPoolSize(pool.connectionMaxPoolSize)
    conf.setMinimumIdle(pool.connectionIdlePoolSize)
    conf.setConnectionTimeout(pool.connectionTimeout.toMillis)
    conf.setLeakDetectionThreshold(pool.leakDetectionThreshold.map(_.toMillis).getOrElse(0L))

    pool.connectionMaxLifetime.foreach { max =>
      conf.setMaxLifetime(max.toMillis)
    }
    pool.connectionIdleTimeout.foreach { idle =>
      conf.setIdleTimeout(idle.toMillis)
    }

    pool.socketTimeout.foreach { socketTimeout =>
      conf.addDataSourceProperty("socketTimeout", socketTimeout.toMillis)
    }

    pool.keepAliveTimeout.foreach { keepAliveTimeout =>
      conf.setKeepaliveTime(keepAliveTimeout.toMillis)
      conf.setConnectionTestQuery("SELECT 1")
    }

    conf.setPoolName(poolName)

    conf.setDriverClassName(connection.jdbcDriverName)
    conf.setJdbcUrl(connection.jdbcUrl)
    conf.setUsername(connection.user)
    conf.setPassword(connection.password)

    conf
  }

}
