package ru.nh.user

import java.time.Instant
import java.util.UUID

final case class Message(sender: UUID, conversationId: UUID, conversationIndex: Int, message: String, createdAt: Instant)