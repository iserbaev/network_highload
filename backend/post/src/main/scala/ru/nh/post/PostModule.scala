package ru.nh.post

import cats.data.NonEmptyList
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import ru.nh.PostService
import ru.nh.auth.AuthService
import ru.nh.db.PostgresModule
import ru.nh.http.SEndpoint
import ru.nh.post.db.PostgresPostAccessor
import ru.nh.post.http.PostEndpoints
import ru.nh.user.UserClient

import scala.concurrent.duration.DurationInt

trait PostModule {
  def service: PostService

  def endpoints: NonEmptyList[SEndpoint]
}

object PostModule {
  final case class ClientsConfig(user: UserServiceClientConfig)
  final case class UserServiceClientConfig(host: String, port: Int)

  def loadClientsConfig: IO[ClientsConfig] = IO
    .fromTry(
      ConfigSource.default.load[ClientsConfig].leftMap(fails => new RuntimeException(fails.prettyPrint())).toTry
    )

  def resource(clientsConfig: ClientsConfig, postgresModule: PostgresModule, authService: AuthService)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, PostModule] =
    (
      PostgresPostAccessor.inIO(postgresModule.rw),
      UserClient.resource(clientsConfig.user.host, clientsConfig.user.port)
    )
      .flatMapN { (accessor, userClient) =>
        PostManager(accessor).map { um =>
          new PostModule {

            val service: PostService = um

            val endpoints: NonEmptyList[SEndpoint] = new PostEndpoints(authService, um, userClient, 20.seconds).all
          }
        }
      }
}
