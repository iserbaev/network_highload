CREATE TABLE IF NOT EXISTS friends
(
    user_id   UUID
        CONSTRAINT friends_user_id_fkey
            REFERENCES users (user_id) NOT NULL,
    friend_id UUID
        CONSTRAINT friends_friend_id_fkey
            REFERENCES users (user_id) NOT NULL,
    PRIMARY KEY (user_id, friend_id)
);
