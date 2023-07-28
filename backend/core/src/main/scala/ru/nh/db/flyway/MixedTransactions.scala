package ru.nh.db.flyway

import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

sealed trait MixedTransactions
object MixedTransactions {
  case object Allow extends MixedTransactions
  case object Deny  extends MixedTransactions

  implicit val MixedTransactionsConfigReader: ConfigReader[MixedTransactions] =
    deriveEnumerationReader[MixedTransactions]
}
