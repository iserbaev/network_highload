package ru.nh

import java.time.Instant
import java.util.UUID

final case class GroupMessage(
    conversationId: UUID,
    conversationIndex: Long,
    sender: UUID,
    message: String,
    createdAt: Instant
)

final case class PrivateMessage(
    conversationId: UUID,
    conversationIndex: Long,
    sender: UUID,
    recipient: UUID,
    message: String,
    createdAt: Instant
)
