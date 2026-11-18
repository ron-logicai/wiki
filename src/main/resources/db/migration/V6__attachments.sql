-- Uploaded files (extension beyond the MVP; rules follow spec U-01): the bytes live on disk outside
-- the public static files, this row holds the metadata. Download only via /attachments/{id} with a
-- session. The file on disk is named after the id, never after the uploaded name.
CREATE TABLE attachment (
    id           CHAR(36) PRIMARY KEY,
    page_id      CHAR(36) NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    uploaded_by  VARCHAR(200) NOT NULL,
    uploaded_at  DATETIME(6) NOT NULL,
    CONSTRAINT attachment_page_fk FOREIGN KEY (page_id) REFERENCES page (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX attachment_page_idx ON attachment (page_id);
