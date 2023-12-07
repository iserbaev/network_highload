package ru.nh.digital_wallet.db

import cats.NonEmptyTraverse
import cats.effect.{ IO, Resource }
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import ru.nh.db.transactors.ReadWriteTransactors
import ru.nh.digital_wallet.BalanceAccessor.{ BalanceCommandLogRow, BalanceEventLogRow }
import ru.nh.digital_wallet.{ BalanceAccessor, BalanceSnapshot, TransferCommand, TransferEvent }

import java.time.Instant

class PostgresBalanceAccessor private (rw: ReadWriteTransactors[IO]) extends BalanceAccessor[IO] {
  def logTransferCommand(cmd: TransferCommand): IO[BalanceCommandLogRow] = {
    val sql =
      sql"""INSERT INTO balance_commands_log(transaction_id, account_id_from, account_id_to,
           |                                 amount, currency_code_letter)
           |VALUES (${cmd.transactionId}, ${cmd.fromAccount}, ${cmd.toAccount}, ${cmd.amount}, ${cmd.currencyType})
           |RETURNING change_index, created_at
           |""".stripMargin

    sql.update
      .withGeneratedKeys[(Long, Instant)]("change_index", "created_at")
      .compile
      .lastOrError
      .transact(rw.writeXA.xa)
      .map { case (l, instant) =>
        BalanceCommandLogRow(
          cmd.transactionId,
          cmd.fromAccount,
          cmd.toAccount,
          cmd.amount,
          cmd.currencyType,
          l,
          instant
        )
      }
  }

  def logTransferCommandsBatch[R[_]: NonEmptyTraverse](cmd: R[TransferCommand]): IO[Vector[BalanceCommandLogRow]] = {
    val sql =
      """INSERT INTO balance_commands_log(transaction_id, account_id_from, account_id_to, amount, currency_code_letter)
        |VALUES (?, ?, ?, ?, ?)
        |RETURNING *""".stripMargin

    Update[TransferCommand](sql)
      .updateManyWithGeneratedKeys[BalanceCommandLogRow](
        "transaction_id",
        "account_id_from",
        "account_id_to",
        "amount",
        "currency_code_letter",
        "change_index",
        "created_at"
      )(cmd)
      .compile
      .toVector
      .transact(rw.writeXA.xa)
  }

  def logTransferEvent(e: TransferEvent): IO[BalanceEventLogRow] = {
    val sql =
      sql"""INSERT INTO balance_events_log(account_id, transaction_id,
           |                              mint_change, spend_change, change_description, change_index)
           |VALUES (${e.accountId}, ${e.transactionId}, ${e.mint}, ${e.spend}, ${e.description}, ${e.changeIndex})
           |RETURNING created_at
           |""".stripMargin

    sql.update
      .withGeneratedKeys[Instant]("created_at")
      .compile
      .lastOrError
      .transact(rw.writeXA.xa)
      .map(i => BalanceEventLogRow(e.accountId, e.transactionId, e.mint, e.spend, e.description, e.changeIndex, i))
  }

  def logTransferEventBatch[R[_]: NonEmptyTraverse](e: R[TransferEvent]): IO[Vector[BalanceEventLogRow]] = {
    val sql =
      """INSERT INTO balance_events_log(account_id, transaction_id, mint_change, spend_change, change_description, change_index)
        |VALUES (?, ?, ?, ?, ?, ?)
        |RETURNING *""".stripMargin

    Update[TransferEvent](sql)
      .updateManyWithGeneratedKeys[BalanceEventLogRow](
        "account_id",
        "transaction_id",
        "mint_change",
        "spend_change",
        "change_description",
        "change_index",
        "created_at"
      )(e)
      .compile
      .toVector
      .transact(rw.writeXA.xa)
  }

  def getBalanceSnapshot(accountId: String): IO[Option[BalanceSnapshot]] = {
    val sql = sql"""SELECT account_id, last_balance_change_index, mint_sum, spend_sum, last_modified_at 
                   |FROM balance_snapshot
                   |WHERE account_id = $accountId""".stripMargin

    sql
      .query[BalanceSnapshot]
      .option
      .transact(rw.readXA.xa)
  }
}

object PostgresBalanceAccessor {
  def resource(rw: ReadWriteTransactors[IO]): Resource[IO, PostgresBalanceAccessor] =
    Resource.pure(new PostgresBalanceAccessor(rw))
}
