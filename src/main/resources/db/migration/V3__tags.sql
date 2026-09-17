-- Tags and the many-to-many link with pages (spec F-14).
-- A tag name is unique without regard to case; deleting a tag never deletes pages.

CREATE TABLE tag (
    id         UUID PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    created_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT tag_name_not_blank CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX tag_name_unique ON tag (lower(name));

CREATE TABLE page_tag (
    page_id UUID NOT NULL REFERENCES page (id),
    tag_id  UUID NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (page_id, tag_id)
);

CREATE INDEX page_tag_tag_idx ON page_tag (tag_id);
