package ru.nh.auth.db

import cats.effect.{ IO, Resource }
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.auth.LoginAccessor
import ru.nh.auth.LoginAccessor.LoginRow

class PostgresLoginAccessor extends LoginAccessor[ConnectionIO] {
  import ru.nh.db.ensureUpdated
  def save(login: String, password: String): ConnectionIO[Unit] = ensureUpdated {
    sql"""INSERT INTO logins(login, password)
         |VALUES ($login, $password) 
           """.stripMargin.update.run
  }

  def get(login: String): ConnectionIO[Option[LoginRow]] =
    sql"""SELECT login, password, created_at
         |FROM logins
         |WHERE login = $login
         """.stripMargin
      .query[LoginRow]
      .option
}

object PostgresLoginAccessor {
  import ru.nh.db.transactors._
  import ru.nh.db.transactors.syntax._

  def resource: Resource[IO, PostgresLoginAccessor] = Resource.eval {
    IO {
      new PostgresLoginAccessor
    }
  }

  def inIO(rw: ReadWriteTransactors[IO])(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, LoginAccessor[IO]] = Resource.suspend {
    L.fromClass(classOf[PostgresLoginAccessor]).map { implicit log =>
      resource.map(_.mapK(rw.readK, rw.writeK))
    }
  }
}
