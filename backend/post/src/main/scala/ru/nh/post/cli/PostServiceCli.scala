package ru.nh.post.cli

import cats.effect.{ ExitCode, IO }
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect.CommandIOApp
import io.prometheus.client.CollectorRegistry
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.{ LoggerFactory, SelfAwareStructuredLogger }
import ru.nh.auth.AuthClient
import ru.nh.config.ServerConfig
import ru.nh.db.PostgresModule
import ru.nh.db.flyway.FlywaySupport
import ru.nh.http.HttpModule
import ru.nh.metrics.MetricsModule
import ru.nh.post.PostModule

object PostServiceCli
    extends CommandIOApp(
      name = "post-service-cli",
      header = "Post service CLI",
      helpFlag = true,
      version = BuildInfo.version,
    ) {
  val main = PostCli.all
}

object PostCli {
  def program(migrate: Boolean, mock: Boolean): IO[ExitCode] =
    (PostModule.loadClientsConfig, ServerConfig.load)
      .flatMapN { (clientsConfig, serverConfig) =>
        implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
        implicit val logger: SelfAwareStructuredLogger[IO] =
          loggerFactory.getLoggerFromClass(classOf[PostCli.type])

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
              AuthClient.resource(serverConfig.auth.host, serverConfig.auth.port).flatMap { auth =>
                PostModule
                  .resource(clientsConfig, pg, auth)
                  .flatMap(postModule =>
                    HttpModule
                      .resource(serverConfig.http, postModule.endpoints, m, "post")
                  )
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
    (forceMigrateOpt, mockModeOpt).mapN((m, mock) => PostCli.program(m, mock))
  }

  val all = Opts.subcommands(server)
}
