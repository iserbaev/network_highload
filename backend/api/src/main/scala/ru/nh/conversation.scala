package ru.nh

import java.util.UUID

final case class Conversation(
    id: UUID,
    participant: UUID,
    privateConversation: Boolean,
    privateConversationParticipant: Option[UUID]
)
