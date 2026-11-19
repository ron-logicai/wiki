-- OAuth voor de MCP-server (docs/mcp.md): een gebruiker koppelt een AI-agent (Claude, ChatGPT) aan het eigen
-- wiki-account. De wiki is zelf autorisatieserver; dit zijn de drie tabellen van Spring Security's
-- JDBC-opslag (oauth2-registered-client-schema.sql, oauth2-authorization-schema.sql en
-- oauth2-authorization-consent-schema.sql), aangepast aan de MySQL-regels van deze wiki: DATETIME(6) in UTC
-- en utf8mb4_0900_as_cs. De id's blijven VARCHAR(100): Spring genereert ze zelf en het zijn geen UUID's van
-- de wiki. Er hoort geen JPA-entity bij; Spring leest en schrijft ze met JDBC.
--
-- oauth2_registered_client: de apps die zich hebben aangemeld (dynamische registratie).
-- oauth2_authorization:     een koppeling van een gebruiker met een app, met de uitgegeven tokens.
--                           Een rij verwijderen trekt de koppeling in (WikiJwtAuthenticationConverter).
-- oauth2_authorization_consent: de toestemming die de gebruiker aan een app gaf.
-- De sleutel waarmee tokens worden ondertekend staat hier bewust niet (WIKI_MCP_SIGNING_KEY).
CREATE TABLE oauth2_registered_client (
    id                            VARCHAR(100)  NOT NULL,
    client_id                     VARCHAR(100)  NOT NULL,
    client_id_issued_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    client_secret                 VARCHAR(200)  NULL,
    client_secret_expires_at      DATETIME(6)   NULL,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000) NULL,
    post_logout_redirect_uris     VARCHAR(1000) NULL,
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE UNIQUE INDEX oauth2_registered_client_client_id_idx ON oauth2_registered_client (client_id);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE oauth2_authorization (
    id                            VARCHAR(100)  NOT NULL,
    registered_client_id          VARCHAR(100)  NOT NULL,
    principal_name                VARCHAR(200)  NOT NULL,
    authorization_grant_type      VARCHAR(100)  NOT NULL,
    authorized_scopes             VARCHAR(1000) NULL,
    attributes                    BLOB          NULL,
    state                         VARCHAR(500)  NULL,
    authorization_code_value      BLOB          NULL,
    authorization_code_issued_at  DATETIME(6)   NULL,
    authorization_code_expires_at DATETIME(6)   NULL,
    authorization_code_metadata   BLOB          NULL,
    access_token_value            BLOB          NULL,
    access_token_issued_at        DATETIME(6)   NULL,
    access_token_expires_at       DATETIME(6)   NULL,
    access_token_metadata         BLOB          NULL,
    access_token_type             VARCHAR(100)  NULL,
    access_token_scopes           VARCHAR(1000) NULL,
    oidc_id_token_value           BLOB          NULL,
    oidc_id_token_issued_at       DATETIME(6)   NULL,
    oidc_id_token_expires_at      DATETIME(6)   NULL,
    oidc_id_token_metadata        BLOB          NULL,
    refresh_token_value           BLOB          NULL,
    refresh_token_issued_at       DATETIME(6)   NULL,
    refresh_token_expires_at      DATETIME(6)   NULL,
    refresh_token_metadata        BLOB          NULL,
    user_code_value               BLOB          NULL,
    user_code_issued_at           DATETIME(6)   NULL,
    user_code_expires_at          DATETIME(6)   NULL,
    user_code_metadata            BLOB          NULL,
    device_code_value             BLOB          NULL,
    device_code_issued_at         DATETIME(6)   NULL,
    device_code_expires_at        DATETIME(6)   NULL,
    device_code_metadata          BLOB          NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX oauth2_authorization_principal_idx ON oauth2_authorization (principal_name);
CREATE INDEX oauth2_authorization_client_idx ON oauth2_authorization (registered_client_id);
