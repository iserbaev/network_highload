CREATE UNIQUE INDEX IF NOT EXISTS conversation_log__private_conversations__partial_covering_idx ON conversation_log
    USING btree (participant, private_conversation_participant)
    INCLUDE (id, participant, private_conversation, private_conversation_participant, created_at)
    WHERE private_conversation AND private_conversation_participant IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS message_log__conversation__covering_idx ON message_log
    USING btree (conversation_id, conversation_index)
    INCLUDE (sender, conversation_id, conversation_index, message, created_at);


