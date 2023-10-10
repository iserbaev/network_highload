package ru.nh.db.tarantool

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory

trait TarantoolModule {
  def client: TarantoolHttpClient
}

object TarantoolModule {
  final case class Config(httpHost: String, httpPort: Int)

  object Config {

    import pureconfig.ConfigSource
    import pureconfig.generic.auto._

    def load: IO[Config] = IO
      .fromTry(
        ConfigSource.default
          .at("tarantool")
          .load[Config]
          .leftMap(fails => new RuntimeException(fails.prettyPrint()))
          .toTry
      )
  }

  def resource(conf: Config)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, TarantoolModule] =
    TarantoolHttpClient.resource(conf.httpHost, conf.httpPort).map { c =>
      new TarantoolModule {
        override def client: TarantoolHttpClient = c
      }
    }
}
