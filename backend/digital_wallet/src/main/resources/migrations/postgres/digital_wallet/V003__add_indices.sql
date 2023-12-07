CREATE UNIQUE INDEX IF NOT EXISTS balance_events_log__account_id_change_index__idx
    ON balance_events_log
        USING btree (account_id, change_index);