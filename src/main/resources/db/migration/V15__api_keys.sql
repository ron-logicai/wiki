-- API keys voor de REST API op /api/v1 (docs/api.md). Een key hoort bij een gebruiker en handelt onder
-- diens naam en rol. Alleen de SHA-256-hash van de key staat hier; de key zelf ziet de gebruiker een keer,
-- direct na het aanmaken. key_prefix is het begin van de key, zodat de gebruiker hem in de lijst herkent.
CREATE TABLE api_key (
    id           CHAR(36)     PRIMARY KEY,
    user_id      CHAR(36)     NOT NULL,
    name         VARCHAR(100) NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    key_prefix   VARCHAR(12)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    last_used_at DATETIME(6)  NULL,
    CONSTRAINT api_key_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE UNIQUE INDEX api_key_token_hash_idx ON api_key (token_hash);
CREATE INDEX api_key_user_idx ON api_key (user_id);
