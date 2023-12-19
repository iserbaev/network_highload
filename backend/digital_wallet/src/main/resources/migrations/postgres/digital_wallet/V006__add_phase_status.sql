CREATE TABLE IF NOT EXISTS phase_status
(
    transaction_id   UUID                                   NOT NULL,
    from_completed   BOOLEAN                                NOT NULL,
    from_transfer_ts TIMESTAMP,
    to_completed     BOOLEAN                                NOT NULL,
    to_transfer_ts   TIMESTAMP,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    CONSTRAINT "primary_phase_status"
        PRIMARY KEY (transaction_id)
);