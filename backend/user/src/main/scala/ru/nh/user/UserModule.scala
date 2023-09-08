package ru.nh.user

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.catsSyntaxTuple2Semigroupal
import org.typelevel.log4cats.LoggerFactory
import ru.nh.UserService
import ru.nh.auth.AuthModule
import ru.nh.db.PostgresModule
import ru.nh.http.SEndpoint
import ru.nh.user.db.{ PostgresPostAccessor, PostgresUserAccessor }
import ru.nh.user.http.{ PostEndpoints, UserEndpoints }

trait UserModule {
  def accessor: UserAccessor[IO]

  def service: UserService

  def endpoints: NonEmptyList[SEndpoint]
}

object UserModule {

  def apply(postgresModule: PostgresModule, authModule: AuthModule)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, UserModule] =
    (PostgresPostAccessor.inIO(postgresModule.rw), PostgresUserAccessor.inIO(postgresModule.rw))
      .flatMapN { (postAcc, acc) =>
        UserManager(acc, postAcc).map { um =>
          new UserModule {
            val accessor: UserAccessor[IO] = acc

            val service: UserService = um

            val endpoints: NonEmptyList[SEndpoint] =
              new UserEndpoints(authModule.service, um).all ::: new PostEndpoints(authModule.service, um).all
          }
        }
      }
}
