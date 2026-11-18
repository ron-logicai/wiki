-- Personal favorites (spec U-03): one row per starred page per user.
CREATE TABLE page_favorite (
    page_id    CHAR(36)     NOT NULL,
    username   VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (page_id, username),
    CONSTRAINT page_favorite_page_fk FOREIGN KEY (page_id) REFERENCES page (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX page_favorite_user_idx ON page_favorite (username);
