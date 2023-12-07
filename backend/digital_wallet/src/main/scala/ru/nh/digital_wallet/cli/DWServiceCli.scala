package ru.nh.digital_wallet.cli

import cats.effect.{ ExitCode, IO }
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect.CommandIOApp
import io.prometheus.client.CollectorRegistry
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.{ LoggerFactory, SelfAwareStructuredLogger }
import ru.nh.config.ServerConfig
import ru.nh.db.PostgresModule
import ru.nh.db.flyway.FlywaySupport
import ru.nh.digital_wallet.DWModule
import ru.nh.http.HttpModule
import ru.nh.metrics.MetricsModule

object DWServiceCli
    extends CommandIOApp(
      name = "digital-wallet-service-cli",
      header = "Digital Wallet service CLI",
      helpFlag = true,
      version = BuildInfo.version,
    ) {
  val main = DWCli.all
}

object DWCli {
  def program(migrate: Boolean, mock: Boolean): IO[ExitCode] =
    (ServerConfig.load, DWModule.Config.load)
      .flatMapN { (serverConfig, dwConfig) =>
        implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
        implicit val logger: SelfAwareStructuredLogger[IO] =
          loggerFactory.getLoggerFromClass(classOf[DWCli.type])

        val migrateOrValidate =
          IO.pure(migrate)
            .ifM(
              ifTrue = FlywaySupport
                .migrate(
                  serverConfig.db.write.connection,
                  serverConfig.db.migrations.locations,
                  serverConfig.db.migrations.mixed,
                  serverConfig.db.migrations.flywayTableName
                )
                .void,
              ifFalse = FlywaySupport
                .validate(
                  serverConfig.db.write.connection,
                  serverConfig.db.migrations.locations,
                  serverConfig.db.migrations.mixed,
                  serverConfig.db.migrations.flywayTableName
                )
            )

        val runProgram =
          MetricsModule
            .prometheus(CollectorRegistry.defaultRegistry, serverConfig.metrics)
            .flatMap(m => PostgresModule(serverConfig.db, m.metricsFactory).tupleLeft(m))
            .flatMap { case (m, pg) =>
              DWModule
                .resource(dwConfig, pg)
                .flatMap { dWModule =>
                  HttpModule
                    .resource(serverConfig.http, dWModule.endpoints, m, "digital_wallet", pg.healthCheck)
                }
            }

        migrateOrValidate.unlessA(mock) *> runProgram.useForever

      }
      .as(ExitCode.Success)

  val forceMigrateOpt: Opts[Boolean] =
    Opts.flag("force-migrate", s"Forces DB migration", "f").orFalse

  val mockModeOpt: Opts[Boolean] =
    Opts.flag("mock", s"Start server with in-memory DB", "mm").orFalse

  val server = Command("server", "Start HTTP server simultaneously") {
    (forceMigrateOpt, mockModeOpt).mapN((m, mock) => DWCli.program(m, mock))
  }

  val all = Opts.subcommands(server)
}
