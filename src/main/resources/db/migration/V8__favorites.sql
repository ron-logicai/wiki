CREATE TABLE page_favourite (
    page_id     UUID         NOT NULL REFERENCES page (id),
    username    VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (page_id, username)

);

CREATE INDEX page_favourite_user_idx   ON page_favourite (username);
