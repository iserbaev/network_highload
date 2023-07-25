package ru.nh.user.http

import ru.nh.auth.AuthService.{ Token, UserPassword }
import ru.nh.http.ErrorResponse
import ru.nh.user.{ RegisterUserCommand, User, UserId }
import sttp.tapir.Schema

object tapirImplicits {
  implicit val errorResponseSchema: Schema[ErrorResponse]             = Schema.derived[ErrorResponse]
  implicit val userPassSchema: Schema[UserPassword]                   = Schema.derived[UserPassword]
  implicit val tokenSchema: Schema[Token]                             = Schema.derived[Token]
  implicit val userIdSchema: Schema[UserId]                           = Schema.derived[UserId]
  implicit val registerUserCommandSchema: Schema[RegisterUserCommand] = Schema.derived[RegisterUserCommand]
  implicit val userSchema: Schema[User]                               = Schema.derived[User]
}
