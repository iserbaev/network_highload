package ru.nh.digital_wallet

import cats.data.NonEmptyList
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.{ LoggerFactory, SelfAwareStructuredLogger }
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
      useUpdatesChannel: Boolean,
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
  ): Resource[IO, DWModule] = {
    import BalanceCommandLogRow.balanceCommandLogSnakeCaseDecoder
    import BalanceEventLogRow.balanceEventLogSnakeCaseDecoder
    implicit val log: SelfAwareStructuredLogger[IO] = L.getLoggerFromClass(classOf[DWModule])

    (
      PgListener.channelEvents[BalanceCommandLogRow, BalanceEventLogRow](config.updatesChannel, postgresModule.rw),
      PostgresBalanceAccessor.resource(postgresModule.rw)
    ).flatMapN { (pgl, ba) =>
      val bc = BalanceEvents(
        ba,
        config.updateTickInterval,
        config.useUpdatesChannel,
        config.updatesChannelTick,
        () => pgl.listenFiltered(_.event),
        config.defaultLimit,
        config.eventBufferTtl
      ).flatMap(
        BalanceCommands(
          ba,
          config.updateTickInterval,
          config.useUpdatesChannel,
          config.updatesChannelTick,
          () => pgl.listenFiltered(_.cmd),
          config.defaultLimit,
          config.eventBufferTtl,
          _
        )
      )

      bc.flatMap(WalletService.resource(_, ba)).map { ws =>
        new DWModule {
          val service: WalletService = ws

          val endpoints: NonEmptyList[SEndpoint] = new DWEndpoints(service, 25.seconds).all
        }
      }

    }
  }
}
