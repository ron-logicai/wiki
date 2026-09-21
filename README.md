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
| Uitrol | Dockerfile + Docker Compose, GitHub Actions CI |

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

Met het profiel `local` bestaan drie testgebruikers, allemaal met wachtwoord `wiki`:
`viewer`, `editor` en `admin`. Ze worden bij het opstarten in de tabel `app_user` gezet als ze
ontbreken; buiten dit profiel gebeurt dat nooit, dus in productie bestaan ze niet.

Gebruikers en rollen beheer je als admin op `/admin/users` (klik onderin de zijbalk op je eigen naam).
Een verse installatie zonder `local`-profiel heeft één keer `WIKI_BOOTSTRAP_ADMIN_USERNAME` nodig
(met `WIKI_BOOTSTRAP_ADMIN_PASSWORD` en/of `WIKI_BOOTSTRAP_ADMIN_EMAIL` voor Entra); die admin wordt
alleen aangemaakt zolang de gebruikerstabel leeg is. Entra-accounts komen alleen binnen als hun
`preferred_username` of e-mailadres overeenkomt met een actieve gebruiker.

Browserbeveiliging (spec N-02): CSRF-bescherming staat aan voor alle formulieren en voor de
fetch-verzoeken van de editor (het token staat als meta-tag in de pagina en gaat mee als header).
De sessiecookie is HttpOnly, Secure en SameSite=Lax; alleen het profiel `local` zet Secure uit omdat
daar geen HTTPS is. Lax is bewust gekozen: de Entra-callback naar `/login/oauth2/code/entra` is een
top-level GET vanaf een andere site, en Strict zou die cookie laten vallen. Productie draait daarom
altijd achter HTTPS. De loginflow (wachtwoord en Entra) wordt in `src/test/java/.../security` over
echte HTTP getest, met een nagebootste Entra in de test.

Tijdens frontend-ontwikkeling herbouwt `npm run dev` de bundle bij elke wijziging;
Spring Boot serveert de bestanden onder `/editor/`.

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

Geüploade bestanden staan niet in de database. Een back-up bestaat daarom uit de database **en** de
map `WIKI_ATTACHMENTS_DIR`; herstel beide samen.

## Volledige uitrol met Docker Compose

```bash
docker compose up -d --build
```

Dit bouwt de editor en de jar in een multi-stage Dockerfile en start app + database.
Databasegegevens staan in het volume `wiki-db-data` en overleven een herstart.

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
