CREATE SEQUENCE posts_index_seq CYCLE;

CREATE TABLE IF NOT EXISTS posts
(
    user_id    UUID
        CONSTRAINT posts_user_id_fkey
            REFERENCES users (user_id)                        NOT NULL,
    post_id    UUID        DEFAULT gen_random_uuid()          NOT NULL,
    index      BIGINT      DEFAULT nextval('posts_index_seq') NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()                      NOT NULL,
    text       TEXT                                           NOT NULL,
    PRIMARY KEY (user_id, post_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS posts__id__idx ON posts USING btree (post_id) INCLUDE (user_id, post_id, created_at, text);
CREATE INDEX IF NOT EXISTS posts__user_id_created_at__covering_idx ON posts USING btree (user_id, created_at) INCLUDE (user_id, post_id, created_at, text);

