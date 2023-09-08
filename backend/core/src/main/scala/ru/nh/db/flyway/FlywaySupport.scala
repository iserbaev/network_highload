package ru.nh.db.flyway

import cats.effect.IO
import cats.syntax.all._
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.exception.FlywayValidateException
import org.flywaydb.core.api.output.{ MigrateErrorResult, MigrateResult }
import org.typelevel.log4cats.Logger

import scala.jdk.CollectionConverters._

object FlywaySupport extends FlywaySupport {
  trait DataSourceConfig {
    def jdbcUrl: String
    def user: String
    def password: String
  }
}

trait FlywaySupport {
  import FlywaySupport.DataSourceConfig

  def configureFlywayDataSource(
      config: DataSourceConfig,
      locations: List[String],
      mixed: MixedTransactions,
      flywayTableName: String
  ): IO[Flyway] = IO.blocking {
    Flyway.configure
      .dataSource(
        config.jdbcUrl,
        config.user,
        config.password
      )
      .baselineOnMigrate(true)
      .locations(locations: _*)
      .table(flywayTableName)
      .mixed(mixed match {
        case MixedTransactions.Allow => true
        case MixedTransactions.Deny  => false
      })
      .load()
  }

  def migrate(config: DataSourceConfig, locations: List[String], mixed: MixedTransactions, flywayTableName: String)(
      implicit log: Logger[IO]
  ): IO[MigrateResult] =
    log.info(s"Running migrations from: [${locations.iterator.mkString(", ")}]") *>
      configureFlywayDataSource(config, locations, mixed, flywayTableName)
        .map(_.migrate())
        .flatTap {
          case e: MigrateErrorResult =>
            log.error(
              s"Migration failure after ${e.migrationsExecuted} executed migrations," +
                s" reason: [${e.error.message}] ${e.error.message}."
            )
          case r =>
            log.info(s"Migration success after ${r.migrationsExecuted} executed migrations.")
        }

  def validate(config: DataSourceConfig, locations: List[String], mixed: MixedTransactions, flywayTableName: String)(
      implicit log: Logger[IO]
  ): IO[Unit] =
    log.info(s"Validating migrations from: [${locations.iterator.mkString(", ")}]") *>
      configureFlywayDataSource(config, locations, mixed, flywayTableName)
        .map(_.validateWithResult())
        .flatMap { result =>
          if (result.validationSuccessful) IO.unit
          else {
            result.invalidMigrations.asScala.toList.traverse_ { error =>
              log.warn(
                s"Migration validation failed for '${error.version}@${error.filepath}':" +
                  s" [${error.errorDetails.errorCode}] ${error.errorDetails.errorMessage}"
              )
            } *> IO.raiseError {
              new FlywayValidateException(
                result.errorDetails,
                result.getAllErrorMessages
              )
            }
          }
        }

}
