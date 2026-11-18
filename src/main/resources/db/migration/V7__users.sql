-- Users and roles (spec section 1, F-01, F-02). Admins add users here; there is no public registration.
-- password_hash is NULL for users who only log in through Microsoft Entra (matched on username or e-mail).
CREATE TABLE app_user (
    id            CHAR(36) PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    display_name  VARCHAR(200) NOT NULL,
    email         VARCHAR(200) NULL,
    password_hash VARCHAR(200) NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    created_by    VARCHAR(200) NOT NULL,
    last_login_at DATETIME(6)  NULL,
    CONSTRAINT app_user_role_check CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Unique without regard to case. A unique index allows any number of NULL e-mail addresses.
CREATE UNIQUE INDEX app_user_username_idx ON app_user ((lower(username)));
CREATE UNIQUE INDEX app_user_email_idx ON app_user ((lower(email)));
