package ru.nh.http

import cats.data.NonEmptyList
import cats.effect.{ IO, Resource }
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server
import org.http4s.server.defaults.{ IdleTimeout, ResponseTimeout }
import org.typelevel.log4cats.LoggerFactory
import ru.nh.http.HttpModule.Config
import ru.nh.metrics.MetricsModule
import sttp.tapir.server.http4s.{ Http4sServerInterpreter, Http4sServerOptions }
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.duration._

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

  def resource(
      cfg: Config,
      endpoints: NonEmptyList[SEndpoint],
      metricsModule: MetricsModule,
      title: String,
      healthCheck: IO[Unit]
  )(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, HttpModule] = {
    val log = L.getLoggerFromClass(classOf[HttpModule])

    val logic   = endpoints
    val swagger = swaggerEndpoints(logic, title ++ " endpoints", "0.0.1")

    val serverInterpreter = Http4sServerInterpreter[IO] {
      Http4sServerOptions
        .customiseInterceptors[IO]
        .metricsInterceptor(metricsModule.httpInterceptor)
        .options
    }
    val serverRoutes = serverInterpreter.toRoutes(
      healthCheckRoute(healthCheck, 10.seconds) :: metricsModule.pullEndpoint :: swagger ::: logic.toList
    )

    val idleTimeout           = cfg.server.idleTimeout.getOrElse(IdleTimeout)
    val responseHeaderTimeout = cfg.server.responseHeaderTimeout.getOrElse(ResponseTimeout)

    BlazeServerBuilder[IO]
      .bindHttp(cfg.server.port, cfg.server.host)
      .withIdleTimeout(FiniteDuration(idleTimeout.length, idleTimeout.unit))
      .withSocketKeepAlive(cfg.server.useKeepAlive)
      .withResponseHeaderTimeout(responseHeaderTimeout)
      .withHttpApp(serverRoutes.orNotFound)
      .resource
      .evalTap(_ => log.info(s"Run http $title server on ${cfg.server.host}:${cfg.server.port}"))
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
