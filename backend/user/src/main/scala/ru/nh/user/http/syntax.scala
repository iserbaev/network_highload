package ru.nh.user.http

import cats.effect.IO
import cats.syntax.all._
import ru.nh.auth.AuthService.Auth
import ru.nh.http.ErrorResponse
import sttp.model.StatusCode

object syntax extends TapirSyntax

trait TapirSyntax {
  implicit def IoToEitherOps[T](f: IO[T]): IOToEitherOps[T] = new IOToEitherOps(f)
}

final class IOToEitherOps[T](private val self: IO[T]) extends AnyVal {
  def toOut(auth: Auth): IO[Either[(StatusCode, Option[ErrorResponse]), T]] = self.attempt
    .map {
      _.leftMap {
        case _: IllegalArgumentException => (StatusCode.BadRequest, none[ErrorResponse])
        case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
      }
    }
}
