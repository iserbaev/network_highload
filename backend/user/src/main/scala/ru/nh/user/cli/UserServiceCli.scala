package ru.nh.user.cli

import cats.data.NonEmptyChain
import cats.effect.{ ExitCode, IO, Resource }
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
import ru.nh.user.db.{ Neo4JModule, Populate }
import ru.nh.user.{ UserAccessor, UserModule }

object UserServiceCli
    extends CommandIOApp(
      name = "user-service-cli",
      header = "User service CLI",
      helpFlag = true,
      version = BuildInfo.version,
    ) {
  val main = UserCli.all
}

object UserCli {
  def program(migrate: Boolean, mock: Boolean, populate: Boolean): IO[ExitCode] =
    (ServerConfig.load, Neo4JModule.Config.load)
      .flatMapN { (config, neo4jConfig) =>
        implicit val loggerFactory: LoggerFactory[IO]      = Slf4jFactory.create[IO]
        implicit val logger: SelfAwareStructuredLogger[IO] = loggerFactory.getLoggerFromClass(classOf[UserCli.type])

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

        def populateProgram(userAccessor: UserAccessor[IO]): IO[Unit] =
          Populate.getUsers
            .flatMap(u =>
              NonEmptyChain.fromSeq(u).traverse_ {
                _.grouped(5000).toList.traverse_ { nec =>
                  userAccessor.saveBatch(nec) <* IO.println(s"Populate users ${nec.length}")
                }
              }
            )
            .whenA(populate)

        val runProgram =
          for {
            m <- MetricsModule.prometheus(CollectorRegistry.defaultRegistry, config.metrics)
            p <- PostgresModule(config.db, m.metricsFactory)
            a <- AuthClient.resource(config.auth.host, config.auth.port)
            n <- Neo4JModule(neo4jConfig)
            u <- UserModule(p, n, a, config.auth.key)
            _ <- HttpModule.resource(config.http, u.endpoints, m, "user", p.healthCheck)
            _ <- Resource.eval(populateProgram(u.accessor))
          } yield ()

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
    (forceMigrateOpt, mockModeOpt, populateOpt).mapN((m, mock, pop) => UserCli.program(m, mock, pop))
  }

  val all = Opts.subcommands(server)
}
