-- Gerelateerde pagina's: een expliciete, gerichte koppeling die redacteuren per pagina bijhouden
-- (rechterkolom "Gerelateerd" op de paginaweergave). Eén rij per (pagina, gerelateerde pagina).
CREATE TABLE page_relation (
    page_id    UUID         NOT NULL REFERENCES page (id),
    related_id UUID         NOT NULL REFERENCES page (id),
    created_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (page_id, related_id),
    CONSTRAINT page_relation_not_self CHECK (page_id <> related_id)
);

CREATE INDEX page_relation_related_idx ON page_relation (related_id);
