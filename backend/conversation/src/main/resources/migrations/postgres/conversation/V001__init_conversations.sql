CREATE TABLE IF NOT EXISTS conversation_log
(
    id                               UUID        DEFAULT gen_random_uuid() NOT NULL,
    participant                      UUID                                  NOT NULL,
    private_conversation             BOOLEAN                               NOT NULL,
    private_conversation_participant UUID,
    created_at                       TIMESTAMPTZ DEFAULT now()             NOT NULL,
    PRIMARY KEY (id, participant)
);
