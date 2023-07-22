CREATE TABLE IF NOT EXISTS users
(
    user_id    UUID PRIMARY KEY          NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    name       VARCHAR(128)              NOT NULL,
    surname    VARCHAR(128)              NOT NULL,
    age        INTEGER                   NOT NULL,
    gender     VARCHAR(128)              NOT NULL,
    city       VARCHAR(128)              NOT NULL
);

CREATE TABLE IF NOT EXISTS user_hobby
(
    user_id UUID
        CONSTRAINT fk_user_id_ref_users
            REFERENCES users,
    hobby   VARCHAR(128),
    PRIMARY KEY (user_id, hobby)
);