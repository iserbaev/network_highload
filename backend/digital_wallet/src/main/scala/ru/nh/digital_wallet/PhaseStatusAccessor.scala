package ru.nh.digital_wallet

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

trait PhaseStatusAccessor[F[_]] {
  def upsert(ps: PhaseStatus): F[Int]

  def setFromCompleted(transactionId: UUID, ts: Instant): F[Int]
  def setToCompleted(transactionId: UUID, ts: Instant): F[Int]

  def completePhase(transactionId: UUID): F[Int]
  def getNonCompletedPhases(ttl: FiniteDuration, limit: Int): F[Vector[PhaseStatus]]

  def getNonCompletedPhases(until: Instant, limit: Int): F[Vector[PhaseStatus]]
  def update(ps: PhaseStatus): F[Int]

  def getList(from: Instant, limit: Int): F[Vector[PhaseStatus]]

  def get(transactionId: UUID): F[PhaseStatus]

}
