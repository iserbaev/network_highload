package ru.nh.auth.cli

import cats.effect.{ ExitCode, IO }
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect.CommandIOApp
import io.prometheus.client.CollectorRegistry
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.{ LoggerFactory, SelfAwareStructuredLogger }
import ru.nh.auth.AuthModule
import ru.nh.config.ServerConfig
import ru.nh.db.PostgresModule
import ru.nh.db.flyway.FlywaySupport
import ru.nh.http.HttpModule
import ru.nh.metrics.MetricsModule

object AuthServiceCli
    extends CommandIOApp(
      name = "auth-service-cli",
      header = "Auth service CLI",
      helpFlag = true,
      version = BuildInfo.version,
    ) {
  val main = AuthCli.all
}

object AuthCli {
  def program(migrate: Boolean, mock: Boolean): IO[ExitCode] =
    ServerConfig.load
      .flatMap { config =>
        implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
        implicit val logger: SelfAwareStructuredLogger[IO] =
          loggerFactory.getLoggerFromClass(classOf[AuthCli.type])

        val migrateOrValidate =
          IO.pure(migrate)
            .ifM(
              ifTrue = FlywaySupport
                .migrate(
                  config.db.write.connection,
                  config.db.migrations.locations,
                  config.db.migrations.mixed,
                  config.db.migrations.flywayTableName
                )
                .void,
              ifFalse = FlywaySupport
                .validate(
                  config.db.write.connection,
                  config.db.migrations.locations,
                  config.db.migrations.mixed,
                  config.db.migrations.flywayTableName
                )
            )

        val runProgram =
          MetricsModule
            .prometheus(CollectorRegistry.defaultRegistry, config.metrics)
            .flatMap(m => PostgresModule(config.db, m.metricsFactory).tupleLeft(m))
            .flatMap { case (m, pg) =>
              AuthModule.resource(pg, config.auth.key).flatMap { auth =>
                HttpModule
                  .resource(config.http, auth.endpoints, m, "auth")
              }

            }

        migrateOrValidate.unlessA(mock) *> runProgram.useForever

      }
      .as(ExitCode.Success)

  val forceMigrateOpt: Opts[Boolean] =
    Opts.flag("force-migrate", s"Forces DB migration", "f").orFalse

  val mockModeOpt: Opts[Boolean] =
    Opts.flag("mock", s"Start server with in-memory DB", "mm").orFalse

  val server = Command("server", "Start HTTP servers simultaneously") {
    (forceMigrateOpt, mockModeOpt).mapN((m, mock) => AuthCli.program(m, mock))
  }

  val all = Opts.subcommands(server)
}
