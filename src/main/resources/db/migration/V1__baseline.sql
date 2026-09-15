-- Baseline migration. Domain tables (page, page_revision, tag, ...) follow in later versions.
-- Keeping an explicit V1 guarantees Flyway creates its history table on the first start.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
