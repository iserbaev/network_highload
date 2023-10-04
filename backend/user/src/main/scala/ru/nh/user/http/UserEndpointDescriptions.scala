package ru.nh.user.http

import cats.effect.IO
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.http._
import ru.nh.user.http.UserEndpointDescriptions.SearchUserProfileParams
import ru.nh.{ Id, RegisterUserCommand, User }
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class UserEndpointDescriptions(val authService: AuthService)(implicit L: LoggerFactory[IO]) {
  import ru.nh.http.json.all._
  import tapirImplicits._

  val resource: String                  = "user"
  val resourcePath: EndpointInput[Unit] = resource

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[UserEndpointDescriptions])

  private val base    = baseEndpoint(resource, resourcePath)
  private val secured = securedEndpoint(resource, resourcePath, authService)

  val registerUser: BaseEndpoint[RegisterUserCommand, Id] =
    base.post
      .in("register")
      .description("Register user")
      .in(jsonBody[RegisterUserCommand])
      .out(jsonBody[Id])

  val getUserProfile: SecuredEndpoint[UUID, User] =
    secured.get
      .in("get")
      .in(path[UUID]("id"))
      .out(jsonBody[User])

  val searchUserProfile: SecuredEndpoint[SearchUserProfileParams, List[User]] =
    secured.get
      .in("search")
      .in(path[Int]("limit"))
      .in(query[String]("first_name").and(query[String]("last_name")))
      .mapInTo[SearchUserProfileParams]
      .out(jsonBody[List[User]])

  val addFriend: SecuredEndpoint[UUID, StatusCode] =
    secured.put
      .in("friend" / "set")
      .in(path[UUID]("user_id"))
      .out(
        statusCode
          .description(StatusCode.Ok, "Пользователь успешно указал своего друга")
      )

  val getFriends: SecuredEndpoint[UUID, List[UUID]] =
    secured.get
      .in("friend" / "get")
      .in(path[UUID]("id"))
      .out(jsonBody[List[UUID]])

  val deleteFriend: SecuredEndpoint[UUID, StatusCode] =
    secured.put
      .in("friend" / "delete")
      .in(path[UUID]("user_id"))
      .out(statusCode.description(StatusCode.Ok, "Пользователь успешно удалил из друзей пользователя"))

}

object UserEndpointDescriptions {
  final case class SearchUserProfileParams(limit: Int, firstNamePrefix: String, lastNamePrefix: String)
}
