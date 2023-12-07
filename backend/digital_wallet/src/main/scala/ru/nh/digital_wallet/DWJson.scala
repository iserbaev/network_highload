package ru.nh.digital_wallet

import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }
import sttp.tapir._

object DWJson {
  implicit val bcDecoder: Decoder[BalanceSnapshot]          = deriveDecoder[BalanceSnapshot]
  implicit val bcEncoder: Encoder.AsObject[BalanceSnapshot] = deriveEncoder[BalanceSnapshot]
  implicit val bcSchema: Schema[BalanceSnapshot]            = Schema.derived[BalanceSnapshot]

  implicit val tcDecoder: Decoder[TransferCommand]          = deriveDecoder[TransferCommand]
  implicit val tcEncoder: Encoder.AsObject[TransferCommand] = deriveEncoder[TransferCommand]
  implicit val tcSchema: Schema[TransferCommand]            = Schema.derived[TransferCommand]

  implicit val teDecoder: Decoder[TransferEvent]          = deriveDecoder[TransferEvent]
  implicit val teEncoder: Encoder.AsObject[TransferEvent] = deriveEncoder[TransferEvent]
  implicit val teSchema: Schema[TransferEvent]            = Schema.derived[TransferEvent]

  implicit val tcrDecoder: Decoder[TransferCommandResponse]          = deriveDecoder[TransferCommandResponse]
  implicit val tcrEncoder: Encoder.AsObject[TransferCommandResponse] = deriveEncoder[TransferCommandResponse]
  implicit val tcrSchema: Schema[TransferCommandResponse]            = Schema.derived[TransferCommandResponse]
}
