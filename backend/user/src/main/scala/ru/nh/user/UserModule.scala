package ru.nh.user

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.UserService
import ru.nh.auth.AuthService
import ru.nh.db.PostgresModule
import ru.nh.http.SEndpoint
import ru.nh.user.db.{ Neo4JModule, Neo4jFriendsAccessor, PostgresUserAccessor }
import ru.nh.user.http.UserEndpoints

trait UserModule {
  def accessor: UserAccessor[IO]

  def service: UserService

  def endpoints: NonEmptyList[SEndpoint]
}

object UserModule {

  def apply(postgresModule: PostgresModule, neo4JModule: Neo4JModule, authService: AuthService, appKey: String)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, UserModule] =
    (PostgresUserAccessor.inIO(postgresModule.rw), Neo4jFriendsAccessor.inIO(neo4JModule.driver))
      .flatMapN { (acc, friends) =>
        UserManager(acc, friends).map { um =>
          new UserModule {
            val accessor: UserAccessor[IO] = acc

            val service: UserService = um

            val endpoints: NonEmptyList[SEndpoint] = new UserEndpoints(authService, um, appKey).all
          }
        }
      }
}
