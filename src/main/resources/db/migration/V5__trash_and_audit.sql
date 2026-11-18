-- Trash (spec F-12): who moved a page to the trash. deleted_at already exists (V2).
ALTER TABLE page ADD COLUMN deleted_by VARCHAR(200);

-- Audit trail (spec section 7): who did what and when for move, delete and restore.
-- No document content and no secrets in the event.
CREATE TABLE audit_event (
    id          CHAR(36) PRIMARY KEY,
    action      VARCHAR(40) NOT NULL,
    page_id     CHAR(36) NULL,
    actor       VARCHAR(200) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    details     VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT audit_event_page_fk FOREIGN KEY (page_id) REFERENCES page (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX audit_event_page_idx ON audit_event (page_id, occurred_at DESC);
