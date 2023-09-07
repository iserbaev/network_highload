package ru.nh.user

import java.util.UUID

final case class Conversation(
    id: UUID,
    participant: UUID,
    privateConversation: Boolean,
    privateConversationParticipant: Option[UUID]
)
