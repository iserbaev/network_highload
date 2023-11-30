package ru.nh.digital_wallet

import cats.effect.IO

trait HeartbeatManager {
  def processTransferEvent(e: TransferEvent): IO[Unit]
}
