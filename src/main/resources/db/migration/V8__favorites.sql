-- Personal favorites (spec U-03): one row per starred page per user.
CREATE TABLE page_favorite (
    page_id    UUID         NOT NULL REFERENCES page (id),
    username   VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (page_id, username)
);

CREATE INDEX page_favorite_user_idx ON page_favorite (username);
