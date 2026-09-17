-- Trash (spec F-12): who moved a page to the trash. deleted_at already exists (V2).
ALTER TABLE page ADD COLUMN deleted_by VARCHAR(200);

-- Audit trail (spec section 7): who did what and when for move, delete and restore.
-- No document content and no secrets in the event.
CREATE TABLE audit_event (
    id          UUID PRIMARY KEY,
    action      VARCHAR(40) NOT NULL,
    page_id     UUID NULL REFERENCES page (id),
    actor       VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    details     VARCHAR(500) NOT NULL DEFAULT ''
);

CREATE INDEX audit_event_page_idx ON audit_event (page_id, occurred_at DESC);
