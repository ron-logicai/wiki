-- Uploaded files (extension beyond the MVP; rules follow spec U-01): the bytes live on disk outside
-- the public static files, this row holds the metadata. Download only via /attachments/{id} with a
-- session. The file on disk is named after the id, never after the uploaded name.
CREATE TABLE attachment (
    id           UUID PRIMARY KEY,
    page_id      UUID NULL REFERENCES page (id),
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    uploaded_by  VARCHAR(200) NOT NULL,
    uploaded_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX attachment_page_idx ON attachment (page_id);
