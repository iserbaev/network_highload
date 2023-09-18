package ru.nh.post.db

import cats.NonEmptyTraverse
import cats.data.{ Chain, OptionT }
import cats.effect.{ IO, Resource }
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.post.PostAccessor
import ru.nh.post.PostAccessor.PostRow

import java.util.UUID

class PostgresPostAccessor extends PostAccessor[ConnectionIO] {
  import ru.nh.db.ensureUpdated

  def addPost(userId: UUID, text: String): ConnectionIO[UUID] =
    sql"""INSERT INTO posts(user_id, text)
         |VALUES ($userId, $text)
         |RETURNING post_id
         """.stripMargin.update
      .withGeneratedKeys[UUID]("post_id")
      .compile
      .lastOrError

  def getPost(postId: UUID): ConnectionIO[Option[PostRow]] =
    sql"""SELECT user_id, post_id, index, created_at, text
         |FROM posts
         |WHERE post_id = $postId
         """.stripMargin
      .query[PostRow]
      .option

  def updatePost(postId: UUID, text: String): ConnectionIO[Unit] =
    ensureUpdated {
      sql"""UPDATE posts
           |   SET text = $text
           | WHERE post_id = $postId
           |""".stripMargin.update.run
    }

  def deletePost(postId: UUID): ConnectionIO[Unit] =
    ensureUpdated {
      sql"""DELETE FROM posts
           |WHERE post_id = $postId""".stripMargin.update.run
    }

  def postFeed(userId: UUID, offset: Int, limit: Int): ConnectionIO[Chain[PostRow]] =
    sql"""SELECT friend_posts.* FROM
         |    (
         |        SELECT friend_id FROM friends WHERE user_id = $userId
         |    ) AS friend_ids,
         |LATERAL
         |    (
         |        SELECT user_id, post_id, index, created_at, text
         |        FROM posts p
         |        WHERE p.user_id = friend_ids.friend_id
         |        ORDER BY created_at
         |        LIMIT ${offset + limit}
         |    ) AS friend_posts
         |OFFSET $offset
         |LIMIT $limit
         """.stripMargin
      .query[PostRow]
      .to[List]
      .map(Chain.fromSeq)

  def userPosts(userId: UUID, fromIndex: Long): ConnectionIO[Chain[PostRow]] =
    sql"""SELECT user_id, post_id, index, created_at, text
         |FROM posts p
         |WHERE p.user_id = $userId
         |AND p.index > $fromIndex
         |ORDER BY index
         |LIMIT 100""".stripMargin
      .query[PostRow]
      .to[List]
      .map(Chain.fromSeq)

  def getPostsLog[R[_]: NonEmptyTraverse](
      userIds: R[UUID],
      lastIndex: Long,
      limit: Int
  ): ConnectionIO[Vector[PostRow]] =
    sql"""SELECT user_id, post_id, index, created_at, text
         |FROM posts p
         |WHERE ${Fragments.in(fr"p.user_id", userIds)}
         |AND p.index > $lastIndex
         |ORDER BY index
         |LIMIT $limit""".stripMargin
      .query[PostRow]
      .to[Vector]

  def getLastPost(userId: UUID): OptionT[ConnectionIO, PostRow] = OptionT {
    sql"""SELECT user_id, post_id, index, created_at, text
         |FROM posts p
         |WHERE p.user_id = $userId
         | ORDER BY index DESC
         | LIMIT 1
         |""".stripMargin
      .query[PostRow]
      .option
  }
}

object PostgresPostAccessor {
  import ru.nh.db.transactors._
  import ru.nh.db.transactors.syntax._

  def resource: Resource[IO, PostgresPostAccessor] = Resource.eval {
    IO {
      new PostgresPostAccessor
    }
  }

  def inIO(rw: ReadWriteTransactors[IO])(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, PostAccessor[IO]] = Resource.suspend {
    L.fromClass(classOf[PostgresPostAccessor]).map { implicit log =>
      PostgresPostAccessor.resource.map(_.mapK(rw.readK, rw.writeK))
    }
  }
}
