-- Gerelateerde pagina's: een expliciete, gerichte koppeling die redacteuren per pagina bijhouden
-- (rechterkolom "Gerelateerd" op de paginaweergave). Eén rij per (pagina, gerelateerde pagina).
CREATE TABLE page_relation (
    page_id    CHAR(36)     NOT NULL,
    related_id CHAR(36)     NOT NULL,
    created_by VARCHAR(200) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (page_id, related_id),
    CONSTRAINT page_relation_page_fk FOREIGN KEY (page_id) REFERENCES page (id),
    CONSTRAINT page_relation_related_fk FOREIGN KEY (related_id) REFERENCES page (id),
    CONSTRAINT page_relation_not_self CHECK (page_id <> related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX page_relation_related_idx ON page_relation (related_id);
