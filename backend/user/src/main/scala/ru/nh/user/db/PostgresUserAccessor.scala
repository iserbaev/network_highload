package ru.nh.user.db

import cats.data.{ NonEmptyChain, NonEmptyList }
import cats.effect.std.UUIDGen
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import cats.{ Functor, Reducible }
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import org.typelevel.log4cats.LoggerFactory
import ru.nh.user.UserAccessor.UserRow
import ru.nh.user.{ RegisterUserCommand, User, UserAccessor }

import java.time.LocalDate
import java.util.UUID

class PostgresUserAccessor extends UserAccessor[ConnectionIO] {
  private def getUserStatement(userId: UUID): Fragment =
    sql"""SELECT user_id, created_at, name, surname, age, city, password, gender, biography, birthdate
         |FROM users
         |WHERE user_id = $userId
         """.stripMargin

  private def getUserHobbiesStatement(userId: UUID): Fragment =
    sql"""SELECT hobby
         |FROM user_hobby
         |WHERE user_id = $userId
         """.stripMargin

  private def insertUser(id: UUID, u: RegisterUserCommand): Fragment =
    sql"""INSERT INTO users(
         |             user_id, name, surname, age, city, password, gender, biography, birthdate
         |           )
         |   VALUES  (
         |             $id, ${u.name}, ${u.surname}, ${u.age},
         |             ${u.city}, ${u.password}, ${u.gender},
         |             ${u.biography}, ${u.birthdate}
         |           )
         |RETURNING user_id, created_at, name, surname, age, city, password, gender, biography, birthdate      
          """.stripMargin

  private def searchUserStatement(firstNamePrefix: String, lastNamePrefix: String): Fragment =
    sql"""SELECT user_id, created_at, name, surname, age, city, password, gender, biography, birthdate
         |FROM users
         |WHERE name LIKE ${firstNamePrefix + "%"} AND surname LIKE ${lastNamePrefix + "%"}
         """.stripMargin

  private def insertHobbies[R[_]: Reducible](h: R[(UUID, String)]): Fragment =
    fr"INSERT INTO user_hobby(user_id, hobby)" ++
      Fragments.values[R, (UUID, String)](h)

  def save(u: RegisterUserCommand): ConnectionIO[UserRow] =
    UUIDGen[ConnectionIO].randomUUID.flatMap { id =>
      insertUser(id, u).update
        .withGeneratedKeys[UserRow](
          "user_id",
          "created_at",
          "name",
          "surname",
          "age",
          "city",
          "password",
          "gender",
          "biography",
          "birthdate"
        )
        .compile
        .lastOrError <*
        NonEmptyChain.fromSeq(u.hobbies.map(h => (id, h))).traverse_(insertHobbies(_).update.run)
    }

  private case class InsertUserRowRequest(
      userId: UUID,
      name: String,
      surname: String,
      age: Int,
      city: String,
      password: String,
      gender: Option[String],
      biography: Option[String],
      birthdate: Option[LocalDate]
  )
  def saveBatch[R[_]: Reducible: Functor](u: R[RegisterUserCommand]): ConnectionIO[Unit] = {
    val rows = u.map { c =>
      val id = UUID.randomUUID()
      (
        id,
        InsertUserRowRequest(id, c.name, c.surname, c.age, c.city, c.password, c.gender, c.biography, c.birthdate),
        c
      )
    }

    // Reducible guarantee that it's not empty
    val hobbies = NonEmptyList.fromListUnsafe(rows.foldMap { case (id, _, c) => c.hobbies.map(s => (id, s)) })

    val sql = fr"INSERT INTO users(user_id, name, surname, age, city, password, gender, biography, birthdate)" ++
      Fragments.values[R, InsertUserRowRequest](rows.map(_._2))

    sql.update.run *> insertHobbies(hobbies).update.run.void
  }

  def getUserRow(userId: UUID): ConnectionIO[Option[UserRow]] =
    getUserStatement(userId)
      .query[UserRow]
      .option

  def getHobbies(userId: UUID): ConnectionIO[List[String]] =
    getUserHobbiesStatement(userId).query[String].to[List]

  def getUser(userId: UUID): ConnectionIO[Option[User]] =
    getUserRow(userId).flatMap(_.traverse(row => getHobbies(userId).map(row.toUser)))

  def search(firstNamePrefix: String, lastNamePrefix: String): ConnectionIO[Option[UserRow]] =
    searchUserStatement(firstNamePrefix, lastNamePrefix)
      .query[UserRow]
      .option
}

object PostgresUserAccessor {
  import ru.nh.db.transactors._
  import ru.nh.db.transactors.syntax._
  def resource(implicit L: LoggerFactory[IO]): Resource[IO, PostgresUserAccessor] = Resource.eval {
    L.fromClass(classOf[PostgresUserAccessor]).map { _ =>
      new PostgresUserAccessor
    }
  }

  def inIO(rw: ReadWriteTransactors[IO])(
      implicit L: LoggerFactory[IO]
  ): Resource[IO, UserAccessor[IO]] = Resource.suspend {
    L.fromClass(classOf[PostgresUserAccessor]).map { implicit log =>
      PostgresUserAccessor.resource.map(_.mapK(rw.readK, rw.writeK))
    }
  }
}
