package ru.nh.user.db

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import neotypes.cats.effect.implicits._
import neotypes.{ Driver, GraphDatabase }
import org.neo4j.driver.AuthTokens
class Neo4JModule(val driver: Driver[IO])

object Neo4JModule {
  final case class Config(url: String, login: String, password: String)

  object Config {
    import pureconfig.ConfigSource
    import pureconfig.generic.auto._

    def load: IO[Config] = IO
      .fromTry(
        ConfigSource.default
          .at("graph")
          .load[Config]
          .leftMap(fails => new RuntimeException(fails.prettyPrint()))
          .toTry
      )
  }

  def apply(config: Config): Resource[IO, Neo4JModule] =
    GraphDatabase
      .driver[IO](config.url, AuthTokens.basic(config.login, config.password))
      .map(new Neo4JModule(_))
}
