CREATE TABLE IF NOT EXISTS friends
(
    user_id UUID
        CONSTRAINT fk_user_id_friends_ref_users
            REFERENCES users,
    friend_id UUID
        CONSTRAINT fk_friend_id_friends_ref_users
            REFERENCES users,
    PRIMARY KEY (user_id, friend_id)
);
