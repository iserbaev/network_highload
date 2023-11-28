package ru.nh.events

import cats.data.Chain
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Topic

trait SnapshotManager[K, E, S] {
  def snapshot(key: K): IO[Option[S]]
  def build(key: K, events: Chain[E]): IO[Unit]
  def updateWith(key: K, events: Chain[E]): IO[Unit]

  def updateWith(key: K, event: E): IO[Unit] =
    updateWith(key, Chain.one(event))

  def snapshots: Stream[IO, S]
}

object SnapshotManager {
  def apply[K, E, S](builder: SnapshotBuilder[E, S]): IO[SnapshotManager[K, E, S]] =
    (IO.ref(Map.empty[K, S]), Topic[IO, S]).mapN { (ref, topic) =>
      new SnapshotManager[K, E, S] {
        def snapshot(key: K): IO[Option[S]] =
          ref.get.map(_.get(key))

        def build(key: K, events: Chain[E]): IO[Unit] =
          builder.build(events).traverse_ { s =>
            ref.update(_.updated(key, s)) *> topic.publish1(s)
          }

        def updateWith(key: K, events: Chain[E]): IO[Unit] =
          ref
            .flatModify { m =>
              m.get(key)
                .flatMap { current =>
                  builder
                    .updateWith(events, current)
                    .map { s =>
                      (m.updated(key, s), topic.publish1(s).void)
                    }
                }
                .getOrElse((m, IO.unit))
            }

        def snapshots: Stream[IO, S] =
          topic.subscribeUnbounded
      }

    }

  def resource[K, E, S](builder: SnapshotBuilder[E, S]): Resource[IO, SnapshotManager[K, E, S]] =
    Resource.eval(apply[K, E, S](builder))
}

trait SnapshotBuilder[E, S] {
  def build(events: Chain[E]): Option[S]

  def updateWith(events: Chain[E], current: S): Option[S]
}
