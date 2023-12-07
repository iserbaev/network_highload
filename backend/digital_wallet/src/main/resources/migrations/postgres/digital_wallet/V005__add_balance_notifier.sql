CREATE OR REPLACE FUNCTION balance_updates_log_notify()
    RETURNS trigger AS
$$
BEGIN
    PERFORM pg_notify('balance_log_channel', json_build_object('event', row_to_json(NEW))::text);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER balance_events_log_trigger
    AFTER INSERT
    ON balance_events_log
    FOR EACH ROW
EXECUTE PROCEDURE balance_updates_log_notify();


CREATE OR REPLACE FUNCTION balance_commands_log_notify()
    RETURNS trigger AS
$$
BEGIN
    PERFORM pg_notify('balance_log_channel', json_build_object('command', row_to_json(NEW))::text);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER balance_commands_log_trigger
    AFTER INSERT
    ON balance_commands_log
    FOR EACH ROW
EXECUTE PROCEDURE balance_commands_log_notify();
