package ru.nh.user

import cats.effect.IO
import cats.effect.kernel.Resource
import org.typelevel.log4cats.LoggerFactory
import ru.nh.db.doobie.DoobieSupport
import ru.nh.user.db.{ PostgresModule, PostgresUserAccessor }
import ru.nh.user.metrics.MetricsModule

trait UserModule {
  def accessor: UserAccessor[IO]

  def service: UserService
}

object UserModule {

  final case class Config(db: DoobieSupport.DBSettings)

  def apply(config: Config, metricsModule: MetricsModule)(implicit L: LoggerFactory[IO]): Resource[IO, UserModule] =
    PostgresModule(config.db, metricsModule.metricsFactory).flatMap { transactors =>
      PostgresUserAccessor
        .inIO(config.db.transactionRetry, transactors.write, transactors.read)
        .flatMap { acc =>
          UserManager(acc).map { us =>
            new UserModule {
              def accessor: UserAccessor[IO] = acc

              def service: UserService = us
            }
          }
        }
    }
}
