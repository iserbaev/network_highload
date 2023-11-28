package ru.nh.events

import cats.Monoid
import cats.data.{ Chain, NonEmptyChain }
import cats.effect.IO
import cats.syntax.foldable._

trait BatchSupport {
  def groupBatchesAndFoldMap[A, B: Monoid](batches: Chain[A], batchSize: Int)(run: NonEmptyChain[A] => IO[B]): IO[B] =
    batches.iterator.grouped(batchSize).toList.foldMapM {
      NonEmptyChain.fromSeq(_).map(run).getOrElse(IO(Monoid.empty[B]))
    }
}
