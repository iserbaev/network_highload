CREATE TABLE IF NOT EXISTS phase_status
(
    transaction_id   UUID                                   NOT NULL,
    account_id_from  VARCHAR(64)                            NOT NULL,
    account_id_to    VARCHAR(64)                            NOT NULL,
    from_completed   BOOLEAN                                NOT NULL,
    from_transfer_ts TIMESTAMP,
    to_completed     BOOLEAN                                NOT NULL,
    to_transfer_ts   TIMESTAMP,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    done             BOOLEAN                                NOT NULL,
    CONSTRAINT "primary_phase_status"
        PRIMARY KEY (transaction_id)
);