package ru.nh.user.http

import ru.nh.auth.AuthService.{ Token, UserPassword }
import ru.nh.http.ErrorResponse
import ru.nh.user.http.UserEndpointDescriptions.{ PostCreate, PostUpdate }
import ru.nh.user.{ Id, Post, RegisterUserCommand, User }
import sttp.tapir.Schema

object tapirImplicits {
  implicit val errorResponseSchema: Schema[ErrorResponse]             = Schema.derived[ErrorResponse]
  implicit val userPassSchema: Schema[UserPassword]                   = Schema.derived[UserPassword]
  implicit val tokenSchema: Schema[Token]                             = Schema.derived[Token]
  implicit val userIdSchema: Schema[Id]                               = Schema.derived[Id]
  implicit val registerUserCommandSchema: Schema[RegisterUserCommand] = Schema.derived[RegisterUserCommand]
  implicit val userSchema: Schema[User]                               = Schema.derived[User]
  implicit val postCreateSchema: Schema[PostCreate]                   = Schema.derived[PostCreate]
  implicit val postUpdateSchema: Schema[PostUpdate]                   = Schema.derived[PostUpdate]
  implicit val postSchema: Schema[Post]                               = Schema.derived[Post]
}
