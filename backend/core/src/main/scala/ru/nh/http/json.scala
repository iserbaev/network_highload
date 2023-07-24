package ru.nh.http

import io.circe._
import io.circe.generic.semiauto._
import ru.nh.auth.AuthService.{ Token, UserPassword }
import ru.nh.user.{ RegisterUserCommand, User, UserId, UserProfile }

object json {
  object all extends UserJsonImplicits with CommonImplicits

  object user extends UserJsonImplicits

}

trait CommonImplicits {
  implicit val errorResponseDecoder: Decoder[ErrorResponse]          = deriveDecoder[ErrorResponse]
  implicit val errorResponseEncoder: Encoder.AsObject[ErrorResponse] = deriveEncoder[ErrorResponse]

  implicit val userPassDecoder: Decoder[UserPassword]          = deriveDecoder[UserPassword]
  implicit val userPassEncoder: Encoder.AsObject[UserPassword] = deriveEncoder[UserPassword]

  implicit val tokenDecoder: Decoder[Token]          = deriveDecoder[Token]
  implicit val tokenEncoder: Encoder.AsObject[Token] = deriveEncoder[Token]
}

trait UserJsonImplicits {
  implicit val userIdDecoder: Decoder[UserId]          = deriveDecoder[UserId]
  implicit val userIdEncoder: Encoder.AsObject[UserId] = deriveEncoder[UserId]

  implicit val userRegisterDecoder: Decoder[RegisterUserCommand]          = deriveDecoder[RegisterUserCommand]
  implicit val userRegisterEncoder: Encoder.AsObject[RegisterUserCommand] = deriveEncoder[RegisterUserCommand]

  implicit val userDecoder: Decoder[User]          = deriveDecoder[User]
  implicit val userEncoder: Encoder.AsObject[User] = deriveEncoder[User]

  implicit val userProfileDecoder: Decoder[UserProfile]          = deriveDecoder[UserProfile]
  implicit val userProfileEncoder: Encoder.AsObject[UserProfile] = deriveEncoder[UserProfile]
}