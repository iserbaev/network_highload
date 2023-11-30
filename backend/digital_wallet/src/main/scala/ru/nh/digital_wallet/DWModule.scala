package ru.nh.digital_wallet

import cats.data.NonEmptyList
import cats.effect.{ IO, Resource }
import org.typelevel.log4cats.LoggerFactory
import ru.nh.auth.AuthService
import ru.nh.db.PostgresModule
import ru.nh.http.SEndpoint

trait DWModule {
  def service: WalletService

  def endpoints: NonEmptyList[SEndpoint]
}

object DWModule {

  def resource(postgresModule: PostgresModule, authService: AuthService)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, DWModule] = ???
}
