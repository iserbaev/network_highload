package ru.nh.user.cli

import cats.effect.{ ExitCode, IO }
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect.CommandIOApp
import io.prometheus.client.CollectorRegistry
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import ru.nh.db.doobie.DoobieSupport
import ru.nh.user.UserModule
import ru.nh.user.http.HttpModule
import ru.nh.user.metrics.MetricsModule

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
  def program(migrate: Boolean, mock: Boolean): IO[ExitCode] =
    IO
      .fromTry(
        ConfigSource.default.load[Config].leftMap(fails => new RuntimeException(fails.prettyPrint())).toTry
      )
      .flatMap { config =>
        implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

        val migrateOrValidate =
          IO.pure(migrate)
            .ifM(
              ifTrue = DoobieSupport.migrate(config.user.db),
              ifFalse = DoobieSupport.validate(config.user.db)
            )

        val runProgram =
          MetricsModule
            .prometheus(CollectorRegistry.defaultRegistry, config.metrics)
            .flatMap(m => UserModule(config.user, m).tupleLeft(m))
            .flatMap { case (m, u) =>
              HttpModule.resource(config.http, u, m)
            }

        migrateOrValidate.unlessA(mock) *> runProgram.useForever

      }
      .as(ExitCode.Success)

  val forceMigrateOpt: Opts[Boolean] =
    Opts.flag("force-migrate", s"Forces DB migration", "f").orFalse

  val mockModeOpt: Opts[Boolean] =
    Opts.flag("mock", s"Start server with in-memory DB", "mm").orFalse

  val server = Command("server", "Starts both gRPC and HTTP servers simultaneously") {
    (forceMigrateOpt, mockModeOpt).mapN((m, mock) => UserCli.program(m, mock))
  }

  val all = Opts.subcommands(server)
}
