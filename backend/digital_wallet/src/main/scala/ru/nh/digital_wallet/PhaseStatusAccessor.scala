package ru.nh.digital_wallet

import java.time.Instant
import java.util.UUID

trait PhaseStatusAccessor[F[_]] {
  def upsert(ps: PhaseStatus): F[Int]

  def setFromCompleted(transactionId: UUID, ts: Instant): F[Int]
  def setToCompleted(transactionId: UUID, ts: Instant): F[Int]
  def update(ps: PhaseStatus): F[Int]

  def getList(from: Instant, limit: Int): F[Vector[PhaseStatus]]

  def get(transactionId: UUID): F[PhaseStatus]

}
