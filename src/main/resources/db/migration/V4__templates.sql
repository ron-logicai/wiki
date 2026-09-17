-- Page templates (spec F-15): a starting document that is copied into a new page.
-- Later changes to a template never touch pages created from it.

CREATE TABLE page_template (
    id                      UUID PRIMARY KEY,
    title                   VARCHAR(200) NOT NULL,
    description             VARCHAR(300) NOT NULL DEFAULT '',
    document                JSONB NOT NULL,
    document_schema_version INTEGER NOT NULL DEFAULT 1,
    created_by              VARCHAR(200) NOT NULL,
    updated_by              VARCHAR(200) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    lock_version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT page_template_title_not_blank CHECK (btrim(title) <> '')
);

-- The three required example templates (spec section 4). Fixed ids so tests and docs can refer to them.
INSERT INTO page_template (id, title, description, document, created_by, updated_by, created_at, updated_at) VALUES
('11111111-1111-4111-8111-111111111101', 'Projectinformatie',
 'Een compleet overzicht van je project: contact, doel, repositorylinks, omgevingen, stack en onderhoud.',
 $json$[
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Contact","styles":{}}]},
  {"type":"bulletListItem","content":[{"type":"text","text":"Naam — rol · e-mail · telefoon","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Doel","styles":{}}]},
  {"type":"paragraph","content":[{"type":"text","text":"Wat levert dit project op en voor wie? Noem het meetbare doel en de beoogde livegang.","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Repositorylinks","styles":{}}]},
  {"type":"bulletListItem","content":[{"type":"text","text":"repository-naam — wat erin zit (GitHub)","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Omgevingen","styles":{}}]},
  {"type":"bulletListItem","content":[{"type":"text","text":"Productie — adres · live","styles":{}}]},
  {"type":"bulletListItem","content":[{"type":"text","text":"Staging — adres · test","styles":{}}]},
  {"type":"bulletListItem","content":[{"type":"text","text":"Lokaal — adres · dev","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Stack","styles":{}}]},
  {"type":"paragraph","content":[{"type":"text","text":"Talen, frameworks, hosting en tooling, gescheiden door ·","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Onderhoud","styles":{}}]},
  {"type":"bulletListItem","content":[{"type":"text","text":"Taak — frequentie of datum · verantwoordelijke","styles":{}}]}
 ]$json$::jsonb,
 'systeem', 'systeem', now(), now()),
('11111111-1111-4111-8111-111111111102', 'Developer-onboarding',
 'Alles wat een nieuwe developer nodig heeft: accounts, ontwikkelomgeving en eerste taken.',
 $json$[
  {"type":"paragraph","content":[{"type":"text","text":"Welkom! Reken op ongeveer twee dagen voor accounts en omgeving. Je buddy is …","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Accounts","styles":{}}]},
  {"type":"bulletListItem","content":[{"type":"text","text":"Dienst — waarvoor · aanvragen via wie","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Ontwikkelomgeving","styles":{}}]},
  {"type":"numberedListItem","content":[{"type":"text","text":"Installeer de basis — tools en versies","styles":{}}]},
  {"type":"numberedListItem","content":[{"type":"text","text":"Kloon de repositories","styles":{}}]},
  {"type":"numberedListItem","content":[{"type":"text","text":"Controleer dat de applicatie lokaal draait","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Eerste taken","styles":{}}]},
  {"type":"checkListItem","props":{"checked":false},"content":[{"type":"text","text":"Stel je voor aan het team — dag 1","styles":{}}]},
  {"type":"checkListItem","props":{"checked":false},"content":[{"type":"text","text":"Lees de werkinstructies — dag 1","styles":{}}]},
  {"type":"checkListItem","props":{"checked":false},"content":[{"type":"text","text":"Maak één kleine fix — week 1","styles":{}}]}
 ]$json$::jsonb,
 'systeem', 'systeem', now(), now()),
('11111111-1111-4111-8111-111111111103', 'Werkinstructie',
 'Duidelijke, stapsgewijze instructie: doel, voorwaarden, stappen, controle en gerelateerde links.',
 $json$[
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Doel","styles":{}}]},
  {"type":"paragraph","content":[{"type":"text","text":"Wat bereik je met deze instructie en voor wie geldt ze?","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Voorwaarden","styles":{}}]},
  {"type":"bulletListItem","content":[{"type":"text","text":"Wat moet klaar zijn voordat je begint","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Stappen","styles":{}}]},
  {"type":"numberedListItem","content":[{"type":"text","text":"Eerste stap — korte toelichting","styles":{}}]},
  {"type":"codeBlock","content":[{"type":"text","text":"commando","styles":{}}]},
  {"type":"numberedListItem","content":[{"type":"text","text":"Tweede stap — korte toelichting","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Controle","styles":{}}]},
  {"type":"checkListItem","props":{"checked":false},"content":[{"type":"text","text":"Waaraan zie je dat het gelukt is","styles":{}}]},
  {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Gerelateerde links","styles":{}}]},
  {"type":"bulletListItem","content":[{"type":"text","text":"Paginanaam · ruimte","styles":{}}]}
 ]$json$::jsonb,
 'systeem', 'systeem', now(), now());
