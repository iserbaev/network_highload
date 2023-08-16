package ru.nh.user.inmemory

import cats.data.{ Chain, OptionT }
import cats.effect.std.UUIDGen
import cats.effect.{ IO, Ref }
import cats.syntax.all._
import cats.{ Functor, NonEmptyTraverse, Reducible }
import ru.nh.user.UserAccessor.{ PostRow, UserRow }
import ru.nh.user.{ RegisterUserCommand, User, UserAccessor }
import fs2.Stream

import java.util.UUID
import scala.collection.SortedSet

class InMemoryUserAccessor(
    users: Ref[IO, Map[UUID, UserRow]],
    hobbies: Ref[IO, Map[UUID, List[String]]],
    friends: Ref[IO, Map[UUID, Set[UUID]]],
    posts: Ref[IO, Map[UUID, PostRow]],
    userPostIds: Ref[IO, Map[UUID, Set[UUID]]],
    counter: Ref[IO, Long]
) extends UserAccessor[IO] {
  def save(u: RegisterUserCommand): IO[UserRow] =
    (IO.realTimeInstant, IO.randomUUID).flatMapN { (now, id) =>
      val userRow = UserRow(id, now, u.name, u.surname, u.age, u.city, u.password, u.gender, u.biography, u.birthdate)
      (users.update(_.updated(id, userRow)), hobbies.update(_.updated(id, u.hobbies))).tupled.as(userRow)
    }

  def saveBatch[R[_]: Reducible: Functor](u: R[RegisterUserCommand]): IO[Unit] =
    u.traverse_(save)

  def getUserRow(userId: UUID): IO[Option[UserRow]] =
    users.get.map(_.get(userId))

  def getUser(userId: UUID): IO[Option[User]] =
    getUserRow(userId).flatMap(_.traverse(row => getHobbies(userId).map(row.toUser)))

  def getHobbies(userId: UUID): IO[List[String]] =
    hobbies.get.map(_.getOrElse(userId, List.empty))

  def search(firstNamePrefix: String, lastNamePrefix: String): IO[Option[UserRow]] =
    users.get.map(
      _.find { case (_, row) => row.name.contains(firstNamePrefix) && row.surname.contains(lastNamePrefix) }.map(_._2)
    )

  def addFriend(userId: UUID, friendId: UUID): IO[Unit] =
    friends.update(_.updatedWith(userId)(_.map(_ + friendId).orElse(Set(friendId).some))).void

  def deleteFriend(userId: UUID, friendId: UUID): IO[Unit] =
    friends.update(_.updatedWith(userId)(_.map(_ - friendId))).void

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
    (friends.get, userPostIds.get, posts.get).mapN { (friendsSnapshot, userPostsSnapshot, postsSnapshot) =>
      val friendsIds = friendsSnapshot.getOrElse(userId, Set.empty)
      val postFeed = friendsIds.foldLeft(SortedSet.empty[PostRow]) { case (acc, friendId) =>
        val friendPosts: Set[PostRow] =
          userPostsSnapshot.getOrElse(friendId, Set.empty[UUID]).flatMap(postsSnapshot.get)

        acc ++ friendPosts
      }

      Chain.fromIterableOnce(postFeed.slice(offset, offset + limit))
    }

  def userPosts(userId: UUID, fromIndex: Long): IO[Chain[PostRow]] =
    (userPostIds.get, posts.get).mapN { (userPostsSnapshot, postsSnapshot) =>
      val userPosts = userPostsSnapshot.getOrElse(userId, Set.empty[UUID]).flatMap(postsSnapshot.get)

      Chain.fromIterableOnce(userPosts.filter(_.index >= fromIndex)).sortBy(_.index)
    }

  def getPostsLog[R[_]: NonEmptyTraverse](userIds: R[UUID], lastIndex: Long): Stream[IO, PostRow] =
    Stream.evalSeq(IO(userIds.toList)).flatMap(id => Stream.evalSeq(userPosts(id, lastIndex)))

  def getLastPost(userId: UUID): OptionT[IO, PostRow] =
    OptionT {
      posts.get.map(_.get(userId))
    }
}
