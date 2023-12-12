package ru.nh.digital_wallet.events

import cats.data.{ Chain, NonEmptyChain }
import cats.syntax.all._
import ru.nh.digital_wallet.BalanceAccessor.BalanceEventLogRow
import ru.nh.digital_wallet.BalanceSnapshot
import ru.nh.events.SnapshotBuilder

object snapshots {
  val balanceSnapshotBuilder: SnapshotBuilder[BalanceEventLogRow, BalanceSnapshot] =
    new SnapshotBuilder[BalanceEventLogRow, BalanceSnapshot] {
      def build(events: Chain[BalanceEventLogRow]): Option[BalanceSnapshot] =
        NonEmptyChain.fromChain(events).map { nec =>
          val id   = nec.head.accountId
          val last = nec.maximumBy(_.changeIndex)

          BalanceSnapshot(
            accountId = id,
            lastChangeIndex = last.changeIndex,
            mintSum = nec.map(_.mintChange.getOrElse(0)).sumAll,
            spendSum = nec.map(_.spendChange.getOrElse(0)).sumAll,
            lastModifiedAt = last.createdAt
          )
        }

      def updateWith(events: Chain[BalanceEventLogRow], current: BalanceSnapshot): Option[BalanceSnapshot] =
        build(events.filter(_.accountId == current.accountId)).flatMap { newStatus =>
          Option.when(newStatus.lastChangeIndex > current.lastChangeIndex) {
            current.copy(
              mintSum = current.mintSum + newStatus.mintSum,
              spendSum = current.spendSum + newStatus.spendSum,
              lastChangeIndex = newStatus.lastChangeIndex,
              lastModifiedAt = newStatus.lastModifiedAt
            )
          }
        }
    }

}
