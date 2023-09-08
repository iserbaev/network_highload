CREATE TABLE conversation_log
(
    id                               UUID        DEFAULT gen_random_uuid() NOT NULL,
    participant                      UUID                                  NOT NULL,
    private_conversation             BOOLEAN                               NOT NULL,
    private_conversation_participant UUID,
    created_at                       timestamptz DEFAULT now()             NOT NULL,
    PRIMARY KEY (id, participant)
);

CREATE TABLE message_log
(
    sender             UUID                                                      NOT NULL,
    conversation_id    UUID
        CONSTRAINT message_conversation_id_fkey REFERENCES conversation_log (id) NOT NULL,
    conversation_index INT         DEFAULT 1                                     NOT NULL,
    message            TEXT                                                      NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT NOW()                                 NOT NULL,
    PRIMARY KEY (sender, conversation_id, conversation_index)
);



