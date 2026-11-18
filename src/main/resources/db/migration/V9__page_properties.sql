-- Eenvoudige properties per pagina (spec U-07) en "laatst gecontroleerd" (spec U-08).
-- owner en reviewed_by zijn usernames, net als created_by/updated_by (geen FK naar app_user).
ALTER TABLE page ADD COLUMN owner VARCHAR(100);
ALTER TABLE page ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTUEEL';
ALTER TABLE page ADD CONSTRAINT page_status_check CHECK (status IN ('CONCEPT', 'ACTUEEL', 'VEROUDERD'));
ALTER TABLE page ADD COLUMN reviewed_at DATETIME(6);
ALTER TABLE page ADD COLUMN reviewed_by VARCHAR(200);
