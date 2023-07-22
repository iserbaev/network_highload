package ru.nh.db.doobie

import cats.arrow.FunctionK
import cats.effect.IO
import cats.~>
import doobie._
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

trait DoobieTransformations {
  self: RetrySupport =>

  def doobie2IO(xa: Transactor[IO], retryCount: Int, baseInterval: FiniteDuration)(
      retryWhen: PartialFunction[Throwable, IO[Boolean]]
  )(
      implicit logger: Logger[IO]
  ): ConnectionIO ~> IO = new FunctionK[ConnectionIO, IO] {
    def apply[A](fa: ConnectionIO[A]): IO[A] =
      retryConnectionIO(fa)(xa, retryCount, baseInterval)(retryWhen)
  }

  def doobie2IODefaultRetry(xa: Transactor[IO], retryCount: Int, baseInterval: FiniteDuration)(
      implicit logger: Logger[IO]
  ): ConnectionIO ~> IO = new FunctionK[ConnectionIO, IO] {
    def apply[A](fa: ConnectionIO[A]): IO[A] =
      retryConnectionIO(fa)(xa, retryCount, baseInterval)(defaultTransactionRetryCondition)
  }

}
