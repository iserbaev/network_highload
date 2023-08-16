package ru.nh.user.http

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.http.ErrorResponse
import ru.nh.user.{ Id, UserService }
import sttp.model.StatusCode

import java.util.UUID

class UserEndpoints(authService: AuthService, userService: UserService)(implicit L: LoggerFactory[IO]) {

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[UserEndpoints])

  private val userEndpointDescriptions = new UserEndpointDescriptions(authService)

  val registerUser: SEndpoint = userEndpointDescriptions.registerUser
    .serverLogic { cmd =>
      userService
        .register(cmd)
        .attempt
        .map {
          _.map(u => Id(u.id))
            .leftMap {
              case _: IllegalArgumentException => (StatusCode.BadRequest, none)
              case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, "", 0).some)
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
    .serverLogic { auth => firstNamePrefixAndLastNamePrefix =>
      userService
        .search(firstNamePrefixAndLastNamePrefix._1, firstNamePrefixAndLastNamePrefix._2)
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

  val addFriend: SEndpoint = userEndpointDescriptions.addFriend
    .serverLogic { auth => id =>
      userService
        .addFriend(UUID.fromString(auth.userId), id)
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

  val deleteFriend: SEndpoint = userEndpointDescriptions.deleteFriend
    .serverLogic { auth => id =>
      userService
        .deleteFriend(UUID.fromString(auth.userId), id)
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

  val addPost: SEndpoint = userEndpointDescriptions.addPost
    .serverLogic { auth => postCreate =>
      userService
        .addPost(UUID.fromString(auth.userId), postCreate.text)
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap { id =>
            Id(id).asRight
          }
        }
    }

  val updatePost: SEndpoint = userEndpointDescriptions.updatePost
    .serverLogic { auth => postUpdate =>
      userService
        .updatePost(postUpdate.id, postUpdate.text)
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

  val deletePost: SEndpoint = userEndpointDescriptions.deletePost
    .serverLogic { auth => id =>
      userService
        .deletePost(id)
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

  val getPost: SEndpoint = userEndpointDescriptions.getPost
    .serverLogic { auth => id =>
      userService
        .getPost(id)
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap {
            case Some(post) => post.asRight
            case None       => (StatusCode.NotFound, none).asLeft
          }
        }
    }

  val postFeed: SEndpoint = userEndpointDescriptions.postFeed
    .serverLogic { auth => id =>
      userService
        .postFeed(UUID.fromString(auth.userId), id._1, id._2)
        .use(_.stream.compile.toList)
        .attempt
        .map {
          _.leftMap {
            case _: IllegalArgumentException => (StatusCode.BadRequest, none)
            case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, auth.userId, 0).some)
          }.flatMap { feed =>
            feed.asRight
          }
        }
    }

  val all = NonEmptyList.of(
    registerUser,
    getUserProfile,
    searchUserProfile,
    addFriend,
    deleteFriend,
    addPost,
    updatePost,
    deletePost,
    getPost,
    postFeed
  )
}
