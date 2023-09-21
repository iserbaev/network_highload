package ru.nh

import cats.effect.{ IO, Resource }
import fs2.Stream

import java.util.UUID

import PostService.PostFeed

trait PostService {
  def addPost(userId: UUID, text: String): IO[UUID]

  def getPost(postId: UUID): IO[Option[Post]]

  def updatePost(postId: UUID, text: String): IO[Unit]

  def deletePost(postId: UUID): IO[Unit]

  def postFeed(userId: UUID, friends: List[UUID], offset: Int, limit: Int): Resource[IO, PostFeed]

  def postFeedPosted(userId: UUID, friends: List[UUID]): Resource[IO, PostFeed]
}

object PostService {
  final case class PostFeed(stream: Stream[IO, Post])
}
