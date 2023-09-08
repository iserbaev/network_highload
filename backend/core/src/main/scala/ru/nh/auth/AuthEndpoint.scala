package ru.nh.auth

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.auth.AuthService.{ Auth, Token, UserPassword }
import ru.nh.http.{ ErrorResponse, SEndpoint, baseEndpoint, tapirImplicits }
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class AuthEndpoint(val authService: AuthService)(implicit L: LoggerFactory[IO]) {
  import ru.nh.http.json.all._
  import tapirImplicits._

  implicit val log: Logger[IO] = L.getLoggerFromClass(classOf[AuthEndpoint])

  implicit val authSchema: Schema[Auth] = Schema.derived[Auth]

  val resource: String                  = "auth"
  val resourcePath: EndpointInput[Unit] = resource

  private val base = baseEndpoint(resource, resourcePath)

  private val saveEndpointDescription =
    base.post
      .in("save")
      .in(path[String]("key"))
      .description("save user creds")
      .in(jsonBody[UserPassword])
      .out(statusCode)

  private val loginEndpointDescription =
    base.post
      .in("login")
      .description("Login user")
      .in(jsonBody[UserPassword])
      .out(jsonBody[Token])

  private val authorizeEndpointDescription =
    base.post
      .in("authorize")
      .description("Authorize user")
      .in(jsonBody[Token])
      .out(jsonBody[AuthService.Auth])

  val saveEndpoint: SEndpoint = saveEndpointDescription.serverLogic { login =>
    authService
      .save(login._2.id, login._2.password, login._1)
      .attempt
      .map {
        _.leftMap {
          case _: IllegalArgumentException => (StatusCode.BadRequest, none)
          case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, login._2.id, 0).some)
        }.as(StatusCode.Ok)
      }
  }

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

  val authorizeEndpoint: SEndpoint = authorizeEndpointDescription.serverLogic { token =>
    authService
      .authorize(token.token)
      .attempt
      .map {
        _.leftMap {
          case _: IllegalArgumentException => (StatusCode.BadRequest, none)
          case ex => (StatusCode.InternalServerError, ErrorResponse(ex.getMessage, token.token, 0).some)
        }.flatMap {
          case Some(auth) => auth.asRight[(StatusCode, Option[ErrorResponse])]
          case None       => (StatusCode.NotFound, none).asLeft[Auth]
        }
      }
  }

  val all = NonEmptyList.of(saveEndpoint, loginEndpoint, authorizeEndpoint)
}
