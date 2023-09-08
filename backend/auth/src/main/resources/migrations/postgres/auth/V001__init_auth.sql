CREATE TABLE logins
(
    login      VARCHAR(128) PRIMARY KEY  NOT NULL,
    password   VARCHAR(128)              NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);
