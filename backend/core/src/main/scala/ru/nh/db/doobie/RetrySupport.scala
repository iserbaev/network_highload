package ru.nh.db.doobie

import cats.Applicative
import cats.effect.Temporal
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
  private[doobie] def transactionRetrySqlStateCondition[F[_]: Applicative]: PartialFunction[Throwable, F[Boolean]] = {
    case ex: SQLException =>
      Option(ex.getSQLState).contains(RetrySqlStateCode).pure[F]
  }

  private[doobie] def transactionConnectionFailureCondition[F[_]: Applicative]
      : PartialFunction[Throwable, F[Boolean]] = { case ex: SQLTransientConnectionException =>
    Option(ex.getSQLState).contains(ConnectionFailureCode).pure[F]
  }

  def defaultTransactionRetryCondition[F[_]: Applicative]: PartialFunction[Throwable, F[Boolean]] =
    transactionRetrySqlStateCondition[F].orElse(transactionConnectionFailureCondition[F])

  def retryConnection[F[_], A](fa: ConnectionIO[A])(xa: Transactor[F], retryCount: Int, baseInterval: FiniteDuration)(
      retryWhen: PartialFunction[Throwable, F[Boolean]]
  )(
      implicit logger: Logger[F],
      F: Temporal[F]
  ): F[A] = {
    val transaction = fa.recoverWith(invariantHandlers[A]).transact(xa)

    retryTransactions(transaction, retryCount, baseInterval, retryWhen)
  }

  private def retryTransactions[F[_], A](
      f: F[A],
      retryCount: Int,
      baseInterval: FiniteDuration,
      retryWhen: PartialFunction[Throwable, F[Boolean]]
  )(
      implicit logger: Logger[F],
      F: Temporal[F]
  ): F[A] =
    f.retryingOnSomeErrors(
      isWorthRetrying = retryWhen.applyOrElse[Throwable, F[Boolean]](_, _ => false.pure[F]),
      policy = limitRetries[F](retryCount) |+| fullJitter[F](baseInterval),
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

  private[doobie] def invariantHandlers[A]: PartialFunction[Throwable, ConnectionIO[A]] = {
    case UnexpectedEnd      => FC.raiseError(new NoSuchElementException(s"Queried element not found"))
    case ex: InvalidEnum[_] => FC.raiseError(new IllegalArgumentException(s"Enum variant not found: ${ex.getMessage}"))
    case ex: InvalidOrdinal[_] =>
      FC.raiseError(new IllegalArgumentException(s"Enum ordinal not found: ${ex.getMessage}"))
    case ex: InvalidValue[_, _] =>
      FC.raiseError(new IllegalArgumentException(s"Query value is invalid: ${ex.getMessage}"))
  }

  private[doobie] def logSqlException[F[_]](e: Throwable)(implicit logger: Logger[F]): F[Unit] = e match {
    case e: SQLException =>
      logger.warn(e) {
        s"${e.getClass.getSimpleName} caught in transaction" ++
          s" with message '${e.getMessage}'" ++
          s" for SQL state: ${e.getSQLState}."
      }
    case other =>
      logger.warn(other) {
        s"${other.getClass.getSimpleName} caught in transaction: ${other.getMessage}"
      }
  }

}
