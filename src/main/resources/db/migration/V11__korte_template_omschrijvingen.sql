-- Kortere omschrijvingen voor de drie meegeleverde templates (teksten uit het ontwerp, scherm "Nieuwe pagina"),
-- zodat de keuzekaarten rustig ogen. Alleen als de omschrijving nog de oorspronkelijke tekst uit V4 is;
-- een door een beheerder aangepaste omschrijving blijft staan.
UPDATE page_template SET description = 'Klant, scope, team en planning.'
WHERE id = '11111111-1111-4111-8111-111111111101'
  AND description = 'Een compleet overzicht van je project: contact, doel, repositorylinks, omgevingen, stack en onderhoud.';

UPDATE page_template SET description = 'Toegang, tools en eerste week.'
WHERE id = '11111111-1111-4111-8111-111111111102'
  AND description = 'Alles wat een nieuwe developer nodig heeft: accounts, ontwikkelomgeving en eerste taken.';

UPDATE page_template SET description = 'Genummerde stappen met uitleg.'
WHERE id = '11111111-1111-4111-8111-111111111103'
  AND description = 'Duidelijke, stapsgewijze instructie: doel, voorwaarden, stappen, controle en gerelateerde links.';
