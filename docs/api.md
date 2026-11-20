# REST API: pagina's beheren met een API key

De wiki heeft een REST API op `/api/v1` waarmee een script of integratie pagina's aanmaakt, leest, wijzigt en
verwijdert. Toegang kan alleen met een API key. Het is een extra buiten de MVP van de stageopdracht; de regels
van de wiki blijven gelden omdat de API dezelfde services gebruikt als de webinterface.

Niet te verwarren met de endpoints onder `/api/pages`, `/api/templates` en `/api/attachments`: die zijn van de
editor in de browser, werken met de inlogsessie en accepteren geen API key.

## Een key aanmaken

Op **Account > API keys** (`/account/api-keys`) geef je de key een naam en kies je **Key aanmaken**. Dat vraagt de
rol Editor of hoger. De key (`wiki_` gevolgd door 43 tekens) staat één keer op de pagina; daarna is hij niet meer
op te vragen. Je kunt maximaal 10 keys hebben.

Een key hoort bij jou: hij heeft jouw rechten en wijzigingen staan op jouw naam. Intrekken kan op dezelfde
pagina; een beheerder ziet alle keys op **Beheer > API keys** (`/admin/api-keys`) en kan ze daar intrekken.

## Aanroepen

Stuur de key mee in de header `Authorization: Bearer <key>`. Het adres is `<WIKI_BASE_URL>/api/v1`. Verzoeken en
antwoorden zijn JSON; de inhoud van een pagina is Markdown.

| Methode | Pad | Body of parameters | Antwoord |
| --- | --- | --- | --- |
| `GET` | `/api/v1/pages` | optioneel `?parentId=` | De pagina's op het hoogste niveau, of de subpagina's van `parentId`: `id`, `title`, `url`, `version` |
| `GET` | `/api/v1/pages/{id}` | | De pagina met `markdown` |
| `POST` | `/api/v1/pages` | `title`, optioneel `parentId`, `markdown`, `templateId`, `tags` | `201` met de pagina |
| `PUT` | `/api/v1/pages/{id}` | `baseVersion`, `markdown`, optioneel `title`, `allowFormattingLoss` | De pagina; de inhoud is vervangen |
| `POST` | `/api/v1/pages/{id}/append` | `baseVersion`, `markdown` | De pagina; de inhoud is onderaan toegevoegd |
| `DELETE` | `/api/v1/pages/{id}?baseVersion=` | | `204`; de pagina staat in de prullenbak |

Lezen mag met elke geldige key; aanmaken, wijzigen en verwijderen vraagt de rol Editor.

Een pagina in een antwoord heeft de velden `id`, `title`, `url`, `path` (de titels van de bovenliggende pagina's),
`parentId`, `version`, `revision`, `status`, `owner`, `tags`, `updatedAt` en `updatedBy`. `version` is de waarde
die je bij een wijziging of bij verwijderen als `baseVersion` terugstuurt; `revision` is het nummer in de
geschiedenis van de pagina.

```bash
KEY=wiki_...
WIKI=https://logicai-wiki.fly.dev

# Aanmaken
curl -X POST "$WIKI/api/v1/pages" -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -d '{"title": "Releasenotes", "markdown": "Eerste versie.", "tags": ["release"]}'

# Lezen: geeft onder meer "version" en "markdown"
curl "$WIKI/api/v1/pages/<id>" -H "Authorization: Bearer $KEY"

# Inhoud vervangen
curl -X PUT "$WIKI/api/v1/pages/<id>" -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -d '{"baseVersion": 1, "markdown": "Nieuwe inhoud."}'

# Alleen iets toevoegen
curl -X POST "$WIKI/api/v1/pages/<id>/append" -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -d '{"baseVersion": 2, "markdown": "## Versie 2\n\nNog een alinea."}'

# Verwijderen (naar de prullenbak)
curl -X DELETE "$WIKI/api/v1/pages/<id>?baseVersion=3" -H "Authorization: Bearer $KEY"
```

`POST /api/v1/pages` accepteert `markdown` of `templateId`, niet allebei. Zonder beide is de pagina leeg. Een
nieuwe pagina krijgt de status Concept en jou als eigenaar.

## Fouten

Een fout is JSON met een melding voor mensen en een code voor het script: `{"error": "...", "code": "..."}`.

| Status | Code | Wanneer |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | Geen key, een onbekende of ingetrokken key, of de gebruiker is gedeactiveerd |
| 403 | `FORBIDDEN` | De rol van de gebruiker staat de wijziging niet toe |
| 404 | `NOT_FOUND` | Geen actieve pagina (of template) met dit id; een pagina in de prullenbak telt als niet gevonden |
| 400 | `INVALID` | Ongeldige body, ontbrekende `baseVersion` of titel, inhoud die de wiki niet toelaat |
| 409 | `CONFLICT` | De pagina is intussen gewijzigd; `currentVersion` geeft de actuele versie. Lees opnieuw en probeer het nog eens |
| 409 | `HAS_SUBPAGES` | Verwijderen van een pagina die nog actieve subpagina's heeft |
| 409 | `FORMATTING_LOSS` | De wijziging zou opmaak verwijderen die Markdown niet kent; `losses` noemt welke |

## Regels die ongewijzigd gelden

- **Versiecontrole (F-10):** wijzigen en verwijderen vragen `baseVersion`. Bij een verouderde versie verandert er
  niets.
- **Revisies (F-11):** elke wijziging is een revisie op naam van de gebruiker en is terug te zetten in de wiki.
  Daarnaast komt er een auditregel (`page.api.create`, `page.api.update`) met de naam van de key; verwijderen
  geeft de gewone regel `page.delete`.
- **Verwijderen is de prullenbak.** Een editor zet de pagina terug op `/trash`. Definitief verwijderen bestaat
  niet.
- **Validatie:** de inhoud gaat door `DocumentValidator`, zoals bij opslaan in de editor.
- **Markdown:** hetzelfde uitwisselformaat als de MCP-server, zie "Markdown als uitwisselformaat" in `mcp.md`.
  Blokken waarvan de Markdown ongewijzigd terugkomt, blijven exact zoals ze waren. `GET` noemt onder `warnings`
  welke blokken opmaak hebben die Markdown niet toont; wijzig je juist zo'n blok, dan volgt `FORMATTING_LOSS`,
  tenzij je `"allowFormattingLoss": true` meestuurt.

## Beveiliging

- **Alleen een key.** `/api/v1` heeft een eigen keten zonder sessie en zonder inlogpagina (`ApiSecurityConfig`).
  Een ingelogde browser krijgt er `401`; CSRF speelt niet, want een browser stuurt de header niet vanzelf mee.
- **De key is de gebruiker.** Bij elk verzoek wordt de gebruiker opnieuw uit `app_user` gelezen (F-02): een
  gedeactiveerde of verwijderde gebruiker wordt direct geweigerd en een rolwijziging geldt direct.
- **Opslag (N-04).** Alleen de SHA-256-hash van de key staat in de tabel `api_key` (V15), plus de eerste 12
  tekens om hem in de lijst te herkennen.
- **Intrekken werkt direct.** Alle keys van een gebruiker vervallen ook als het wachtwoord wijzigt of wordt
  hersteld en als de gebruiker wordt gedeactiveerd of verwijderd. Aanmaken en intrekken staan in de audit
  (`api.key.create`, `api.key.revoke`).
- Gebruik de API alleen over HTTPS; de key staat in elke aanroep.

## Bekende beperkingen

- Alleen pagina's: verplaatsen, herstellen uit de prullenbak, eigenschappen, bijlagen, templates en zoeken zitten
  niet in de API.
- Een key heeft geen verloopdatum en er is geen limiet op het aantal verzoeken.
- "Laatst gebruikt" wordt hooguit eens per vijf minuten bijgewerkt.
- Getest in `RestApiKeyIntegrationTest`, over echte HTTP.
