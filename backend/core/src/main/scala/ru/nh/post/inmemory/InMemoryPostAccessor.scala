package ru.nh.post.inmemory

import cats.NonEmptyTraverse
import cats.data.{ Chain, OptionT }
import cats.effect.std.UUIDGen
import cats.effect.{ IO, Ref }
import cats.syntax.all._
import ru.nh.post.PostAccessor
import ru.nh.post.PostAccessor.PostRow
import ru.nh.user.FriendsAccessor

import java.util.UUID
import scala.collection.SortedSet

class InMemoryPostAccessor(
    friendsAccessor: FriendsAccessor[IO],
    posts: Ref[IO, Map[UUID, PostRow]],
    userPostIds: Ref[IO, Map[UUID, Set[UUID]]],
    counter: Ref[IO, Long]
) extends PostAccessor[IO] {
  def addPost(userId: UUID, text: String): IO[UUID] =
    (IO.realTimeInstant, UUIDGen.randomUUID[IO], counter.updateAndGet(_ + 1)).flatMapN { (now, postId, nextIndex) =>
      val postRow = PostRow(userId, postId, nextIndex, now, text)

      userPostIds
        .update(_.updatedWith(userId) {
          case Some(postIds) => (postIds + postId).some
          case None          => Set(postId).some
        }) *>
        posts
          .update(_.updated(postId, postRow))
          .as(postId)
    }

  def getPost(postId: UUID): IO[Option[PostRow]] =
    posts.get.map(_.get(postId))

  def updatePost(postId: UUID, text: String): IO[Unit] =
    posts.update(_.updatedWith(postId)(_.map(_.copy(text = text))))

  def deletePost(postId: UUID): IO[Unit] =
    posts.get.flatMap(_.get(postId).traverse_(row => userPostIds.update(_.removed(row.postId)))) *>
      posts.update(_.removed(postId))

  def postFeed(userId: UUID, offset: Int, limit: Int): IO[Chain[PostRow]] =
    (userPostIds.get, posts.get, friendsAccessor.getFriends(userId)).mapN {
      (userPostsSnapshot, postsSnapshot, friendsIds) =>
        val postFeed = friendsIds.foldLeft(SortedSet.empty[PostRow]) { case (acc, friend) =>
          val friendPosts: Set[PostRow] =
            userPostsSnapshot.getOrElse(friend.friendId, Set.empty[UUID]).flatMap(postsSnapshot.get)

          acc ++ friendPosts
        }

        Chain.fromIterableOnce(postFeed.slice(offset, offset + limit))
    }

  def userPosts(userId: UUID, fromIndex: Long): IO[Chain[PostRow]] =
    (userPostIds.get, posts.get).mapN { (userPostsSnapshot, postsSnapshot) =>
      val userPosts = userPostsSnapshot.getOrElse(userId, Set.empty[UUID]).flatMap(postsSnapshot.get)

      Chain.fromIterableOnce(userPosts.filter(_.index >= fromIndex)).sortBy(_.index)
    }

  def getPostsLog[R[_]: NonEmptyTraverse](userIds: R[UUID], lastIndex: Long, limit: Int): IO[Vector[PostRow]] =
    userIds.foldMapM(id => userPosts(id, lastIndex).map(_.toVector))

  def getLastPost(userId: UUID): OptionT[IO, PostRow] =
    OptionT {
      posts.get.map(_.get(userId))
    }
}
