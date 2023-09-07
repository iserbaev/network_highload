package ru.nh.user.http

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all._
import io.circe.syntax._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.http._
import ru.nh.user.{ Id, PostService }
import sttp.model.StatusCode

import java.util.UUID

class PostEndpoints(authService: AuthService, postService: PostService)(
    implicit L: LoggerFactory[IO]
) {
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
        .debug(s"Start http post feed for [${auth.userId}]")
        .as {
          fs2.Stream
            .resource(postService.postFeed(UUID.fromString(auth.userId), offsetLimit._1, offsetLimit._2))
            .flatMap(_.stream)
            .map(_.asJson.toString())
            .through(fs2.text.utf8.encode)
            .onFinalizeCase(ec => log.debug(s"Finalized http post feed [${auth.userId}], $ec"))
        }
    }

  val all: NonEmptyList[SEndpoint] = NonEmptyList.of(
    addPost,
    updatePost,
    deletePost,
    getPost,
    postFeed
  )
}
