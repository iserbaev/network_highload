package ru.nh.config

import cats.effect.IO
import cats.syntax.all._
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import ru.nh.auth.inmemory.AuthConfig
import ru.nh.db.PostgresModule
import ru.nh.http.HttpModule
import ru.nh.metrics.MetricsModule

final case class ServerConfig(
    http: HttpModule.Config,
    db: PostgresModule.Config,
    metrics: MetricsModule.Config,
    auth: AuthConfig
)

object ServerConfig {
  def load: IO[ServerConfig] = IO
    .fromTry(
      ConfigSource.default.load[ServerConfig].leftMap(fails => new RuntimeException(fails.prettyPrint())).toTry
    )
}
