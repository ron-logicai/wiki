-- Baseline migration. Domain tables (page, page_revision, tag, ...) follow in later versions.
-- Keeping an explicit V1 guarantees Flyway creates its history table on the first start.
-- (Under PostgreSQL this created the pgcrypto extension; MySQL needs nothing here.)
SELECT 1;
