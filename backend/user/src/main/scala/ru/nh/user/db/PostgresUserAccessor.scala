package ru.nh.user.db

import cats.Reducible
import cats.data.NonEmptyChain
import cats.effect.std.UUIDGen
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import ru.nh.user.UserAccessor.UserRow
import ru.nh.user.{ RegisterUserCommand, User, UserAccessor }

import java.util.UUID

class PostgresUserAccessor extends UserAccessor[ConnectionIO] {
  private def getUserStatement(userId: UUID): Fragment =
    sql"""SELECT user_id, createdAt, name, surname, age, gender, city
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
         |             user_id, name, surname, age, gender, city
         |           )
         |   VALUES  (
         |             $id, ${u.name}, ${u.surname}, ${u.age},
         |             ${u.gender}, ${u.city}
         |           )        
          """.stripMargin

  private def insertHobbies[R[_]: Reducible](h: R[(UUID, String)]): Fragment =
    fr"INSERT INTO user_hobby(user_id, hobby)" ++
      Fragments.values[R, (UUID, String)](h)

  def save(u: RegisterUserCommand): ConnectionIO[User] =
    UUIDGen[ConnectionIO].randomUUID.flatMap { id =>
      (
        insertUser(id, u).update.run,
        NonEmptyChain.fromSeq(u.hobbies.map(h => (id, h))).traverse_(insertHobbies(_).update.run)
      ).tupled
        .as(u.toUser(id))
    }

  def get(userId: UUID): ConnectionIO[Option[User]] =
    getUserStatement(userId)
      .query[UserRow]
      .option
      .flatMap(_.traverse { userRow =>
        getUserHobbiesStatement(userId).query[String].to[List].map(userRow.toUser)
      })
}
