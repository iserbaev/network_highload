CREATE INDEX IF NOT EXISTS users__user_id_name_surname_text_pattern_ops__covering_idx ON users
    USING btree (name text_pattern_ops, surname text_pattern_ops) INCLUDE
        (user_id, created_at, name, surname, age, city, gender, biography, birthdate);