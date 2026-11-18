-- Uitnodigen per e-mail (spec F-01: geen openbare registratie; alleen een beheerder nodigt uit).
-- De app_user-rij ontstaat pas bij het accepteren, want de gebruikersnaam kiest de genodigde zelf.
-- Alleen de SHA-256-hash van het token staat hier; het token zelf staat alleen in de mail.
-- Een nieuwe uitnodiging voor hetzelfde adres vervangt de oudere; verlopen rijen worden opgeruimd.
CREATE TABLE user_invitation (
    id          CHAR(36)     PRIMARY KEY,
    email       VARCHAR(200) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    invited_by  VARCHAR(200) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    accepted_at DATETIME(6)  NULL,
    CONSTRAINT user_invitation_role_check CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE UNIQUE INDEX user_invitation_token_hash_idx ON user_invitation (token_hash);
CREATE INDEX user_invitation_email_idx ON user_invitation (email);
