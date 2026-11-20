# MCP-server: de wiki koppelen aan AI-agents

De wiki heeft een MCP-server (Model Context Protocol) waarmee een AI-agent zoals Claude of ChatGPT namens een
gebruiker pagina's zoekt, leest, aanmaakt en bijwerkt. Het is een extra buiten de MVP van de stageopdracht (die
vraagt geen AI-functionaliteit) en staat standaard uit: zonder `WIKI_MCP_ENABLED=true` bestaan `/mcp` en de
OAuth-endpoints niet en werkt de wiki zoals voorheen. De wiki roept zelf nooit een AI-dienst aan; de agent komt
naar de wiki, niet andersom.

## Aanzetten

| Variabele | Betekenis |
| --- | --- |
| `WIKI_MCP_ENABLED` | `true` zet `/mcp` en de OAuth-endpoints aan. Standaard `false`. |
| `WIKI_BASE_URL` | Het publieke adres van de wiki. Hieruit komen de issuer, het MCP-adres (`<basis>/mcp`) en alle links die een agent krijgt; nooit uit request-headers. Moet precies het adres zijn dat gebruikers in Claude of ChatGPT invullen. |
| `WIKI_MCP_SIGNING_KEY` | RSA-sleutel (PKCS8 PEM) waarmee de toegangstokens worden ondertekend. Zonder deze variabele maakt de wiki bij elke start een nieuwe sleutel: prima voor lokaal en tests, maar na elke herstart of deploy moet iedereen opnieuw koppelen. Nooit in Git (N-04). |
| `WIKI_MCP_ISSUER` | Alleen nodig als de issuer afwijkt van `WIKI_BASE_URL`. |

Een sleutel maken en op Fly zetten:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out mcp-signing-key.pem
fly secrets set WIKI_MCP_ENABLED=true WIKI_MCP_SIGNING_KEY="$(cat mcp-signing-key.pem)"
```

De overige instellingen staan in `application.yaml` onder `wiki.mcp`: levensduur van tokens (30 minuten, verversen
tot 30 dagen), de toegestane terugkeeradressen en het maximum aantal aangemelde apps.

## Koppelen

Het adres is steeds `<WIKI_BASE_URL>/mcp`, bijvoorbeeld `https://logicai-wiki.fly.dev/mcp`. De app stuurt je naar
de wiki, je logt in met je eigen account (wachtwoord of Microsoft) en kiest **Toestaan**. Claude en ChatGPT
bereiken alleen een wiki die via HTTPS op internet staat; lokaal testen kan met Claude Code of de MCP Inspector.

- **Claude (claude.ai, Desktop):** *Customize > Connectors > + Add > Add custom connector*, naam en het adres
  invullen en de stappen volgen. Bij Team en Enterprise voegt een Owner de connector toe onder *Organization
  settings > Connectors*; leden klikken daarna op *Connect*.
- **Claude Code:** `claude mcp add --transport http wiki <WIKI_BASE_URL>/mcp`, daarna `/mcp` in Claude Code en
  inloggen in de browser.
- **ChatGPT:** een eigen MCP-connector toevoegen met het adres en OAuth als aanmeldmethode (onder de instellingen
  voor apps en connectors, met ontwikkelaarsmodus aan; de menunamen wisselen per abonnement en veranderen
  geregeld, zie de hulppagina's van OpenAI). De tools `search` en `fetch` hebben de vorm die ChatGPT voor deep
  research en bedrijfskennis verwacht.
- **MCP Inspector (testen):** `npx @modelcontextprotocol/inspector`, transport *Streamable HTTP*, het adres invullen.

Een koppeling intrekken kan de gebruiker zelf op **Account > Gekoppelde apps** (`/account/koppelingen`) en een
beheerder op **Beheer > Koppelingen** (`/admin/koppelingen`). Intrekken werkt direct.

## Tools

Lezen mag iedere actieve gebruiker; wijzigen vraagt de rol Editor, net als in de webinterface.

| Tool | Wat het doet | Rol |
| --- | --- | --- |
| `search` | Zoeken in titels en inhoud; geeft `id`, `title`, `url` (vorm voor ChatGPT) | Viewer |
| `fetch` | Eén pagina: `text` is de inhoud als Markdown (vorm voor ChatGPT) | Viewer |
| `search_pages` | Zoeken met pad, tekstfragment, tags, filter op tag en paginering | Viewer |
| `get_page` | Pagina met metadata, subpagina's, gerelateerde pagina's, `version` en Markdown | Viewer |
| `list_pages` | De paginaboom, vanaf de top of onder een pagina | Viewer |
| `list_recent_changes`, `list_tags`, `list_templates` | Overzichten | Viewer |
| `create_page` | Nieuwe pagina met Markdown of vanuit een template, optioneel met tags | Editor |
| `update_page` | Inhoud (en titel) vervangen; vraagt `baseVersion` | Editor |
| `append_to_page` | Inhoud onderaan toevoegen; bestaande inhoud blijft ongemoeid | Editor |
| `add_tag`, `remove_tag` | Tag koppelen of ontkoppelen | Editor |

Bewust niet aangeboden: verplaatsen, verwijderen en herstellen, paginaeigenschappen, bijlagen uploaden en alles
rond gebruikers. Dat blijft mensenwerk.

Regels die ongewijzigd gelden, omdat de tools dezelfde services gebruiken als de webinterface:

- **Versiecontrole (F-10):** `update_page` en `append_to_page` vragen de `version` uit `get_page` als `baseVersion`.
  Is de pagina intussen gewijzigd, dan volgt de fout `CONFLICT` en verandert er niets.
- **Revisies (F-11):** elke wijziging is een revisie op naam van de gebruiker en is terug te zetten. Daarnaast komt
  er een auditregel (`page.mcp.create`, `page.mcp.update`) met de naam van de app.
- **Validatie:** de inhoud gaat door `DocumentValidator`, zoals bij opslaan in de editor.
- Fouten komen terug als toolfout met een code: `CONFLICT`, `NOT_FOUND`, `INVALID`, `FORBIDDEN`, `FORMATTING_LOSS`.
  Toolbeschrijvingen en foutcodes zijn Engels (een model leest ze); de pagina's voor mensen zijn Nederlands.

## Markdown als uitwisselformaat

Een agent leest en schrijft Markdown; de wiki bewaart BlockNote-blokken. `MarkdownExporter` vertaalt de ene kant
op (zie `markdown-export.md`), `MarkdownImporter` de andere. De importer leest alles terug wat de exporter
schrijft. Markdown waar de wiki geen blok voor heeft, wordt nooit geweigerd maar wordt tekst, met een waarschuwing
in het antwoord: een tabel blijft als voorgeformatteerde tekst staan, ruwe HTML als letterlijke tekst, een externe
afbeelding als link en een niet-toegestane link als gewone tekst.

De export is verliesgevend: kleuren, de gekozen breedte van een afbeelding, uitlijning en inspringen onder een
alinea hebben geen Markdown-vorm. Een agent die een pagina herschrijft vanuit die Markdown zou dat verlies
blijvend maken. Daarom:

1. `update_page` vervangt alleen de blokken die echt veranderd zijn. Een blok waarvan de Markdown ongewijzigd
   terugkomt, blijft exact zoals het was opgeslagen, met kleuren, breedte en blok-id.
2. `get_page` noemt onder `warnings` welke blokken zulke opmaak hebben. Wijzigt de agent juist zo'n blok, dan
   weigert `update_page` met `FORMATTING_LOSS`, tenzij `allowFormattingLoss=true` wordt meegegeven.
3. `append_to_page` raakt bestaande blokken niet aan en is de aanbevolen weg om iets toe te voegen.

## Beveiliging

De wiki is zelf OAuth 2.1-autorisatieserver; er is geen losse identiteitsdienst. De MCP-server accepteert geen
API keys; die zijn er alleen voor de REST API op `/api/v1` (zie `api.md`).

- **De agent is de gebruiker.** Een token staat op naam van één wiki-gebruiker. Bij elk verzoek aan `/mcp` wordt
  die gebruiker opnieuw uit `app_user` gelezen (F-02, N-01): een gedeactiveerde of verwijderde gebruiker wordt
  direct geweigerd en een rolwijziging geldt direct, want de rol staat niet in het token.
- **Intrekken werkt direct.** Het token verwijst naar de koppeling; bestaat die niet meer, dan is het token
  ongeldig. Een koppeling vervalt ook als het wachtwoord wijzigt of wordt hersteld en als de gebruiker wordt
  gedeactiveerd of verwijderd.
- **Toestemming.** Na het inloggen ziet de gebruiker welke app toegang vraagt en naar welk adres die terugstuurt.
  De naam komt van de app zelf en is dus geen bewijs; het adres komt van de toegestane lijst.
- **Aanmelden van apps is open, maar begrensd.** Claude en ChatGPT melden zichzelf aan als OAuth-client zonder
  dat iemand is ingelogd (zo werken MCP-connectors). Aanmelden geeft nergens toegang toe; pas na inloggen en
  toestemming van een gebruiker kan een app iets. Een app mag gebruikers alleen terugsturen naar een adres uit
  `wiki.mcp.allowed-redirect-uris` (Claude, ChatGPT en `localhost`), moet PKCE gebruiken, en er geldt een maximum
  aantal apps en een limiet per adres per uur. Een andere MCP-client toelaten betekent zijn terugkeeradres aan
  die lijst toevoegen.
- **Tokens.** Het toegangstoken is een ondertekende JWT van 30 minuten. Het ververstoken is eenmalig: bij elk
  gebruik komt er een nieuw en vervalt het oude, zodat een gekopieerd token waardeloos wordt zodra de echte app
  ververst. Claude en ChatGPT hebben geen clientgeheim; Spring geeft zulke apps standaard geen ververstoken,
  dat voegt `McpPublicClientRefresh` toe (toegestaan in OAuth 2.1 mits eenmalig).
- **Geen sessie, geen CSRF op `/mcp`.** Dat endpoint accepteert alleen een bearer-token; een browser stuurt dat
  niet vanzelf mee. Voor de rest van de site blijft CSRF ongewijzigd aan (N-02).
- **Opslag.** De drie OAuth-tabellen (V14) bevatten geen wachtwoorden en geen Microsoft-tokens. De signeersleutel
  staat niet in de database, omdat het profiel `local` en productie dezelfde database delen.

## Bekende beperkingen

- De registratielimiet per adres staat in het geheugen van één proces; genoeg voor de ene Fly-machine.
- Hergebruik van een oud ververstoken wordt geweigerd, maar trekt de koppeling niet vanzelf in.
- ChatGPT's nieuwere aanmeldvorm zonder registratie (Client ID Metadata Documents) staat uit; ChatGPT en Claude
  gebruiken dan dynamische registratie. Getest zijn de volledige flow in `McpOAuthFlowIntegrationTest` (zoals een
  client hem doorloopt) en de tools in `McpToolsIntegrationTest`; een koppeling vanuit claude.ai of ChatGPT zelf
  kan pas worden geprobeerd als de functie op het publieke adres aan staat.
- De OAuth-laag gebruikt naast Spring Security de communitybibliotheek `spring-ai-community/mcp-security`
  (nog niet officieel onderdeel van Spring); de versie staat vast in `pom.xml` en het gebruik zit in
  `McpSecurityConfig`.
