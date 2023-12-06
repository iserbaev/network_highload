package ru.nh.digital_wallet

import cats.data.NonEmptyList
import cats.effect.{ IO, Resource }
import org.typelevel.log4cats.LoggerFactory
import ru.nh.auth.AuthService
import ru.nh.db.PostgresModule
import ru.nh.digital_wallet.http.DWEndpoints
import ru.nh.http.SEndpoint

import scala.annotation.unused
import scala.concurrent.duration.DurationInt

trait DWModule {
  def service: WalletService

  def endpoints: NonEmptyList[SEndpoint]
}

object DWModule {

  def resource(@unused postgresModule: PostgresModule, @unused authService: AuthService)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, DWModule] = WalletService.noop.map { ws =>
    new DWModule {
      override def service: WalletService = ws

      override def endpoints: NonEmptyList[SEndpoint] = new DWEndpoints(ws, 1.second).all
    }
  }
}
