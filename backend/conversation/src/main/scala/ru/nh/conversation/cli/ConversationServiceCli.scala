package ru.nh.conversation.cli

import cats.effect.{ ExitCode, IO }
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect.CommandIOApp
import io.prometheus.client.CollectorRegistry
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.{ LoggerFactory, SelfAwareStructuredLogger }
import ru.nh.auth.AuthModule
import ru.nh.config.ServerConfig
import ru.nh.conversation.ConversationModule
import ru.nh.db.PostgresModule
import ru.nh.db.flyway.FlywaySupport
import ru.nh.http.HttpModule
import ru.nh.metrics.MetricsModule

object ConversationServiceCli
    extends CommandIOApp(
      name = "conversation-service-cli",
      header = "Conversation service CLI",
      helpFlag = true,
      version = BuildInfo.version,
    ) {
  val main = ConversationCli.all
}

object ConversationCli {
  def program(migrate: Boolean, mock: Boolean): IO[ExitCode] =
    ServerConfig.load
      .flatMap { config =>
        implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
        implicit val logger: SelfAwareStructuredLogger[IO] =
          loggerFactory.getLoggerFromClass(classOf[ConversationCli.type])

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
              AuthModule.inMemory.flatMap { auth => // TODO Add persistent Auth
                ConversationModule.resource(pg, auth)
                  .flatMap(conversationModule =>
                    HttpModule
                      .resource(config.http, conversationModule.endpoints, auth, m, "conversation endpoints")
                  )
              }

            }

        migrateOrValidate.unlessA(mock) *> runProgram.useForever

      }
      .as(ExitCode.Success)

  val forceMigrateOpt: Opts[Boolean] =
    Opts.flag("force-migrate", s"Forces DB migration", "f").orFalse

  val populateOpt: Opts[Boolean] =
    Opts.flag("populate", s"Populate database with dummy data", "pp").orFalse

  val mockModeOpt: Opts[Boolean] =
    Opts.flag("mock", s"Start server with in-memory DB", "mm").orFalse

  val server = Command("server", "Starts both gRPC and HTTP servers simultaneously") {
    (forceMigrateOpt, mockModeOpt, populateOpt).mapN((m, mock, pop) => ConversationCli.program(m, mock, pop))
  }

  val all = Opts.subcommands(server)
}
