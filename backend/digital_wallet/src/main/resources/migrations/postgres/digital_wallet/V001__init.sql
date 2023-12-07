CREATE SEQUENCE IF NOT EXISTS change_index_seq CYCLE;

CREATE TABLE IF NOT EXISTS balance_commands_log
(
    transaction_id       UUID                                                         NOT NULL,
    account_id_from      VARCHAR(64)                                                  NOT NULL,
    account_id_to        VARCHAR(64)                                                  NOT NULL,
    amount               INT                                                          NOT NULL,
    currency_code_letter VARCHAR(3)                                                   NOT NULL,
    change_index         BIGINT                   DEFAULT nextval('change_index_seq') NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE DEFAULT now()                       NOT NULL,
    CONSTRAINT "primary_balance_commands_log"
        PRIMARY KEY (transaction_id)
);

CREATE TABLE IF NOT EXISTS balance_events_log
(
    account_id         VARCHAR(64)                            NOT NULL,
    transaction_id     UUID                                   NOT NULL,
    mint_change        INT,
    spend_change       INT,
    change_description TEXT                                   NOT NULL,
    change_index       BIGINT                                 NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT "primary_balance_events_log"
        PRIMARY KEY (account_id, transaction_id)
);

CREATE TABLE IF NOT EXISTS balance_snapshot
(
    account_id                VARCHAR(64)                            NOT NULL,
    last_balance_change_index BIGINT                                 NOT NULL,
    mint_sum                  INT,
    spend_sum                 INT,
    last_modified_at          TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT "primary_balance_status"
        PRIMARY KEY (account_id)
);


