-- Uitnodigen per e-mail (spec F-01: geen openbare registratie; alleen een beheerder nodigt uit).
-- De app_user-rij ontstaat pas bij het accepteren, want de gebruikersnaam kiest de genodigde zelf.
-- Alleen de SHA-256-hash van het token staat hier; het token zelf staat alleen in de mail.
-- Een nieuwe uitnodiging voor hetzelfde adres vervangt de oudere; verlopen rijen worden opgeruimd.
CREATE TABLE user_invitation (
    id          UUID         PRIMARY KEY,
    email       VARCHAR(200) NOT NULL,
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN')),
    token_hash  VARCHAR(64)  NOT NULL,
    invited_by  VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    accepted_at TIMESTAMPTZ  NULL
);

CREATE UNIQUE INDEX user_invitation_token_hash_idx ON user_invitation (token_hash);
CREATE INDEX user_invitation_email_idx ON user_invitation (email);
