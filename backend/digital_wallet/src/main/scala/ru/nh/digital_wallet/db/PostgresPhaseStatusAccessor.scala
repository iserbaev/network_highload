package ru.nh.digital_wallet.db

import cats.effect.{ IO, Resource }
import doobie.Write
import doobie.implicits._
import doobie.postgres.implicits._
import org.postgresql.util.PGInterval
import ru.nh.db.transactors.ReadWriteTransactors
import ru.nh.digital_wallet.{ PhaseStatus, PhaseStatusAccessor }

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

class PostgresPhaseStatusAccessor private (rw: ReadWriteTransactors[IO]) extends PhaseStatusAccessor[IO] {

  def upsert(ps: PhaseStatus): IO[Int] =
    sql"""|INSERT INTO phase_status(transaction_id, from_completed, from_transfer_ts, to_completed, to_transfer_ts, created_at, done)
          |VALUES (${ps.transactionId}, ${ps.fromTransferCompleted}, ${ps.fromTransferCreatedAt}, ${ps.toTransferCompleted}, ${ps.toTransferCreatedAt}, ${ps.createdAt}, ${ps.done})
          |ON CONFLICT (transaction_id)
          |    DO UPDATE
          |    SET from_completed = ${ps.fromTransferCompleted},
          |        from_transfer_ts = ${ps.fromTransferCreatedAt},
          |        to_completed = ${ps.toTransferCompleted},
          |        to_transfer_ts = ${ps.toTransferCreatedAt},
          |        done = ${ps.done}
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

  def completePhase(transactionId: UUID): IO[Int] =
    sql"""|UPDATE phase_status
          |   SET done = true
          |WHERE transaction_id = $transactionId
          """.stripMargin.update.run
      .transact(rw.writeXA.xa)

  def getNonCompletedPhases(until: Instant, limit: Int): IO[Vector[PhaseStatus]] =
    sql"""|SELECT transaction_id, from_completed, from_transfer_ts, to_completed, to_transfer_ts, created_at, done
          |FROM phase_status
          |WHERE done = false 
          |  AND created_at < $until
          |ORDER BY created_at
          |LIMIT $limit
          """.stripMargin
      .query[PhaseStatus]
      .to[Vector]
      .transact(rw.readXA.xa)

  def getNonCompletedPhases(ttl: FiniteDuration, limit: Int): IO[Vector[PhaseStatus]] =
    IO.realTimeInstant.flatMap { now =>
      getNonCompletedPhases(now.minusSeconds(ttl.toSeconds), limit)
    }

  def update(ps: PhaseStatus): IO[Int] =
    sql"""|UPDATE phase_status
          |   SET from_completed = ${ps.fromTransferCompleted},
          |       from_transfer_ts = ${ps.fromTransferCreatedAt},
          |       to_completed = ${ps.toTransferCompleted},
          |       to_transfer_ts = ${ps.toTransferCreatedAt},
          |       done = ${ps.done}
          |WHERE transaction_id = ${ps.transactionId}
          """.stripMargin.update.run
      .transact(rw.writeXA.xa)

  def getList(from: Instant, limit: Int): IO[Vector[PhaseStatus]] =
    sql"""|SELECT transaction_id, from_completed, from_transfer_ts, to_completed, to_transfer_ts, created_at, done
          |FROM phase_status
          |WHERE created_at  > $from
          |ORDER BY created_at
          |LIMIT $limit
          """.stripMargin
      .query[PhaseStatus]
      .to[Vector]
      .transact(rw.readXA.xa)

  def get(transactionId: UUID): IO[PhaseStatus] =
    sql"""|SELECT transaction_id, from_completed, from_transfer_ts, to_completed, to_transfer_ts, created_at, done
          |FROM phase_status
          |WHERE transaction_id = ${transactionId}
          """.stripMargin
      .query[PhaseStatus]
      .unique
      .transact(rw.readXA.xa)
}

object PostgresPhaseStatusAccessor {
  implicit val finiteDurationDoobieWrite: Write[FiniteDuration] =
    Write[PGInterval].contramap(fd => new PGInterval(fd.toString()))

  def resource(rw: ReadWriteTransactors[IO]): Resource[IO, PostgresPhaseStatusAccessor] =
    Resource.pure(new PostgresPhaseStatusAccessor(rw))
}
