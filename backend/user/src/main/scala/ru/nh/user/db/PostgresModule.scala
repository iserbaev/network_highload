package ru.nh.user.db

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import doobie.ConnectionIO
import doobie.implicits._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.db.flyway.MixedTransactions
import ru.nh.db.transactors._
import ru.nh.db.transactors.syntax._

final class PostgresModule(val config: PostgresModule.Config, val rw: ReadWriteTransactors[IO])(
    implicit log: Logger[IO]
) {
  val healthCheck: IO[Unit] =
    log.trace(show"Checking health of Postgres at '${config.read.connection.jdbcUrl}' ...") *>
      (rw.read(PostgresModule.getSlowQueries), rw.read(PostgresModule.checkSeqScanTables)).flatMapN {
        (slowQueries, seqScanTables) =>
          seqScanTables.traverse_(res =>
            log.info(s"Detected sequential scan in ${res.relname}, avg read rows count ${res.avgSeqTupRead}")
          ) *>
            slowQueries
              .filterNot(_.query.contains("pg_stat"))
              .traverse_(res =>
                log.info(s"Detected slow query: mean time ${res.meanTime} MS, \n query ${res.query}...")
              ) *> rw.read(PostgresModule.resetStatementsStats).whenA(slowQueries.nonEmpty)
      }

}

object PostgresModule {
  final case class Config(
      read: TransactorSettings,
      write: TransactorSettings,
      migrations: Migrations,
      hikariMetricsEnabled: Boolean
  )

  final case class Migrations(locations: List[String], mixed: MixedTransactions)

  def apply(config: Config, metricsTrackerFactory: MetricsTrackerFactory)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, PostgresModule] =
    Resource
      .eval(L.fromClass(classOf[PostgresModule]))
      .flatMap { implicit log =>
        ReadWriteTransactors
          .buildMetered(config.read, config.write, "Postgres", metricsTrackerFactory)
          .evalMap { transactors =>
            IO(new PostgresModule(config, transactors))
          }
      }

  private[db] final case class SeqScanTablesCheckResult(
      schemaname: String,
      relname: String,
      seqTupRead: Long,
      seqScan: Long,
      avgSeqTupRead: Long,
      rowCount: Long
  )

  private[db] final case class SlowQueryCheckResult(
      query: String,
      calls: Long,
      totalTime: Double,
      meanTime: Double,
      maxTime: Double,
      percentage: Int
  )

  private[db] def checkSeqScanTables: ConnectionIO[List[SeqScanTablesCheckResult]] =
    sql"""SELECT schemaname,
         |       relname,
         |       seq_scan,
         |       seq_tup_read,
         |       seq_tup_read / seq_scan as avg_seq_tup_read,
         |       n_tup_ins - n_tup_del as rowcount
         |FROM pg_stat_user_tables
         |WHERE seq_scan > 0 AND seq_tup_read > 0 AND n_tup_ins > 0 AND (seq_tup_read / seq_scan) > 10000
         |ORDER BY 5 DESC""".stripMargin.query[SeqScanTablesCheckResult].to[List]

  private[db] def getSlowQueries: ConnectionIO[List[SlowQueryCheckResult]] =
    sql"""SELECT substring(query, 1, 150) AS query, 
         |       calls,
         |       round(total_exec_time::numeric, 2) AS total_time,
         |       round(mean_exec_time::numeric, 2) AS mean_time,
         |       round(max_exec_time::numeric, 2) AS max_time,
         |       round((100 * total_exec_time / sum(total_exec_time)
         |                                      OVER ())::numeric, 2) AS percentage
         |FROM  pg_stat_statements
         |WHERE mean_exec_time > 1000 AND total_exec_time > 0
         |ORDER BY total_exec_time DESC""".stripMargin.query[SlowQueryCheckResult].to[List]

  private[db] def resetStatementsStats: ConnectionIO[Unit] =
    fr"SELECT pg_stat_statements_reset()".query[String].option.void

}
