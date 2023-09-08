package ru.nh.auth.inmemory

import cats.effect.{ IO, Ref, Resource }
import ru.nh.auth.LoginAccessor
import ru.nh.auth.LoginAccessor.LoginRow

class InMemoryLoginAccessor private (ref: Ref[IO, Map[String, LoginRow]]) extends LoginAccessor[IO] {
  def save(login: String, password: String): IO[Unit] =
    IO.realTimeInstant
      .flatMap(now => ref.update(_.updated(login, LoginRow(login, password, now))))
      .void

  def get(login: String): IO[Option[LoginAccessor.LoginRow]] =
    ref.get.map(_.get(login))
}

object InMemoryLoginAccessor {
  def resource(): Resource[IO, LoginAccessor[IO]] =
    Resource.eval(IO.ref(Map.empty[String, LoginRow]).map(ref => new InMemoryLoginAccessor(ref)))
}
