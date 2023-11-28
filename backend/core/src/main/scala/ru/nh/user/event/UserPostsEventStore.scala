package ru.nh.user.event

import cats.NonEmptyTraverse
import cats.data.{ Chain, OptionT }
import cats.effect.IO
import ru.nh.events.ReadEventStore
import ru.nh.post.PostAccessor
import ru.nh.post.PostAccessor.PostRow

import java.time.Instant
import java.util.UUID

class UserPostsEventStore private (val accessor: PostAccessor[IO]) extends ReadEventStore[IO, UUID, PostRow] {
  def getLastEventLog(key: UUID): OptionT[IO, PostRow] =
    accessor.getLastPost(key)

  def getEventLogs(key: UUID): IO[Chain[PostRow]] =
    accessor.userPosts(key, 0)

  def getEventLogs[R[_]: NonEmptyTraverse](keys: R[UUID], lastModifiedAt: Instant, limit: Int): IO[Vector[PostRow]] =
    accessor.getPostsLog(keys, 0, limit)
}

object UserPostsEventStore {
  def apply(accessor: PostAccessor[IO]): UserPostsEventStore =
    new UserPostsEventStore(accessor)
}
