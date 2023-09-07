package ru.nh.post

import cats.data.{ Chain, OptionT }
import cats.{ NonEmptyTraverse, ~> }
import ru.nh.post.PostAccessor.{ PostAccessorMapK, PostRow }
import ru.nh.user.Post

import java.time.Instant
import java.util.UUID

trait PostAccessor[F[_]] {
  def addPost(userId: UUID, text: String): F[UUID]
  def getPost(postId: UUID): F[Option[PostRow]]
  def updatePost(postId: UUID, text: String): F[Unit]
  def deletePost(postId: UUID): F[Unit]
  def postFeed(userId: UUID, offset: Int, limit: Int): F[Chain[PostRow]]
  def userPosts(userId: UUID, fromIndex: Long): F[Chain[PostRow]]
  def getPostsLog[R[_]: NonEmptyTraverse](
      userIds: R[UUID],
      lastIndex: Long,
      limit: Int
  ): F[Vector[PostRow]]
  def getLastPost(userId: UUID): OptionT[F, PostRow]

  def mapK[G[_]](read: F ~> G, write: F ~> G): PostAccessor[G] =
    new PostAccessorMapK(this, read, write)
}

object PostAccessor {
  final case class PostRow(
      userId: UUID,
      postId: UUID,
      index: Long,
      createdAt: Instant,
      text: String
  ) {
    def toPost: Post =
      Post(postId, text, userId, createdAt)
  }
  object PostRow {
    implicit val ordering: Ordering[PostRow] = Ordering.by[PostRow, Instant](_.createdAt)
  }

  private[post] final class PostAccessorMapK[F[_], G[_]](underlying: PostAccessor[F], read: F ~> G, write: F ~> G)
      extends PostAccessor[G] {
    def addPost(userId: UUID, text: String): G[UUID] =
      write(underlying.addPost(userId, text))

    def getPost(postId: UUID): G[Option[PostRow]] =
      read(underlying.getPost(postId))

    def updatePost(postId: UUID, text: String): G[Unit] =
      write(underlying.updatePost(postId, text))

    def deletePost(postId: UUID): G[Unit] =
      write(underlying.deletePost(postId))

    def postFeed(userId: UUID, offset: Int, limit: Int): G[Chain[PostRow]] =
      read(underlying.postFeed(userId, offset, limit))

    def userPosts(userId: UUID, fromIndex: Long): G[Chain[PostRow]] =
      read(underlying.userPosts(userId, fromIndex))

    def getPostsLog[R[_]: NonEmptyTraverse](userIds: R[UUID], lastIndex: Long, limit: Int): G[Vector[PostRow]] =
      read(underlying.getPostsLog(userIds, lastIndex, limit))

    def getLastPost(userId: UUID): OptionT[G, PostRow] =
      underlying.getLastPost(userId).mapK(read)
  }
}
