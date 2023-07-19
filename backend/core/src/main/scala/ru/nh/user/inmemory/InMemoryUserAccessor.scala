package ru.nh.user.inmemory

import cats.effect.{ IO, Ref }
import ru.nh.user.{ RegisterUserCommand, User, UserAccessor }

import java.util.UUID

class InMemoryUserAccessor(ref: Ref[IO, Map[UUID, User]]) extends UserAccessor {
  def save(u: RegisterUserCommand): IO[User] =
    IO.randomUUID.flatMap { id =>
      ref
        .update(_.updated(id, u.toUser(id)))
        .as(u.toUser(id))
    }

  def get(userId: UUID): IO[Option[User]] =
    ref.get.map(_.get(userId))
}
