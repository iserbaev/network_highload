package ru.nh.user.db

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import doobie.Transactor
import doobie.implicits._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import ru.nh.db.doobie.DoobieSupport
import ru.nh.db.doobie.DoobieSupport.ReadWriteTransactors
import ru.nh.db.flyway.MixedTransactions

final class PostgresModule(val config: PostgresModule.Config, val rw: ReadWriteTransactors[IO])(
    implicit log: Logger[IO]
) {
  val healthCheck: IO[Unit] =
    log.trace(show"Checking health of Postgres at '${config.read.connection.jdbcUrl}' ...") *>
      (PostgresModule.checkFullScan(rw.read.xa), PostgresModule.getTables(rw.read.xa)).flatMapN {
        (fullScanQueries, ourTables) =>
          fullScanQueries
            .filterNot(q => q.relname.contains("pg"))
            .filter(q => ourTables.contains(q.relname))
            .traverse_ { checkResult =>
              log
                .info(s"Detected sequential scan query in " ++ checkResult.relname)
                .whenA(checkResult.isFullScan)
            }
      }

}
object PostgresModule {
  final case class Config(
      read: DoobieSupport.TransactorSettings,
      write: DoobieSupport.TransactorSettings,
      migrations: Migrations,
      metricsEnabled: Boolean
  )

  final case class Migrations(locations: List[String], mixed: MixedTransactions)

  def apply(config: Config, metricsTrackerFactory: MetricsTrackerFactory)(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, PostgresModule] =
    Resource.eval(L.fromClass(classOf[PostgresModule])).flatMap { implicit log =>
      DoobieSupport
        .buildMeteredReadWriteTransactors(config.read, config.write, "Postgres", metricsTrackerFactory)
        .evalMap { transactors =>
          IO(new PostgresModule(config, transactors))
        }
    }

  private[db] final case class FullScanCheckResult(
      schemaname: String,
      relname: String,
      seqTupRead: Option[Long],
      seqScan: Option[Int],
      avgSeqTupRead: Option[Int],
      rowCount: Option[Int]
  ) {
    def isFullScan: Boolean = seqScan.exists(_ > 0) && rowCount.exists(_ > 0)
  }

  private[db] def checkFullScan(read: Transactor[IO]): IO[Seq[FullScanCheckResult]] =
    sql"""SELECT schemaname,
         |       relname,
         |       seq_scan,
         |       seq_tup_read,
         |       seq_tup_read / seq_scan as avg_seq_tup_read,
         |       n_tup_ins - n_tup_del as rowcount
         |FROM pg_stat_all_tables
         |WHERE seq_scan > 0
         |ORDER BY 5 DESC""".stripMargin.query[FullScanCheckResult].to[List].transact(read)

  private[db] def getTables(read: Transactor[IO]): IO[List[String]] =
    sql"""|SELECT table_name
          |  FROM information_schema.tables
          | WHERE table_type = 'BASE TABLE'
          |  AND table_catalog = 'network_highload'
          |  AND table_schema = 'public'""".stripMargin
      .query[String]
      .to[List]
      .transact(read)
}
