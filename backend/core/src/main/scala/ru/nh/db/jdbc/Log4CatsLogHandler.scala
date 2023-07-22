package ru.nh.user.db.jdbc

import cats.Defer
import doobie.util.log._
import org.typelevel.log4cats.Logger

final class Log4CatsLogHandler[F[_]] private (log: Logger[F])(implicit F: Defer[F]) extends LogHandler[F] {
  def run(e: LogEvent): F[Unit] = F.defer {
    def sql  = e.sql.linesIterator.dropWhile(_.trim.isEmpty).mkString("\n  ")
    def args = e.args.mkString("[", ", ", "]")

    e match {
      case Success(_, _, l, exec, proc) =>
        def elapsed = exec + proc
        log.trace(s"""Successful Statement Execution:
                     |
                     |  $sql
                     |
                     | arguments = $args
                     | label     = $l
                     |   elapsed = ${elapsed.toMillis} ms (${exec.toMillis} ms exec, ${proc.toMillis} ms processing)
                     |""".stripMargin)

      case ProcessingFailure(_, _, l, exec, proc, t) =>
        def elapsed = exec + proc
        log.warn(t)(
          s"""Failed ResultSet Processing:
             |
             |  $sql
             |
             | arguments = $args
             | label     = $l
             |   elapsed = ${elapsed.toMillis} ms (${exec.toMillis} ms exec, [failed] ${proc.toMillis} ms processing)
             |   failure = [${t.getClass.getSimpleName}] ${t.getMessage}
             |""".stripMargin
        )

      case ExecFailure(_, _, l, exec, t) =>
        log.warn(t)(s"""Failed Statement Execution:
                       |
                       |  $sql
                       |
                       | arguments = $args
                       | label     = $l
                       |   elapsed = ${exec.toMillis.toString} ms ([failed] ${exec.toMillis} ms exec)
                       |   failure = [${t.getClass.getSimpleName}] ${t.getMessage}
                       |""".stripMargin)
    }
  }
}

object Log4CatsLogHandler {
  def apply[F[_]: Defer](log: Logger[F]): LogHandler[F] = new Log4CatsLogHandler(log)
}
