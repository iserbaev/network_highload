package ru.th.user

final case class UserInfo(
    name: String,
    surname: String,
    age: Int,
    gender: String,
    hobbies: List[String],
    city: String
)
