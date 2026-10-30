# LogicAI Wiki

Interne kenniswiki voor LogicAI / Code Art BV: pagina's, blokken en blijvende links.
Gebouwd als stageopdracht softwareontwikkeling (opdracht v1.0, 15 september 2026).

## Stack

| Onderdeel | Keuze |
|---|---|
| Runtime | Java 25 LTS (Eclipse Temurin) |
| Backend | Spring Boot 4.1.x: Spring MVC, Data JPA, Security (OIDC), Validation |
| HTML | Thymeleaf, server-side gerenderd; vaste URL's per pagina |
| Database | PostgreSQL 18, JSONB voor documentinhoud, Flyway-migraties |
| Editor | BlockNote als lokale React-island (TypeScript + Vite), geen client-side router |
| Tests | JUnit, MockMvc, Testcontainers (PostgreSQL 18), Playwright |
| Uitrol | Dockerfile + Docker Compose, Fly.io (`fly.toml`), GitHub Actions CI |

## Vereisten voor ontwikkelen

- Eclipse Temurin JDK 25: <https://adoptium.net/temurin/releases/?version=25>
- Node.js 26 en npm (alleen buildhulpmiddel voor de editor)
- Docker Desktop (voor PostgreSQL via Compose en voor Testcontainers)

## Snel starten

```bash
# 1. Database
cp .env.example .env            # vul WIKI_DB_PASSWORD in
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

## Ontwikkelen met een blijvende database (Docker-volume)

Bij `docker compose up -d db` staan de databasebestanden niet in de container maar in het
benoemde Docker-volume `wiki-db-data`. Dat volume blijft bestaan als de container stopt, opnieuw
start of wordt verwijderd, dus je pagina's, gebruikers en revisies zijn er na een herstart nog.
Dit is de relevante configuratie uit `docker-compose.yml`:

```yaml
services:
  db:
    image: postgres:18
    restart: unless-stopped            # start mee met Docker Desktop
    environment:
      POSTGRES_DB: ${WIKI_DB_NAME:-wiki}
      POSTGRES_USER: ${WIKI_DB_USERNAME:-wiki}
      POSTGRES_PASSWORD: ${WIKI_DB_PASSWORD:?set WIKI_DB_PASSWORD in .env}
    ports:
      - "127.0.0.1:5432:5432"          # alleen bereikbaar vanaf je eigen machine
    volumes:
      - wiki-db-data:/var/lib/postgresql   # postgres:18 verwacht het volume hier, niet in .../data

volumes:
  wiki-db-data:                        # benoemd volume, beheerd door Docker
```

Zo werk je er dagelijks mee:

```bash
docker compose up -d db          # database starten (eerste keer: volume wordt aangemaakt en Flyway vult het schema bij de eerste app-start)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

docker compose stop db           # stoppen, gegevens blijven staan
docker compose down              # containers weg, volume blijft staan
docker compose up -d db          # weer verder met dezelfde gegevens

docker volume ls | grep wiki     # het volume bekijken (heet <projectmap>_wiki-db-data)
docker compose down -v           # ALLES wissen: containers én volume, je begint met een lege wiki
```

Alleen `docker compose down -v` (of `docker volume rm`) gooit de gegevens weg. Geüploade bestanden
staan bij een lokale run niet in Docker maar in `./data/attachments` (zie `WIKI_ATTACHMENTS_DIR`);
die map staat in `.gitignore` en blijft ook gewoon staan.

Wil je de databasebestanden liever als gewone map in het project (bijvoorbeeld om ze makkelijk te
kopiëren of te verwijderen), zet dan een `docker-compose.override.yml` naast `docker-compose.yml`.
Compose leest die automatisch mee; commit hem niet.

```yaml
# docker-compose.override.yml: databasebestanden in ./data/postgres in plaats van in een Docker-volume
services:
  db:
    volumes:
      - ./data/postgres:/var/lib/postgresql
```

Een kopie van je ontwikkeldata maak je met `pg_dump` in de container; terugzetten gaat met `psql`:

```bash
docker compose exec db pg_dump -U wiki wiki > wiki-dev.sql
docker compose exec -T db psql -U wiki wiki < wiki-dev.sql
```

## Tests

```bash
./mvnw verify                         # unit-, integratie- en MVC-tests (start postgres:18 via Testcontainers)
cd frontend && npm run typecheck      # TypeScript
cd frontend && npx playwright test    # browserflows tegen een draaiende app op :8080
```

## Configuratie

Alle instellingen komen uit omgevingsvariabelen; zie `.env.example`. Er staan geen secrets in Git.

| Variabele | Betekenis |
|---|---|
| `WIKI_DB_URL`, `WIKI_DB_USERNAME`, `WIKI_DB_PASSWORD` | PostgreSQL-verbinding |
| `SPRING_PROFILES_ACTIVE` | `local` (ontwikkeling) en/of `entra` (bedrijfslogin) |
| `WIKI_ENTRA_TENANT_ID`, `WIKI_ENTRA_CLIENT_ID`, `WIKI_ENTRA_CLIENT_SECRET` | Microsoft Entra ID app-registratie |
| `WIKI_ATTACHMENTS_DIR` | Map voor geüploade bestanden (standaard `./data/attachments`; in Compose het volume `wiki-attachments`) |
| `WIKI_UPLOAD_MAX_FILE_SIZE` | Maximale grootte per afbeelding (PNG, JPEG) of PDF (standaard `10MB`, spec U-01) |
| `WIKI_UPLOAD_MAX_VIDEO_SIZE` | Maximale grootte per video (MP4, WebM; standaard `100MB`) |
| `WIKI_BASE_URL` | Publieke URL van de wiki, voor de links in e-mail (standaard `http://localhost:8080`) |
| `WIKI_INVITATION_TTL` | Geldigheid van een uitnodigingslink (standaard `1h`) |
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

Dit bouwt de editor en de jar in een multi-stage Dockerfile en start app + database.
Databasegegevens staan in het volume `wiki-db-data` en overleven een herstart.

## Uitrol op Fly.io

Lokaal is hiervoor **geen Docker nodig**: `fly deploy` stuurt de broncode naar een remote builder van
Fly, die daar dezelfde `Dockerfile` bouwt (`--remote-only` is de standaard; alleen `--local-only`
gebruikt Docker Desktop). `docker-compose.yml` blijft voor lokaal ontwikkelen en wordt door Fly niet gelezen.

De wiki draait op Fly als twee apps in regio `ams` (Amsterdam), verbonden over het private netwerk:

| App | Configuratie | Inhoud | Volume |
|---|---|---|---|
| `logicai-wiki` | `fly.toml` | de Dockerfile, 1 GB geheugen, publiek op `https://logicai-wiki.fly.dev` | `wiki_attachments` op `/app/data` (uploads) |
| `logicai-wiki-db` | `deploy/fly/db/fly.toml` | officieel `postgres:18`-image, 512 MB, geen publieke poorten (`logicai-wiki-db.internal:5432`) | `wiki_db_data` op `/var/lib/postgresql` |

Er is bewust geen `SPRING_PROFILES_ACTIVE` gezet: zonder profiel zijn de sessiecookies Secure, bestaan
de testgebruikers niet en wordt `.env` niet gelezen. De eerste admin komt uit `WIKI_BOOTSTRAP_ADMIN_*`.
Flyway draait bij het opstarten van de app. De database is onbeheerd (Fly Managed Postgres kent geen
Postgres 18 en begint bij $38 per maand): back-ups en Postgres-updates zijn voor onszelf, zie onder.
Richtprijs voor beide machines plus volumes: ongeveer $11 à $12 per maand (prijzen oktober 2026).

### Eerste keer

```powershell
# 1. flyctl installeren en inloggen (account met betaalmethode; er is geen gratis tier meer)
pwsh -Command "iwr https://fly.io/install.ps1 -useb | iex"
fly auth login

# 2. Database-app: volume en wachtwoord eerst, dan deployen (alleen een image-pull, geen build)
fly apps create logicai-wiki-db
fly volumes create wiki_db_data --region ams --size 3 -a logicai-wiki-db --yes
fly secrets set POSTGRES_PASSWORD='<sterk wachtwoord>' -a logicai-wiki-db
fly deploy --config deploy/fly/db/fly.toml -a logicai-wiki-db
fly logs -a logicai-wiki-db          # verwacht: "database system is ready to accept connections"

# 3. Wiki-app: volume en geheimen (zelfde databasewachtwoord; de bootstrap-admin geldt alleen bij de eerste start)
fly apps create logicai-wiki
fly volumes create wiki_attachments --region ams --size 3 -a logicai-wiki --yes
fly secrets set WIKI_DB_PASSWORD='<zelfde wachtwoord>' WIKI_BOOTSTRAP_ADMIN_EMAIL='<admin-e-mailadres>' WIKI_BOOTSTRAP_ADMIN_PASSWORD='<admin-wachtwoord>' -a logicai-wiki

# 4. Deployen vanuit de projectmap (remote build; de eerste keer 5 à 10 minuten voor npm en Maven)
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
- `fly ips list -a logicai-wiki-db` toont geen publiek adres.

### Back-up

Fly maakt dagelijks een snapshot van elk volume en bewaart die vijf dagen
(`fly volumes snapshots list <volume-id>`). Maak daarnaast zelf een dump van database én uploads:

```powershell
fly ssh console -a logicai-wiki-db -C "pg_dump -U wiki wiki" > wiki-fly.sql
fly ssh console -a logicai-wiki -C "tar czf - /app/data/attachments" > attachments-fly.tgz
```

Terugzetten: `fly proxy 5433:5432 -a logicai-wiki-db` en dan lokaal `psql -h localhost -p 5433 -U wiki wiki < wiki-fly.sql`.

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
