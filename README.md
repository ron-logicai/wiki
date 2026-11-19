# LogicAI Wiki

Interne kenniswiki voor LogicAI / Code Art BV: pagina's, blokken en blijvende links.
Gebouwd als stageopdracht softwareontwikkeling (opdracht v1.0, 15 september 2026).

## Stack

| Onderdeel | Keuze |
|---|---|
| Runtime | Java 25 LTS (Eclipse Temurin) |
| Backend | Spring Boot 4.1.x: Spring MVC, Data JPA, Security (OIDC), Validation |
| HTML | Thymeleaf, server-side gerenderd; vaste URL's per pagina |
| Database | MySQL 9.1, JSON-kolom voor documentinhoud, FULLTEXT-index voor zoeken, Flyway-migraties |
| Editor | BlockNote als lokale React-island (TypeScript + Vite), geen client-side router |
| Tests | JUnit, MockMvc, Testcontainers (MySQL 9.1), Playwright |
| Uitrol | Dockerfile + Docker Compose, Fly.io (`fly.toml`), GitHub Actions CI |

## Vereisten voor ontwikkelen

- Eclipse Temurin JDK 25: <https://adoptium.net/temurin/releases/?version=25>
- Node.js 26 en npm (alleen buildhulpmiddel voor de editor)
- Docker Desktop (voor Testcontainers, en voor een lokale MySQL via Compose)

## Snel starten

```bash
# 1. Database: een lokale MySQL-container (of zet WIKI_DB_* in .env voor een andere server)
cp .env.example .env
docker compose up -d db

# 2. Editor-bundle bouwen (schrijft naar src/main/resources/static/editor)
cd frontend && npm ci && npm run build && cd ..

# 3. Applicatie starten met het lokale profiel (geen HTTPS-cookies)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

De applicatie draait op <http://localhost:8080>. Healthcheck: <http://localhost:8080/actuator/health>.

Inloggen gaat met e-mailadres en wachtwoord. Met het profiel `local` bestaan drie testgebruikers,
allemaal met wachtwoord `wiki`: `viewer@wiki.nl`, `editor@wiki.nl` en `admin@wiki.nl`. Ze worden bij
het opstarten in de tabel `app_user` gezet als ze ontbreken; buiten dit profiel gebeurt dat nooit, dus
in productie bestaan ze niet.

Gebruikers en rollen beheer je als admin op `/admin/users` (klik onderin de zijbalk op je eigen naam).
Een nieuwe gebruiker nodig je uit per e-mail: je vult alleen het adres en de rol in, de genodigde kiest
zelf een naam en wachtwoord via de link in de mail (`/accountaanmaken?token=`, één uur geldig, eenmalig)
en logt daarna in met het uitgenodigde e-mailadres. Zonder `WIKI_MAIL_HOST` staat die link in de log.
Overal in de wiki staat de weergavenaam; de kolom `username` in `app_user` is alleen nog een interne
sleutel (afgeleid van het deel vóór de `@`) voor pagina's, favorieten en de audit.
Een verse installatie zonder `local`-profiel heeft één keer `WIKI_BOOTSTRAP_ADMIN_EMAIL` nodig
(met `WIKI_BOOTSTRAP_ADMIN_PASSWORD` voor inloggen met wachtwoord, zonder wachtwoord alleen via Entra;
`WIKI_BOOTSTRAP_ADMIN_NAME` is optioneel); die admin wordt alleen aangemaakt zolang de gebruikerstabel
leeg is. Entra-accounts komen alleen binnen als hun `preferred_username` of e-mailadres overeenkomt met
een actieve gebruiker. Een account van vóór de e-mail-login dat nog geen adres heeft, kan met de oude
gebruikersnaam blijven inloggen tot een beheerder op `/admin/users` een e-mailadres instelt.

Browserbeveiliging (spec N-02): CSRF-bescherming staat aan voor alle formulieren en voor de
fetch-verzoeken van de editor (het token staat als meta-tag in de pagina en gaat mee als header).
De sessiecookie is HttpOnly, Secure en SameSite=Lax; alleen het profiel `local` zet Secure uit omdat
daar geen HTTPS is. Lax is bewust gekozen: de Entra-callback naar `/login/oauth2/code/entra` is een
top-level GET vanaf een andere site, en Strict zou die cookie laten vallen. Productie draait daarom
altijd achter HTTPS. De loginflow (wachtwoord en Entra) wordt in `src/test/java/.../security` over
echte HTTP getest, met een nagebootste Entra in de test.

Tijdens frontend-ontwikkeling herbouwt `npm run dev` de bundle bij elke wijziging;
Spring Boot serveert de bestanden onder `/editor/`.

## Database

De wiki gebruikt MySQL 9.1. Welke database de app gebruikt, bepalen `WIKI_DB_URL`, `WIKI_DB_USERNAME` en
`WIKI_DB_PASSWORD`:

- **Gedeelde server** (`database.enabl.sh`, database `wiki`): lokaal en Fly-productie gebruiken dezelfde
  database. De drie waarden staan in `.env` (lokaal) en in `fly.toml` plus een Fly-secret (productie),
  nooit in Git. Let op: wat je lokaal wijzigt, wijzigt productie, en een nieuwe Flyway-migratie die je
  lokaal start draait meteen op de productiedatabase.
- **Lokale container** (voor experimenten, nieuwe migraties en de browsertests): laat de drie regels weg
  uit `.env`; de app gebruikt dan `jdbc:mysql://localhost:3306/wiki` met gebruiker en wachtwoord `wiki`,
  de MySQL-container uit `docker-compose.yml`. De gegevens staan in het Docker-volume `wiki-mysql-data` en
  blijven staan tot `docker compose down -v`.

```bash
docker compose up -d db          # lokale MySQL starten (Flyway vult het schema bij de eerste app-start)
docker compose stop db           # stoppen, gegevens blijven staan
docker compose down -v           # ALLES wissen: containers én volume
docker compose exec db mysqldump -uwiki -pwiki wiki > wiki-dev.sql      # kopie maken
docker compose exec -T db mysql -uwiki -pwiki wiki < wiki-dev.sql       # terugzetten
```

Elke JDBC-URL heeft `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` nodig (tijdstempels staan
als UTC in `DATETIME(6)`), en `sslMode=REQUIRED` voor een server die niet op je eigen machine staat.
Geüploade bestanden staan bij een lokale run niet in de database maar in `./data/attachments`
(zie `WIKI_ATTACHMENTS_DIR`); die map staat in `.gitignore`.

Afwijking van de stageopdracht: sectie 6 schrijft PostgreSQL 18 met JSONB voor. Het project is in november
2026 op verzoek omgezet naar MySQL: UUID's zijn `CHAR(36)`, het document staat in een `JSON`-kolom en zoeken
gebruikt een `FULLTEXT`-index.

Bekende zoekbeperkingen (spec F-13, sectie 7): alleen hele woorden, geen stammen of voorvoegsels; een woord
is een reeks letters, cijfers en underscores, dus "node.js" zoekt op "node". MySQL indexeert geen woorden
korter dan drie tekens en geen stopwoorden (waaronder "de" en "en"); die tellen in een zoekvraag niet mee.
Bestaat een zoekvraag alleen uit zulke woorden ("AI", "C#"), dan zoekt de wiki op de letterlijke tekst in
titel en inhoud, nieuwste eerst.

### De oude PostgreSQL-gegevens

Tot november 2026 draaide de wiki op PostgreSQL. De gegevens van de lokale database en van Fly-productie
zijn samengevoegd en overgezet naar MySQL; de bronnen zijn niet gewijzigd en dienen als back-up:

- lokaal: Docker-volume `wiki_wiki-db-data` (wachtwoord in `.env` als `WIKI_OLD_POSTGRES_PASSWORD`), te
  starten met `docker run -d --name wiki-oude-postgres -p 127.0.0.1:5432:5432 -v wiki_wiki-db-data:/var/lib/postgresql postgres:18`;
- Fly: de app `logicai-wiki-db` met volume `wiki_db_data`. Verwijder die pas (`fly apps destroy logicai-wiki-db`)
  als productie een tijd goed op MySQL draait.

## Tests

```bash
./mvnw verify                         # unit-, integratie- en MVC-tests (start mysql:9.1 via Testcontainers)
cd frontend && npm run typecheck      # TypeScript
cd frontend && npx playwright test    # browserflows tegen een draaiende app op :8080
```

De tests draaien nooit tegen de gedeelde server. Start voor de browsertests de app tegen de lokale container,
anders komen de testpagina's in de echte wiki:

```bash
docker compose up -d db
WIKI_DB_URL='jdbc:mysql://localhost:3306/wiki?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
  WIKI_DB_USERNAME=wiki WIKI_DB_PASSWORD=wiki ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Configuratie

Alle instellingen komen uit omgevingsvariabelen; zie `.env.example`. Er staan geen secrets in Git.

| Variabele | Betekenis |
|---|---|
| `WIKI_DB_URL`, `WIKI_DB_USERNAME`, `WIKI_DB_PASSWORD` | MySQL-verbinding (zie "Database"); zonder deze drie de lokale container |
| `SPRING_PROFILES_ACTIVE` | `local` (ontwikkeling) en/of `entra` (bedrijfslogin) |
| `WIKI_ENTRA_TENANT_ID`, `WIKI_ENTRA_CLIENT_ID`, `WIKI_ENTRA_CLIENT_SECRET` | Microsoft Entra ID app-registratie |
| `WIKI_ATTACHMENTS_DIR` | Map voor geüploade bestanden (standaard `./data/attachments`; in Compose het volume `wiki-attachments`) |
| `WIKI_UPLOAD_MAX_FILE_SIZE` | Maximale grootte per afbeelding (PNG, JPEG) of PDF (standaard `10MB`, spec U-01) |
| `WIKI_UPLOAD_MAX_VIDEO_SIZE` | Maximale grootte per video (MP4, WebM; standaard `100MB`) |
| `WIKI_BASE_URL` | Publieke URL van de wiki, voor de links in e-mail (standaard `http://localhost:8080`) |
| `WIKI_INVITATION_TTL` | Geldigheid van een uitnodigingslink (standaard `1h`) |
| `WIKI_MCP_ENABLED`, `WIKI_MCP_SIGNING_KEY` | MCP-server voor AI-agents (Claude, ChatGPT) op `/mcp`, met OAuth-login op het eigen wiki-account; standaard uit. Zie `docs/mcp.md` |
| `WIKI_MAIL_GRAPH_SENDER` | Mail via Microsoft Graph: de mailbox waaruit de wiki "wachtwoord vergeten" en uitnodigingen verstuurt, als applicatie met de Entra-appregistratie (`WIKI_ENTRA_*`); geen mailboxwachtwoord nodig en gaat vóór SMTP |
| `WIKI_MAIL_HOST`, `WIKI_MAIL_PORT`, `WIKI_MAIL_STARTTLS`, `WIKI_MAIL_USERNAME`, `WIKI_MAIL_PASSWORD`, `WIKI_MAIL_FROM` | SMTP als alternatief (Gmail of een andere server, voorbeelden in `.env.example`); zonder `WIKI_MAIL_GRAPH_SENDER` én zonder `WIKI_MAIL_HOST` wordt de link in de log geschreven in plaats van gemaild |

Met profiel `local` leest de app `.env` zelf (`spring.config.import` in `application.yaml`), dus `./mvnw spring-boot:run`
en IntelliJ gebruiken dezelfde waarden als Docker Compose. De tests draaien zonder profiel en lezen `.env` niet.

### Mail via Microsoft Graph (Microsoft 365)

De wiki mailt als applicatie vanuit één mailbox, bijvoorbeeld een gedeelde mailbox `wiki@logicai.nl` (gratis,
zonder licentie en zonder wachtwoord). Het enige geheim is het client secret van de Entra-appregistratie, dat voor
"Inloggen met Microsoft" toch al nodig is. Inrichting door de tenantbeheerder, eenmalig:

1. Gedeelde mailbox aanmaken (Microsoft 365-beheer → Teams en groepen → Gedeelde postvakken).
2. Op de appregistratie: API-machtigingen → Machtiging toevoegen → Microsoft Graph → **Toepassingsmachtigingen**
   → `Mail.Send` → Beheerderstoestemming verlenen.
3. De app beperken tot die ene mailbox, anders mag ze uit elke mailbox van de tenant versturen (Exchange Online
   PowerShell; de policy werkt na ongeveer 30 minuten):
   ```powershell
   Connect-ExchangeOnline
   New-ApplicationAccessPolicy -AppId <client-id> -PolicyScopeGroupId wiki@logicai.nl -AccessRight RestrictAccess -Description "LogicAI Wiki mailt alleen als wiki@logicai.nl"
   Test-ApplicationAccessPolicy -AppId <client-id> -Identity wiki@logicai.nl   # AccessCheckResult: Granted
   ```
4. In `.env`: `WIKI_ENTRA_TENANT_ID`, `WIKI_ENTRA_CLIENT_ID`, `WIKI_ENTRA_CLIENT_SECRET` en `WIKI_MAIL_GRAPH_SENDER=wiki@logicai.nl`.
   Bij het opstarten logt de app "Mail via Microsoft Graph als wiki@logicai.nl".

Inloggen met wachtwoord op `smtp.office365.com` werkt alleen nog tot eind december 2026 (bericht MC786329) en
vraagt het wachtwoord van een mailbox in `.env`; daarom is Graph de standaard. Kan een uitnodiging niet worden
verstuurd (bijvoorbeeld `ErrorAccessDenied` als de machtiging of de access policy ontbreekt), dan toont
`/admin/users` een rode melding met de reden van Entra of Graph en wordt niets bewaard.

Tot die inrichting klaar is verstuurt de wiki via SMTP vanuit `info@logicai.nl` (`WIKI_MAIL_HOST=smtp.office365.com`,
gebruikersnaam en wachtwoord van die mailbox; "Authenticated SMTP" moet aanstaan op de mailbox).

De mails zelf zijn HTML-templates in `src/main/resources/templates/mail/` (`uitnodiging.html`,
`wachtwoord-herstellen.html`, gedeelde stukken in `fragments.html`), gerenderd door `MailTemplates` met de
platte tekst als fallback. Onder profiel `local` kun je ze als admin bekijken op `/dev/mail/uitnodiging` en
`/dev/mail/wachtwoord-herstellen`; de Thymeleaf-cache staat daar uit, dus opslaan en verversen volstaat.

Geüploade bestanden staan niet in de database. Een back-up bestaat daarom uit de database **en** de
map `WIKI_ATTACHMENTS_DIR`; herstel beide samen.

## Volledige uitrol met Docker Compose

```bash
docker compose up -d --build
```

Dit bouwt de editor en de jar in een multi-stage Dockerfile en start de app plus de lokale MySQL-container.
Staan `WIKI_DB_*` in `.env`, dan gebruikt de app in Compose die server in plaats van de container.

## Uitrol op Fly.io

Lokaal is hiervoor **geen Docker nodig**: `fly deploy` stuurt de broncode naar een remote builder van
Fly, die daar dezelfde `Dockerfile` bouwt (`--remote-only` is de standaard; alleen `--local-only`
gebruikt Docker Desktop). `docker-compose.yml` blijft voor lokaal ontwikkelen en wordt door Fly niet gelezen.

De wiki draait op Fly als één app in regio `ams` (Amsterdam); de database is de MySQL-server
`database.enabl.sh` buiten Fly:

| App | Configuratie | Inhoud | Volume |
|---|---|---|---|
| `logicai-wiki` | `fly.toml` | de Dockerfile, 1 GB geheugen, publiek op `https://logicai-wiki.fly.dev` | `wiki_attachments` op `/app/data` (uploads) |

Er is bewust geen `SPRING_PROFILES_ACTIVE` gezet: zonder profiel zijn de sessiecookies Secure en wordt
`.env` niet gelezen. De eerste admin komt uit `WIKI_BOOTSTRAP_ADMIN_*`. Flyway draait bij het opstarten
van de app. Database-URL en gebruiker staan in `fly.toml` (`[env]`), het wachtwoord alleen als secret.

### Overstap van PostgreSQL naar MySQL (eenmalig, november 2026)

De gegevens staan al in MySQL (zie "De oude PostgreSQL-gegevens"). Productie zelf draait nog op de
Fly-Postgres tot deze twee commando's zijn uitgevoerd; wijzig in de tussentijd niets in de Fly-wiki, want
die wijzigingen gaan niet meer mee:

```powershell
fly secrets set WIKI_DB_PASSWORD='<wachtwoord van de MySQL-gebruiker>' -a logicai-wiki --stage
fly deploy
```

### Eerste keer

```powershell
# 1. flyctl installeren en inloggen (account met betaalmethode; er is geen gratis tier meer)
pwsh -Command "iwr https://fly.io/install.ps1 -useb | iex"
fly auth login

# 2. Wiki-app: volume en geheimen (de bootstrap-admin geldt alleen bij de eerste start op een lege database)
fly apps create logicai-wiki
fly volumes create wiki_attachments --region ams --size 3 -a logicai-wiki --yes
fly secrets set WIKI_DB_PASSWORD='<wachtwoord van de MySQL-gebruiker>' WIKI_BOOTSTRAP_ADMIN_EMAIL='<admin-e-mailadres>' WIKI_BOOTSTRAP_ADMIN_PASSWORD='<admin-wachtwoord>' -a logicai-wiki

# 3. Deployen vanuit de projectmap (remote build; de eerste keer 5 à 10 minuten voor npm en Maven)
fly deploy
fly status -a logicai-wiki
fly logs -a logicai-wiki             # Flyway-migraties, daarna "Tomcat started on port 8080"
```

Daarna opent `https://logicai-wiki.fly.dev` de loginpagina. Mail gaat via SMTP vanuit `info@logicai.nl`:
host, poort, gebruiker en afzender staan in `fly.toml` (`[env]`), het mailboxwachtwoord alleen als secret:

```powershell
fly secrets set WIKI_MAIL_PASSWORD='<wachtwoord van info@logicai.nl>' -a logicai-wiki
```

Zonder dat secret (of met `WIKI_MAIL_HOST` leeg in `fly.toml`) meldt `/admin/users` dat er geen mail is
ingesteld en staan herstel- en uitnodigingslinks in `fly logs`.

Loopt de remote builder uit zijn geheugen (exit 137 in de buildlog), verwijder dan de builder-app
(`fly apps list` toont `fly-builder-...`, daarna `fly apps destroy <naam>`) en deploy opnieuw; Fly maakt
een nieuwe builder. Noodoplossing is `fly deploy --local-only`, dan bouwt Docker Desktop de image.

### Controles na een deploy

- `fly status` toont één machine `started` met health check `passing` (`/actuator/health/readiness`).
- `http://` wordt doorgestuurd naar `https://` (`force_https`), de sessiecookie heeft `Secure; HttpOnly; SameSite=Lax`.
- De header `Strict-Transport-Security` staat op een ingelogde pagina: dan vertrouwt Tomcat de
  `X-Forwarded-Proto` van de Fly-proxy (die verbindt vanaf 172.16.0.0/12, binnen de standaard
  `internalProxies`). Ontbreekt hij, zet dan `server.tomcat.remoteip.internal-proxies` ruimer.
- Een geüploade afbeelding is er na `fly deploy` of `fly machine restart` nog (volume werkt);
  `fly ssh console -a logicai-wiki -C "ls -la /app/data/attachments"` toont eigenaar `wiki`.
- `fly logs -a logicai-wiki` toont bij het opstarten `jdbc:mysql://database.enabl.sh` en "Schema `wiki` is up to date".

### Back-up

Fly maakt dagelijks een snapshot van het uploads-volume en bewaart die vijf dagen
(`fly volumes snapshots list <volume-id>`). De database staat niet op Fly: maak daarvan zelf een dump
(of gebruik de back-up van de MySQL-server), samen met de uploads:

```powershell
mysqldump -h database.enabl.sh -u <gebruiker> -p --single-transaction wiki > wiki.sql
fly ssh console -a logicai-wiki -C "tar czf - /app/data/attachments" > attachments-fly.tgz
```

Terugzetten: `mysql -h database.enabl.sh -u <gebruiker> -p wiki < wiki.sql`.

### Later mogelijk

- Automatisch deployen vanuit GitHub Actions: job na de tests op `main` met
  `superfly/flyctl-actions/setup-flyctl` en `flyctl deploy --remote-only`, token via `fly tokens create deploy`.
- Eigen domein: `fly certs add wiki.logicai.nl`, CNAME naar `logicai-wiki.fly.dev`, `WIKI_BASE_URL` aanpassen.
- Entra-login: `SPRING_PROFILES_ACTIVE=entra` in `[env]`, `WIKI_ENTRA_*` als secrets en de redirect-URI
  `https://<domein>/login/oauth2/code/entra` in de app-registratie.
- Goedkoper: `auto_stop_machines = "suspend"` met `min_machines_running = 0`; de eerste aanvraag na stilte is dan enkele seconden traag.

## Projectstructuur

```
src/main/java/nl/logicai/wiki
  controllers/     Spring MVC-controllers (HTML) en de JSON-API voor de editor
  models/          JPA-entiteiten en formuliermodellen
  repositories/    Spring Data JPA-repositories
  services/        Bedrijfsregels, documentvalidatie en HTML-rendering
  exceptions/      Fouten met bijbehorende HTTP-status
  config/          Security, testgebruikers (profiel local), eerste admin, limieten
  security/        Login tegen app_user (wachtwoord en Entra) en de controle per verzoek op deactivering
src/main/resources/templates     Thymeleaf-templates (fragments.html bevat kop, zijbalk en kruimelpad)
src/main/resources/static/css    Stylesheet
src/main/resources/db/migration  Flyway-migraties
src/main/resources/static/editor Gebouwde editor-bundle (gegenereerd, niet in Git)
frontend/                        BlockNote-editor: TypeScript, React, Vite, Playwright
.github/workflows/ci.yml         CI: frontend-build, backendtests, browsertests
```

## Documentatie (in opbouw)

- Technisch ontwerp met datamodel en URL-contract
- Afspraken voor het documentformaat (BlockNote-subset)
- Test- en acceptatiematrix
- Beheerhandleiding voor back-up en restore
- Architecture Decision Records
