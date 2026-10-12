-- Wachtwoord vergeten (spec F-01, uitbreiding): eenmalige hersteltokens voor de e-maillink.
-- Alleen de SHA-256-hash van het token staat hier; het token zelf staat alleen in de mail.
-- Een nieuwe aanvraag of een geslaagd herstel ruimt de oudere rijen van dezelfde gebruiker op.
CREATE TABLE password_reset_token (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX password_reset_token_hash_idx ON password_reset_token (token_hash);
CREATE INDEX password_reset_token_user_idx ON password_reset_token (user_id);
