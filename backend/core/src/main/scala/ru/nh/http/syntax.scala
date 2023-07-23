package ru.nh.http

import cats.effect.IO
import org.typelevel.log4cats.Logger

object syntax extends TapirSyntax

trait TapirSyntax {
  implicit def IoToEitherOps[T](f: IO[T]): IOToEitherOps[T] = new IOToEitherOps(f)
}

final class IOToEitherOps[T](private val self: IO[T]) extends AnyVal {
  def toOut(implicit log: Logger[IO]): IO[Either[Throwable, T]] = self.attempt.flatTap {
    case Right(_) => IO.unit
    case Left(ex) => log.warn(ex)(s"Request failed with: [${ex.getClass.getSimpleName}] ${ex.getMessage}")
  }
}
