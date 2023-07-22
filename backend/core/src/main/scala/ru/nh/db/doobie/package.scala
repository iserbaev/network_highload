package ru.nh.db

package object doobie {
  // codes from https://www.postgresql.org/docs/current/errcodes-appendix.html
  // that can be used for retrying transaction from client side
  val RetrySqlStateCode     = "40001"
  val ConnectionFailureCode = "08006"
}
