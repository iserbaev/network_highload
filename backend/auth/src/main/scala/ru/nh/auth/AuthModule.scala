package ru.nh.auth

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Resource
import org.typelevel.log4cats.LoggerFactory
import ru.nh.auth.db.PostgresLoginAccessor
import ru.nh.db.PostgresModule
import ru.nh.http.SEndpoint

trait AuthModule {
  def service: AuthService

  def endpoints: NonEmptyList[SEndpoint]
}

object AuthModule {
  def resource(postgresModule: PostgresModule, key: String)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, AuthModule] =
    PostgresLoginAccessor.inIO(postgresModule.rw).flatMap(AuthService.apply(key, _)).map { as =>
      new AuthModule {
        val service: AuthService = as

        val endpoints: NonEmptyList[SEndpoint] = new AuthEndpoint(service).all
      }
    }
}
