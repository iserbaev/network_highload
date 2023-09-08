package ru.nh

import doobie.{ConnectionIO, FC}

package object db {
  def ensureUpdated(result: ConnectionIO[Int]): ConnectionIO[Unit] =
    result.flatMap { updated =>
      if (updated != 1) {
        FC.raiseError(new RuntimeException(s"expected 1 row updated but got $updated"))
      } else FC.unit
    }
}
