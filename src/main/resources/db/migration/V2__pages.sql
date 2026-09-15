-- Pages and their immutable revisions (spec section 7).
-- The UUID is the permanent identity of a page; title and position may change.

CREATE TABLE page (
    id                      UUID PRIMARY KEY,
    parent_id               UUID NULL REFERENCES page (id),
    title                   VARCHAR(200) NOT NULL,
    document                JSONB NOT NULL,
    document_schema_version INTEGER NOT NULL DEFAULT 1,
    search_text             TEXT NOT NULL DEFAULT '',
    created_by              VARCHAR(200) NOT NULL,
    updated_by              VARCHAR(200) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    deleted_at              TIMESTAMPTZ NULL,
    lock_version            BIGINT NOT NULL DEFAULT 0,
    current_revision        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT page_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT page_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX page_parent_idx ON page (parent_id) WHERE deleted_at IS NULL;
CREATE INDEX page_updated_idx ON page (updated_at DESC) WHERE deleted_at IS NULL;
-- 'simple' keeps technical terms such as "Spring Boot" and "PostgreSQL" searchable unchanged.
CREATE INDEX page_search_idx ON page USING GIN (to_tsvector('simple', title || ' ' || search_text));

CREATE TABLE page_revision (
    id                      UUID PRIMARY KEY,
    page_id                 UUID NOT NULL REFERENCES page (id),
    revision_number         INTEGER NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    document                JSONB NOT NULL,
    document_schema_version INTEGER NOT NULL,
    author                  VARCHAR(200) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT page_revision_unique_number UNIQUE (page_id, revision_number)
);

CREATE INDEX page_revision_page_idx ON page_revision (page_id, revision_number DESC);
