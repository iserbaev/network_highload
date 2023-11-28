CREATE SEQUENCE change_index_seq CYCLE;

CREATE TABLE IF NOT EXISTS balance_commands_log
(
    distributed_transaction_id UUID                                   NOT NULL,
    account_id_from            UUID                                   NOT NULL,
    account_id_to              UUID                                   NOT NULL,
    amount                     INT                                    NOT NULL,
    currency_code_letter       VARCHAR(3)                             NOT NULL,
    change_description         TEXT,
    created_at                 TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT "primary_balance_commands_log"
        PRIMARY KEY (distributed_transaction_id)
);

CREATE TABLE IF NOT EXISTS balance_events_log
(
    account_id                 UUID                                                         NOT NULL,
    distributed_transaction_id UUID                                                         NOT NULL,
    mint_change                INT,
    spend_change               INT,
    change_description         TEXT,
    change_index               BIGINT                   DEFAULT nextval('change_index_seq') NOT NULL,
    created_at                 TIMESTAMP WITH TIME ZONE DEFAULT now()                       NOT NULL,
    CONSTRAINT "primary_balance_events_log"
        PRIMARY KEY (account_id, distributed_transaction_id)
);

create table if not exists balance_snapshot
(
    account_id                UUID                                   NOT NULL,
    last_balance_change_index BIGINT                                 NOT NULL,
    mint_sum                  INT,
    spend_sum                 INT,
    last_modified_at          TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT "primary_balance_status"
        PRIMARY KEY (account_id)
);


