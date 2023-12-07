package ru.nh.digital_wallet

import cats.data.NonEmptyList
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpLogger
import ru.nh.db.{ PgListener, PostgresModule }
import ru.nh.digital_wallet.BalanceAccessor.{ BalanceCommandLogRow, BalanceEventLogRow }
import ru.nh.digital_wallet.db.PostgresBalanceAccessor
import ru.nh.digital_wallet.events.{ BalanceCommands, BalanceEvents }
import ru.nh.digital_wallet.http.DWEndpoints
import ru.nh.http.SEndpoint

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

trait DWModule {
  def service: WalletService

  def endpoints: NonEmptyList[SEndpoint]
}

object DWModule {
  final case class Config(
      updateTickInterval: FiniteDuration,
      eventBufferTtl: FiniteDuration,
      defaultLimit: Int,
      defaultBatchSize: Int,
      updatesChannel: String,
      updatesChannelTick: FiniteDuration
  )
  object Config {
    import pureconfig.ConfigSource
    import pureconfig.generic.auto._

    def load: IO[Config] = IO
      .fromTry(
        ConfigSource.default
          .at("dw")
          .load[Config]
          .leftMap(fails => new RuntimeException(fails.prettyPrint()))
          .toTry
      )
  }

  def resource(config: Config, postgresModule: PostgresModule)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, DWModule] =
    (
      PgListener.channelEvents(config.updatesChannel, postgresModule.rw)(NoOpLogger[IO]),
      PostgresBalanceAccessor.resource(postgresModule.rw)
    ).flatMapN { (pgl, ba) =>
      val be = BalanceEvents(
        ba,
        config.updateTickInterval,
        config.updatesChannelTick,
        () =>
          pgl.listenFiltered(_.event.map(io.circe.parser.decode[BalanceEventLogRow])).flatMap(fs2.Stream.fromEither[IO](_)),
        config.defaultLimit,
        config.eventBufferTtl
      )

      val bc = BalanceCommands(
        ba,
        config.updateTickInterval,
        config.updatesChannelTick,
        () =>
          pgl
            .listenFiltered(_.event.map(io.circe.parser.decode[BalanceCommandLogRow]))
            .flatMap(fs2.Stream.fromEither[IO](_)),
        config.defaultLimit,
        config.eventBufferTtl
      )

      (be, bc).flatMapN(WalletService.resource(_, _, ba)).map { ws =>
        new DWModule {
          val service: WalletService = ws

          val endpoints: NonEmptyList[SEndpoint] = new DWEndpoints(service, 25.seconds).all
        }
      }

    }
}
