package ru.nh.user.http

import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.http.ErrorResponse
import ru.nh.user.{ UserId, UserService }
import sttp.model.StatusCode

class UserEndpoints(authService: AuthService[IO], userService: UserService)(implicit L: LoggerFactory[IO]) {

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[UserEndpoints])

  private val userEndpointDescriptions = new UserEndpointDescriptions(authService)

  val registerUser = userEndpointDescriptions.registerUser
    .serverLogic { cmd =>
      userService
        .register(cmd)
        .attempt
        .map {
          _.map(u => UserId(u.id))
            .leftMap {
              case _: IllegalArgumentException => (StatusCode.BadRequest, none)
              case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, "", 0).some)
            }
        }
    }

  val getUserProfile = userEndpointDescriptions.getUserProfile
    .serverLogic { auth => id =>
      userService
        .get(id)
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap {
            case Some(user) => user.toUserProfile(none, none).asRight
            case None       => (StatusCode.NotFound, none).asLeft
          }
        }
    }
}
