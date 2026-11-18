-- Pages and their immutable revisions (spec section 7).
-- The UUID is the permanent identity of a page; title and position may change.
-- MySQL: UUIDs are CHAR(36), timestamps DATETIME(6) in UTC, the document is a JSON column. Tables are
-- utf8mb4_0900_as_cs (case and accent sensitive, like the PostgreSQL original); only the two searched
-- columns are case-insensitive so full-text search finds "postgresql" in "PostgreSQL".

CREATE TABLE page (
    id                      CHAR(36) PRIMARY KEY,
    parent_id               CHAR(36) NULL,
    title                   VARCHAR(200) COLLATE utf8mb4_0900_as_ci NOT NULL,
    document                JSON NOT NULL,
    document_schema_version INTEGER NOT NULL DEFAULT 1,
    search_text             LONGTEXT COLLATE utf8mb4_0900_as_ci NOT NULL DEFAULT (''),
    created_by              VARCHAR(200) NOT NULL,
    updated_by              VARCHAR(200) NOT NULL,
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    deleted_at              DATETIME(6) NULL,
    lock_version            BIGINT NOT NULL DEFAULT 0,
    current_revision        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT page_parent_fk FOREIGN KEY (parent_id) REFERENCES page (id),
    CONSTRAINT page_title_not_blank CHECK (trim(title) <> ''),
    CONSTRAINT page_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- MySQL has no partial indexes; these cover deleted pages too.
CREATE INDEX page_parent_idx ON page (parent_id);
CREATE INDEX page_updated_idx ON page (updated_at DESC);
-- Full-text index for search (spec F-13): whole words, no stemming, so technical terms such as
-- "Spring Boot" and "PostgreSQL" stay searchable unchanged. See SearchService for the known limits.
CREATE FULLTEXT INDEX page_search_idx ON page (title, search_text);

CREATE TABLE page_revision (
    id                      CHAR(36) PRIMARY KEY,
    page_id                 CHAR(36) NOT NULL,
    revision_number         INTEGER NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    document                JSON NOT NULL,
    document_schema_version INTEGER NOT NULL,
    author                  VARCHAR(200) NOT NULL,
    created_at              DATETIME(6) NOT NULL,
    CONSTRAINT page_revision_page_fk FOREIGN KEY (page_id) REFERENCES page (id),
    CONSTRAINT page_revision_unique_number UNIQUE (page_id, revision_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX page_revision_page_idx ON page_revision (page_id, revision_number DESC);
