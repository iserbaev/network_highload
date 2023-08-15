package ru.nh.db.transactors

import ru.nh.db.jdbc.JdbcSupport

import scala.annotation.unused
import scala.concurrent.duration.FiniteDuration

final case class TransactorSettings private (
    connection: JdbcSupport.ConnectionConfig,
    pool: JdbcSupport.PoolConfig,
    transactionRetry: TransactionRetryConfig
) {
  def withConnection(connection: JdbcSupport.ConnectionConfig): TransactorSettings = copy(connection = connection)

  def withPool(pool: JdbcSupport.PoolConfig): TransactorSettings = copy(pool = pool)

  def withTransactionRetry(transactionRetry: TransactionRetryConfig): TransactorSettings =
    copy(transactionRetry = transactionRetry)

}

object TransactorSettings {
  def apply(
      connection: JdbcSupport.ConnectionConfig,
      pool: JdbcSupport.PoolConfig,
      transactionRetry: TransactionRetryConfig
  ): TransactorSettings =
    new TransactorSettings(connection, pool, transactionRetry)

  @unused
  private def unapply(c: TransactorSettings): TransactorSettings = c
}

final case class TransactionRetryConfig private (retryCount: Int, baseInterval: FiniteDuration) {
  def withRetryCount(retryCount: Int): TransactionRetryConfig = copy(retryCount = retryCount)

  def withBaseInterval(baseInterval: FiniteDuration): TransactionRetryConfig = copy(baseInterval = baseInterval)
}

object TransactionRetryConfig {
  def apply(retryCount: Int, baseInterval: FiniteDuration): TransactionRetryConfig =
    new TransactionRetryConfig(retryCount, baseInterval)

  @unused
  private def unapply(c: TransactionRetryConfig): TransactionRetryConfig = c
}
