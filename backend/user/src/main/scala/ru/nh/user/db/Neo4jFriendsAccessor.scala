package ru.nh.user.db

import cats.effect.IO
import cats.effect.kernel.Resource
import neotypes.Driver
import neotypes.implicits.syntax.string._
import ru.nh.Friends
import ru.nh.user.FriendsAccessor

import java.util.UUID // Provides the query[T] extension method. // Provides the query[T] extension method.
class Neo4jFriendsAccessor(driver: Driver[IO]) extends FriendsAccessor[IO] {

  def addFriends(f: Friends): IO[Unit] = driver.transact { tx =>
    s"""MERGE (u:User {user_id: '${f.userId}'})
       |MERGE (u2:User {user_id: '${f.friendId}'})
       |MERGE (u)-[:FRIEND_OF]-(u2)
       |""".stripMargin.query[Unit].execute(tx)
  }

  def deleteFriend(f: Friends): IO[Unit] =
    s"MATCH (:User {user_id: '${f.userId}'})-[r:FRIEND_OF]-(:User {user_id: '${f.friendId}'}) DELETE r"
      .query[Unit]
      .single(driver)

  def getFriends(userId: UUID): IO[List[Friends]] =
    s"MATCH (u:User { user_id: '$userId'}) - [:FRIEND_OF]-(friend) RETURN  friend.user_id;"
      .readOnlyQuery[UUID]
      .list(driver)
      .map(_.map(fid => Friends(userId, fid)))

}

object Neo4jFriendsAccessor {
  final case class User(userId: UUID)
  def resource(driver: Driver[IO]): Resource[IO, Neo4jFriendsAccessor] =
    Resource.eval(IO(new Neo4jFriendsAccessor(driver)))

  def inIO(driver: Driver[IO]): Resource[IO, Neo4jFriendsAccessor] =
    resource(driver)
}
