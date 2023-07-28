package ru.nh.user

import cats.effect.IO
import cats.effect.kernel.Resource
import org.typelevel.log4cats.LoggerFactory
import ru.nh.user.db.{ PostgresModule, PostgresUserAccessor }

trait UserModule {
  def accessor: UserAccessor[IO]

  def service: UserService
}

object UserModule {

  def apply(postgresModule: PostgresModule)(implicit L: LoggerFactory[IO]): Resource[IO, UserModule] =
    PostgresUserAccessor
      .inIO(postgresModule.rw)
      .flatMap { acc =>
        UserManager(acc).map { us =>
          new UserModule {
            def accessor: UserAccessor[IO] = acc

            def service: UserService = us
          }
        }
      }
}
