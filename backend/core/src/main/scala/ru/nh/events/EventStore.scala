package ru.nh.events

import cats.NonEmptyTraverse
import cats.data.{ Chain, OptionT }
import cats.effect.IO

import java.time.Instant

trait ReadEventStore[F[_], K, E] {
  def getLastEventLog(key: K): OptionT[F, E]
  def getEventLogs(key: K): F[Chain[E]]
  def getEventLogs[R[_]: NonEmptyTraverse](keys: R[K], from: Instant, limit: Int): F[Vector[E]]
}

trait ReadWriteEventStore[F[_], A, K, E] extends ReadEventStore[F, K, E] {
  def recordValueToEventLog(key: K, value: A): OptionT[IO, E]
  def recordValuesBatch[R[_]: NonEmptyTraverse](values: R[(K, A)]): IO[Vector[E]]
}
