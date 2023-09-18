package ru.nh

import java.time.Instant
import java.util.UUID

final case class Message(
    conversationId: UUID,
    conversationIndex: Int,
    sender: UUID,
    message: String,
    createdAt: Instant
)
