package ru.nh.http

import cats.effect.IO
import cats.syntax.all._
import fs2.{ Pipe, Stream }
import io.circe.Encoder
import io.circe.syntax._
import sttp.model.sse.ServerSentEvent

import scala.concurrent.duration.FiniteDuration

trait SseSupport {
  protected def sseHeartbeatPeriod: FiniteDuration
  protected def sseHeartbeatEvent: IO[ServerSentEvent] = IO {
    ServerSentEvent(eventType = "heartbeat".some)
  }

  protected def ssePipe[A: Encoder.AsRoot]: Pipe[IO, A, ServerSentEvent] = { in =>
    in.map { a =>
      ServerSentEvent(data = a.asJson.toString().some, eventType = "data".some)
    }.mergeHaltL {
      Stream.eval(sseHeartbeatEvent).repeat.metered(sseHeartbeatPeriod)
    }
  }

}
