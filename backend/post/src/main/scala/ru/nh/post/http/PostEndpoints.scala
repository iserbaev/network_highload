package ru.nh.post.http

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.http._
import ru.nh.user.UserClient
import ru.nh.{ Id, PostService }
import sttp.model.StatusCode

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

class PostEndpoints(
    authService: AuthService,
    postService: PostService,
    userClient: UserClient,
    val sseHeartbeatPeriod: FiniteDuration
)(
    implicit L: LoggerFactory[IO]
) extends SseSupport {
  import ru.nh.http.json.all._

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[PostEndpoints])

  private val userEndpointDescriptions = new PostEndpointDescriptions(authService)

  val addPost: SEndpoint = userEndpointDescriptions.addPost
    .serverLogic { auth => postCreate =>
      postService
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
      postService
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
      postService
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
      postService
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
    .serverLogicSuccess { auth => offsetLimit =>
      log
        .debug(s"Start http post feed for [${auth.userId}]") *>
        auth.userIdUUID.flatMap { id =>
          userClient
            .getFriends(id, auth.token)
            .map { friends =>
              fs2.Stream
                .resource(postService.postFeed(id, friends, offsetLimit._1, offsetLimit._2))
                .flatMap(_.stream)
                .through(ssePipe)
                .onFinalizeCase(ec => log.debug(s"Finalized http post feed [${auth.userId}], $ec"))
            }

        }

    }

  val postFeedPosted: SEndpoint = userEndpointDescriptions.postFeedPosted
    .serverLogicSuccess { auth => _ =>
      log
        .debug(s"Start http post feed posted for [${auth.userId}]") *>
        auth.userIdUUID.flatMap { id =>
          userClient
            .getFriends(id, auth.token)
            .map { friends =>
              fs2.Stream
                .resource(postService.postFeedPosted(id, friends))
                .flatMap(_.stream)
                .through(ssePipe)
                .onFinalizeCase(ec => log.debug(s"Finalized http post feed posted [${auth.userId}], $ec"))
            }

        }

    }

  val all: NonEmptyList[SEndpoint] = NonEmptyList.of(
    addPost,
    updatePost,
    deletePost,
    getPost,
    postFeed,
    postFeedPosted
  )
}
