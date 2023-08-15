package ru.nh

import cats.effect.IO
import cats.effect.std.Dispatcher

import java.util.concurrent.CompletableFuture

package object cache {
  private[cache] def fromCF[A](cf: => CompletableFuture[A]): IO[A] =
    IO.fromCompletableFuture(IO.delay(cf))

  private[cache] def toCF[A](io: IO[A], dispatcher: Dispatcher[IO]): CompletableFuture[A] =
    dispatcher.unsafeToCompletableFuture(io)
}
