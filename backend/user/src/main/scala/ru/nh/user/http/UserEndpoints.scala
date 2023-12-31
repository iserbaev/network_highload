package ru.nh.user.http

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.http._
import ru.nh.{ Friends, Id, UserService }
import sttp.model.StatusCode

import java.util.UUID
class UserEndpoints(authService: AuthService, userService: UserService, appKey: String)(
    implicit L: LoggerFactory[IO]
) {

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[UserEndpoints])

  private val userEndpointDescriptions = new UserEndpointDescriptions(authService)

  val registerUser: SEndpoint = userEndpointDescriptions.registerUser
    .serverLogic { cmd =>
      userService
        .register(cmd)
        .flatTap { u =>
          authService.save(u.id.toString, cmd.password, appKey)
        }
        .attempt
        .map {
          _.map { u =>
            Id(u.id)
          }
            .leftMap {
              case _: IllegalArgumentException =>
                (StatusCode.BadRequest, none)
              case ex =>
                (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, "", 0).some)
            }
        }
    }

  val getUserProfile: SEndpoint = userEndpointDescriptions.getUserProfile
    .serverLogic { auth => id =>
      userService
        .get(id)
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap {
            case Some(user) => user.asRight
            case None       => (StatusCode.NotFound, none).asLeft
          }
        }
    }

  val searchUserProfile: SEndpoint = userEndpointDescriptions.searchUserProfile
    .serverLogic { auth => params =>
      userService
        .search(params.firstNamePrefix, params.lastNamePrefix, params.limit)
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap {
            case Nil   => (StatusCode.NotFound, none).asLeft
            case users => users.asRight
          }
        }
    }

  val addFriend: SEndpoint = userEndpointDescriptions.addFriend
    .serverLogic { auth => id =>
      userService
        .addFriends(Friends(UUID.fromString(auth.userId), id))
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap { _ =>
            StatusCode.Ok.asRight
          }
        }
    }

  val getFriends: SEndpoint = userEndpointDescriptions.getFriends
    .serverLogic { auth => id =>
      userService
        .getFriends(id)
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap(_.map(_.friendId).asRight)
        }
    }

  val deleteFriend: SEndpoint = userEndpointDescriptions.deleteFriend
    .serverLogic { auth => id =>
      userService
        .deleteFriend(Friends(UUID.fromString(auth.userId), id))
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap { _ =>
            StatusCode.Ok.asRight
          }
        }
    }

  val all: NonEmptyList[SEndpoint] = NonEmptyList.of(
    registerUser,
    getUserProfile,
    searchUserProfile,
    addFriend,
    deleteFriend,
    getFriends
  )
}
