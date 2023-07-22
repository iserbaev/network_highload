package ru.nh.db.doobie

import cats.effect.IO
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.util.invariant.{ InvalidEnum, InvalidOrdinal, InvalidValue, UnexpectedEnd }
import org.typelevel.log4cats.Logger
import retry.RetryDetails.{ GivingUp, WillDelayAndRetry }
import retry.RetryPolicies.{ fullJitter, limitRetries }
import retry.syntax.all._

import java.sql.{ SQLException, SQLTransientConnectionException }
import scala.concurrent.duration.FiniteDuration
trait RetrySupport {
  private[doobie] def transactionRetrySqlStateCondition: PartialFunction[Throwable, IO[Boolean]] = {
    case ex: SQLException =>
      Option(ex.getSQLState).contains(RetrySqlStateCode).pure[IO]
  }

  private[doobie] def transactionConnectionFailureCondition: PartialFunction[Throwable, IO[Boolean]] = {
    case ex: SQLTransientConnectionException =>
      Option(ex.getSQLState).contains(ConnectionFailureCode).pure[IO]
  }

  def defaultTransactionRetryCondition: PartialFunction[Throwable, IO[Boolean]] =
    transactionRetrySqlStateCondition.orElse(transactionConnectionFailureCondition)

  def retryConnectionIO[A](fa: ConnectionIO[A])(xa: Transactor[IO], retryCount: Int, baseInterval: FiniteDuration)(
      retryWhen: PartialFunction[Throwable, IO[Boolean]]
  )(
      implicit logger: Logger[IO]
  ): IO[A] = {
    val transaction = fa.recoverWith(invariantHandlers[A]).transact(xa)

    retryTransactions(transaction, retryCount, baseInterval, retryWhen)
  }

  private def retryTransactions[A](
      f: IO[A],
      retryCount: Int,
      baseInterval: FiniteDuration,
      retryWhen: PartialFunction[Throwable, IO[Boolean]]
  )(
      implicit logger: Logger[IO]
  ): IO[A] =
    f.retryingOnSomeErrors(
      isWorthRetrying = retryWhen.applyOrElse[Throwable, IO[Boolean]](_, _ => false.pure[IO]),
      policy = limitRetries[IO](retryCount) |+| fullJitter[IO](baseInterval),
      onError = {
        case (ex, WillDelayAndRetry(_, retries, _)) =>
          logger.info(
            s"Got ${ex.getClass.getSimpleName} in transaction, attempting retry #${retries + 1}, message: ${ex.getMessage}"
          )
        case (ex, GivingUp(retries, delay)) =>
          logger.error(ex)(
            s"Got ${ex.getClass.getSimpleName} in transaction, aborting after $retries retries in $delay timespan, message: ${ex.getMessage}"
          )
      }
    )

  private[db] def invariantHandlers[A]: PartialFunction[Throwable, ConnectionIO[A]] = {
    case UnexpectedEnd      => FC.raiseError(new NoSuchElementException(s"Queried element not found"))
    case ex: InvalidEnum[_] => FC.raiseError(new IllegalArgumentException(s"Enum variant not found: ${ex.getMessage}"))
    case ex: InvalidOrdinal[_] =>
      FC.raiseError(new IllegalArgumentException(s"Enum ordinal not found: ${ex.getMessage}"))
    case ex: InvalidValue[_, _] =>
      FC.raiseError(new IllegalArgumentException(s"Query value is invalid: ${ex.getMessage}"))
  }

  private[doobie] def logSqlException(e: Throwable)(implicit logger: Logger[IO]): IO[Unit] = e match {
    case e: SQLException =>
      logger.warn(e) {
        s"${e.getClass.getSimpleName} caught in transaction" +
          s" with message '${e.getMessage}'" +
          s" for SQL state: ${e.getSQLState}."
      }
    case e =>
      logger.warn(e) {
        s"${e.getClass.getSimpleName} caught in transaction: ${e.getMessage}"
      }
  }

}
