CREATE SEQUENCE private_conversation_index_seq CYCLE;
CREATE SEQUENCE group_conversation_index_seq CYCLE;

CREATE TABLE IF NOT EXISTS private_message_log
(
    conversation_id    UUID                                                          NOT NULL,
    conversation_index BIGINT      DEFAULT nextval('private_conversation_index_seq') NOT NULL,
    message_from       UUID                                                          NOT NULL,
    message_to         UUID                                                          NOT NULL,
    message            TEXT                                                          NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT NOW()                                     NOT NULL,
    PRIMARY KEY (conversation_id, conversation_index, message_from)
);

CREATE TABLE IF NOT EXISTS group_message_log
(
    conversation_id    UUID                                                        NOT NULL,
    conversation_index BIGINT      DEFAULT nextval('group_conversation_index_seq') NOT NULL,
    sender             UUID                                                        NOT NULL,
    message            TEXT                                                        NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT NOW()                                   NOT NULL,
    PRIMARY KEY (conversation_id, conversation_index, sender)
);

