package ru.nh.user

import cats.~>
import ru.nh.Friends
import ru.nh.user.FriendsAccessor.FriendsAccessorMapK

import java.util.UUID

trait FriendsAccessor[F[_]] {
  def addFriends(f: Friends): F[Unit]
  def deleteFriend(f: Friends): F[Unit]
  def getFriends(userId: UUID): F[List[Friends]]

  def mapK[G[_]](read: F ~> G, write: F ~> G): FriendsAccessor[G] =
    new FriendsAccessorMapK(this, read, write)
}

object FriendsAccessor {

  private[user] final class FriendsAccessorMapK[F[_], G[_]](underlying: FriendsAccessor[F], read: F ~> G, write: F ~> G)
      extends FriendsAccessor[G] {
    def addFriends(f: Friends): G[Unit] =
      write(underlying.addFriends(f))

    def deleteFriend(f: Friends): G[Unit] =
      write(underlying.deleteFriend(f))

    def getFriends(userId: UUID): G[List[Friends]] =
      read(underlying.getFriends(userId))
  }
}
