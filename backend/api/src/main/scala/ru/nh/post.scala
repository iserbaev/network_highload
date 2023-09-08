package ru.nh

import java.time.Instant
import java.util.UUID

final case class Post(id: UUID, text: String, author_user_id: UUID, createdAt: Instant)
object Post {
  implicit val ordering: Ordering[Post] = Ordering.by[Post, Instant](_.createdAt)
}
