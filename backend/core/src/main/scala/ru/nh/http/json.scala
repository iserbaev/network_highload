package ru.nh.http

import io.circe._
import io.circe.generic.semiauto._
import ru.nh._
import ru.nh.auth.AuthService.{ Auth, Token, UserPassword }

object json {
  object all extends PostJsonImplicits with UserJsonImplicits with ConversationJsonImplicits with CommonImplicits

  object user extends UserJsonImplicits

}

trait CommonImplicits {
  implicit val errorResponseDecoder: Decoder[ErrorResponse]          = deriveDecoder[ErrorResponse]
  implicit val errorResponseEncoder: Encoder.AsObject[ErrorResponse] = deriveEncoder[ErrorResponse]

  implicit val userPassDecoder: Decoder[UserPassword]          = deriveDecoder[UserPassword]
  implicit val userPassEncoder: Encoder.AsObject[UserPassword] = deriveEncoder[UserPassword]

  implicit val tokenDecoder: Decoder[Token]          = deriveDecoder[Token]
  implicit val tokenEncoder: Encoder.AsObject[Token] = deriveEncoder[Token]

  implicit val authDecoder: Decoder[Auth]          = deriveDecoder[Auth]
  implicit val authEncoder: Encoder.AsObject[Auth] = deriveEncoder[Auth]
}

trait UserJsonImplicits {
  implicit val userIdDecoder: Decoder[Id]          = deriveDecoder[Id]
  implicit val userIdEncoder: Encoder.AsObject[Id] = deriveEncoder[Id]

  implicit val userRegisterDecoder: Decoder[RegisterUserCommand]          = deriveDecoder[RegisterUserCommand]
  implicit val userRegisterEncoder: Encoder.AsObject[RegisterUserCommand] = deriveEncoder[RegisterUserCommand]

  implicit val userDecoder: Decoder[User]          = deriveDecoder[User]
  implicit val userEncoder: Encoder.AsObject[User] = deriveEncoder[User]
}

trait PostJsonImplicits {
  implicit val postDecoder: Decoder[Post]          = deriveDecoder[Post]
  implicit val postEncoder: Encoder.AsObject[Post] = deriveEncoder[Post]
}

trait ConversationJsonImplicits {
  implicit val cDecoder: Decoder[Conversation]          = deriveDecoder[Conversation]
  implicit val cEncoder: Encoder.AsObject[Conversation] = deriveEncoder[Conversation]

  implicit val pmDecoder: Decoder[PrivateMessage]          = deriveDecoder[PrivateMessage]
  implicit val pmEncoder: Encoder.AsObject[PrivateMessage] = deriveEncoder[PrivateMessage]
}
