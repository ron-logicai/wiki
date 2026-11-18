-- Wachtwoord vergeten (spec F-01, uitbreiding): eenmalige hersteltokens voor de e-maillink.
-- Alleen de SHA-256-hash van het token staat hier; het token zelf staat alleen in de mail.
-- Een nieuwe aanvraag of een geslaagd herstel ruimt de oudere rijen van dezelfde gebruiker op.
CREATE TABLE password_reset_token (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    CONSTRAINT password_reset_token_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE UNIQUE INDEX password_reset_token_hash_idx ON password_reset_token (token_hash);
CREATE INDEX password_reset_token_user_idx ON password_reset_token (user_id);
