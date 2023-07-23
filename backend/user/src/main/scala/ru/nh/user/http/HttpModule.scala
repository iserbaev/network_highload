package ru.nh.user.http

import scala.concurrent.duration.Duration

trait HttpModule {}

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
  final case class Config(auth: AuthConfig, server: HttpServerConfig)

}
