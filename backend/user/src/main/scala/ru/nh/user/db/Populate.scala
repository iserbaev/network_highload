package ru.nh.user.db

import cats.effect.IO
import ru.nh.RegisterUserCommand

import scala.io.Source
import scala.language.reflectiveCalls
object Populate {
  private val hobbies       = List("books", "sport", "chess", "games", "nature", "sea")
  private val hobbiesLength = hobbies.length

  def getUsers: IO[Vector[RegisterUserCommand]] = IO
    .blocking {
      getCSV.zipWithIndex.map { case (u, i) =>
        RegisterUserCommand(
          u.firstName,
          u.lastName,
          u.age,
          u.city,
          u.firstName + u.age,
          None,
          None,
          None,
          hobbies.slice(0, i % hobbiesLength)
        )
      }
    }
    .flatTap(v => IO.println(s"${v.length} users to populate"))

  private def getCSV: Vector[UserCSVRow] = {
    val rows = Vector.newBuilder[Array[String]]

    using(Source.fromResource("csv/people.csv")) { source =>
      for (line <- source.getLines()) {
        rows += line.split(",").map(_.trim)
      }
    }

    rows.result().tail.flatMap(UserCSVRow.build)
  }

  case class UserCSVRow(
      fullName: String,
      age: Int,
      city: String
  ) {
    def firstName = fullName.split(" ", 2).head
    def lastName  = fullName.split(" ", 2).last
  }

  object UserCSVRow {
    def build(row: Array[String]): Option[UserCSVRow] =
      Option.when(row.length == 3) {
        UserCSVRow(
          row.head,
          row(1).toInt,
          row(2)
        )
      }
  }

  def using[A <: { def close(): Unit }, B](resource: A)(f: A => B): B =
    try {
      f(resource)
    } finally {
      resource.close()
    }

}
