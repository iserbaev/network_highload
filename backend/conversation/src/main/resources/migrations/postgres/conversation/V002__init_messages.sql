CREATE SEQUENCE conversation_index_seq CYCLE;

CREATE TABLE IF NOT EXISTS message_log
(
    conversation_id    UUID                                                  NOT NULL,
    conversation_index BIGINT      DEFAULT nextval('conversation_index_seq') NOT NULL,
    sender             UUID                                                  NOT NULL,
    message            TEXT                                                  NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT NOW()                             NOT NULL,
    PRIMARY KEY (conversation_id, conversation_index, sender)
);
