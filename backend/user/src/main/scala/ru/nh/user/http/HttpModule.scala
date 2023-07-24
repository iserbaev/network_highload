package ru.nh.user.http

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.ChannelOption
import org.http4s.HttpRoutes
import org.http4s.netty.NettyChannelOptions
import org.http4s.netty.server.NettyServerBuilder
import org.http4s.server.Server
import org.http4s.server.defaults.{IdleTimeout, ResponseTimeout}
import org.typelevel.log4cats.LoggerFactory
import ru.nh.auth.AuthService
import ru.nh.user.UserModule
import ru.nh.user.http.HttpModule.Config
import ru.nh.user.metrics.MetricsModule
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.duration.{Duration, FiniteDuration}

trait HttpModule {
  def config: Config
  def routes: HttpRoutes[IO]
  def server: Server

}

object HttpModule {
  final case class AuthConfig(user: String, roles: List[String])
  final case class HttpServerConfig(
      host: String,
      port: Int,
      idleTimeout: Option[Duration],
      responseHeaderTimeout: Option[Duration],
      useBanner: Option[Seq[String]],
      useGzip: Boolean,
      useKeepAlive: Boolean,
      useNettyBackend: Boolean
  )
  final case class Config(server: HttpServerConfig, auth: AuthConfig)

  def resource(cfg: Config, userModule: UserModule, metricsModule: MetricsModule)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, HttpModule] =
    AuthService(userModule.accessor).flatMap { authService =>
      val authEndpoints = new AuthEndpoint(authService)
      val userEndpoint  = new UserEndpoints(authService, userModule.service)

      val logic =
        authEndpoints.all ::: userEndpoint.all
      val swagger = swaggerEndpoints(logic, "network-highload", "0.0.1")

      val serverInterpreter = Http4sServerInterpreter[IO] {
        Http4sServerOptions
          .customiseInterceptors[IO]
          .metricsInterceptor(metricsModule.httpInterceptor)
          .options
      }
      val serverRoutes = serverInterpreter.toRoutes(
        metricsModule.pullEndpoint :: swagger ::: logic.toList
      )

      val idleTimeout           = cfg.server.idleTimeout.getOrElse(IdleTimeout)
      val responseHeaderTimeout = cfg.server.responseHeaderTimeout.getOrElse(ResponseTimeout)

      NettyServerBuilder[IO]
        .bindHttp(cfg.server.port, cfg.server.host)
        .withIdleTimeout(FiniteDuration(idleTimeout.length, idleTimeout.unit))
        .withNettyChannelOptions(
          NettyChannelOptions.empty
            .append[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, responseHeaderTimeout.toMillis.toInt)
            .append[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, cfg.server.useKeepAlive)
        )
        .withHttpApp(serverRoutes.orNotFound)
        .resource
        .map { srv =>
          new HttpModule {
            override def config: Config = cfg

            override def routes: HttpRoutes[IO] = serverRoutes

            override def server: Server = srv
          }
        }

    }

  val DefaultOptions: SwaggerUIOptions = SwaggerUIOptions.default.copy(pathPrefix = List("docz"))
  def swaggerEndpoints(
      routes: NonEmptyList[SEndpoint],
      title: String,
      version: String,
  ): List[SEndpoint] =
    SwaggerInterpreter(swaggerUIOptions = DefaultOptions)
      .fromServerEndpoints[IO](routes.toList, title, version)

}
