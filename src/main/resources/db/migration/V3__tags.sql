-- Tags and the many-to-many link with pages (spec F-14).
-- A tag name is unique without regard to case; deleting a tag never deletes pages.

CREATE TABLE tag (
    id         CHAR(36) PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    created_by VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT tag_name_not_blank CHECK (trim(name) <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE UNIQUE INDEX tag_name_unique ON tag ((lower(name)));

CREATE TABLE page_tag (
    page_id CHAR(36) NOT NULL,
    tag_id  CHAR(36) NOT NULL,
    PRIMARY KEY (page_id, tag_id),
    CONSTRAINT page_tag_page_fk FOREIGN KEY (page_id) REFERENCES page (id),
    CONSTRAINT page_tag_tag_fk FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX page_tag_tag_idx ON page_tag (tag_id);
