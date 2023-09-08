package ru.nh.http

import ru.nh.{Id, Post, RegisterUserCommand, User}
import ru.nh.auth.AuthService.{Token, UserPassword}
import sttp.tapir.Schema

object tapirImplicits {
  implicit val errorResponseSchema: Schema[ErrorResponse]             = Schema.derived[ErrorResponse]
  implicit val userPassSchema: Schema[UserPassword]                   = Schema.derived[UserPassword]
  implicit val tokenSchema: Schema[Token]                             = Schema.derived[Token]
  implicit val userIdSchema: Schema[Id]                               = Schema.derived[Id]
  implicit val registerUserCommandSchema: Schema[RegisterUserCommand] = Schema.derived[RegisterUserCommand]
  implicit val userSchema: Schema[User]                               = Schema.derived[User]
  implicit val postSchema: Schema[Post]                               = Schema.derived[Post]
}
