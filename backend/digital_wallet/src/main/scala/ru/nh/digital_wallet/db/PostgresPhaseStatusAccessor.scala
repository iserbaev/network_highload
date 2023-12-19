package ru.nh.digital_wallet.db

import cats.effect.{ IO, Resource }
import doobie.implicits._
import doobie.postgres.implicits._
import ru.nh.db.transactors.ReadWriteTransactors
import ru.nh.digital_wallet.{ PhaseStatus, PhaseStatusAccessor }

import java.time.Instant
import java.util.UUID

class PostgresPhaseStatusAccessor private (rw: ReadWriteTransactors[IO]) extends PhaseStatusAccessor[IO] {
  def upsert(ps: PhaseStatus): IO[Int] =
    sql"""|INSERT INTO phase_status(transaction_id, from_completed, from_transfer_ts, to_completed, to_transfer_ts, created_at)
          |VALUES (${ps.transactionId}, ${ps.fromTransferCompleted}, ${ps.fromTransferCreatedAt}, ${ps.toTransferCompleted}, ${ps.toTransferCreatedAt}, ${ps.createdAt})
          |ON CONFLICT (transaction_id)
          |    DO UPDATE
          |    SET from_completed = ${ps.fromTransferCompleted},
          |        from_transfer_ts = ${ps.fromTransferCreatedAt},
          |        to_completed = ${ps.toTransferCompleted},
          |        to_transfer_ts = ${ps.toTransferCreatedAt}
          |    WHERE phase_status.created_at < ${ps.createdAt}
          """.stripMargin.update.run
      .transact(rw.writeXA.xa)

  def setFromCompleted(transactionId: UUID, ts: Instant): IO[Int] =
    sql"""|UPDATE phase_status
          |   SET from_completed = true,
          |       from_transfer_ts = $ts
          |WHERE transaction_id = $transactionId
          """.stripMargin.update.run
      .transact(rw.writeXA.xa)

  def setToCompleted(transactionId: UUID, ts: Instant): IO[Int] =
    sql"""|UPDATE phase_status
          |   SET to_completed = true,
          |       to_transfer_ts = $ts
          |WHERE transaction_id = $transactionId
          """.stripMargin.update.run
      .transact(rw.writeXA.xa)

  def update(ps: PhaseStatus): IO[Int] =
    sql"""|UPDATE phase_status
          |   SET from_completed = ${ps.fromTransferCompleted},
          |       from_transfer_ts = ${ps.fromTransferCreatedAt},
          |       to_completed = ${ps.toTransferCompleted},
          |       to_transfer_ts = ${ps.toTransferCreatedAt}
          |WHERE transaction_id = ${ps.transactionId}
          """.stripMargin.update.run
      .transact(rw.writeXA.xa)

  def getList(from: Instant, limit: Int): IO[Vector[PhaseStatus]] =
    sql"""|SELECT transaction_id, from_completed, from_transfer_ts, to_completed, to_transfer_ts, created_at
          |FROM phase_status
          |WHERE created_at  > $from
          |ORDER BY created_at
          |LIMIT $limit
          """.stripMargin
      .query[PhaseStatus]
      .to[Vector]
      .transact(rw.readXA.xa)

  def get(transactionId: UUID): IO[PhaseStatus] =
    sql"""|SELECT transaction_id, from_completed, from_transfer_ts, to_completed, to_transfer_ts, created_at
          |FROM phase_status
          |WHERE transaction_id = ${transactionId}
          """.stripMargin
      .query[PhaseStatus]
      .unique
      .transact(rw.readXA.xa)
}

object PostgresPhaseStatusAccessor {
  def resource(rw: ReadWriteTransactors[IO]): Resource[IO, PostgresPhaseStatusAccessor] =
    Resource.pure(new PostgresPhaseStatusAccessor(rw))
}
