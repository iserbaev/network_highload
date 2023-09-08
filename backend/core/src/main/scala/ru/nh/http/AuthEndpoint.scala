package ru.nh.http

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService
import ru.nh.auth.AuthService.{ Token, UserPassword }
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class AuthEndpoint(val authService: AuthService)(implicit L: LoggerFactory[IO]) {
  import ru.nh.http.json.all._
  import tapirImplicits._

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[AuthEndpoint])

  val resource: String                  = "login"
  val resourcePath: EndpointInput[Unit] = resource

  private val loginEndpointDescription =
    endpoint
      .in(resourcePath)
      .tag(resource)
      .post
      .errorOut(statusCode)
      .errorOut(jsonBody[Option[ErrorResponse]])
      .in(jsonBody[UserPassword])
      .out(jsonBody[Token])

  val loginEndpoint: SEndpoint = loginEndpointDescription.serverLogic { login =>
    authService
      .login(login.id, login.password)
      .attempt
      .map {
        _.leftMap {
          case _: IllegalArgumentException => (StatusCode.BadRequest, none)
          case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, login.id, 0).some)
        }.flatMap {
          case Some(token) => token.asRight[(StatusCode, Option[ErrorResponse])]
          case None        => (StatusCode.NotFound, none).asLeft[Token]
        }
      }
  }

  val all = NonEmptyList.of(loginEndpoint)
}
