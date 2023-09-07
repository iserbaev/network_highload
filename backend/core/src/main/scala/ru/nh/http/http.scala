package ru.nh

import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.Header
import sttp.model.headers.CacheDirective
import sttp.tapir.EndpointIO.FixedHeader
import sttp.tapir.header
import sttp.tapir.server.ServerEndpoint

package object http {
  type SEndpoint = ServerEndpoint[Fs2Streams[IO], IO]

  val NoCacheControlHeader: FixedHeader[Unit]  = header(Header.cacheControl(CacheDirective.NoCache))
  val XAccelBufferingHeader: FixedHeader[Unit] = header("X-Accel-Buffering", "no")
}