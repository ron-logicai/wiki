# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

LogicAI Wiki: an internal knowledge wiki (Spring Boot 4.1 / Java 25 / PostgreSQL 18 / Thymeleaf) with a BlockNote editor built as a local React island. Built as an internship assignment against a numbered spec; code comments cite it as `spec F-xx` (feature), `N-xx` (non-functional) and `section N`. `docs/backlog.md` tracks which spec items are done and what comes next.

UI text, template names, Thymeleaf model attributes and some Java identifiers are **Dutch** (`paginas`, `ouders`, `boom`, `prullenbak`, `verplaatsen`). Java class names, packages and most method names are English. Keep to that split when adding code; user-facing strings and error messages are Dutch.

## Commands

Prerequisites: Temurin JDK 25, Node 26, Docker Desktop (Postgres via Compose and for Testcontainers).

```bash
# Database for local runs
cp .env.example .env            # set WIKI_DB_PASSWORD
docker compose up -d db

# Editor bundle -> src/main/resources/static/editor (gitignored; the app 404s on /editor/* without it)
cd frontend && npm ci && npm run build
cd frontend && npm run dev      # vite build --watch during editor work

# Run the app (profile local = no secure cookies, Thymeleaf cache off, in-memory users)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# Alternative: src/test/java/.../TestWikiApplication starts it with a Testcontainers Postgres.

# Backend tests (Testcontainers pulls postgres:18; Docker must be running)
./mvnw verify
./mvnw test -Dtest=PageFlowIntegrationTest                     # one class
./mvnw test -Dtest=PageFlowIntegrationTest#staleBaseVersionIsRejectedWith409   # one method

# Frontend
cd frontend && npm run typecheck
cd frontend && npx playwright test          # needs the app already running on :8080 (or WIKI_BASE_URL)

# Full stack in Docker
docker compose up -d --build
```

Local test users (profile `local` only): `viewer`, `editor`, `admin`, all with password `wiki`.

CI (`.github/workflows/ci.yml`) runs frontend typecheck+build, then `./mvnw -B verify`, then Playwright against a packaged jar. There is no linter beyond `tsc` and the Java compiler.

## Architecture

### Request flow and the one JSON endpoint

Everything is server-rendered Spring MVC + Thymeleaf with fixed URLs (`/pages/{uuid}`, `/pages/{uuid}/edit`, `/search`, `/tags`, `/templates`, `/trash`). Forms POST and redirect to GET. There is no client-side router.

The only JSON traffic is the editor save: `PUT /api/pages/{id}/content` (and `/api/templates/{id}/content`), handled by `PageApiController` / `TemplateApiController` with `ApiExceptionAdvice` mapping exceptions to 400/409 JSON bodies. `SecurityConfig` gives `/api/**` a 401 entry point instead of the login redirect so the editor never mistakes a login page for a save. CSRF stays on; the token is a `<meta name="_csrf">` in `fragments.html` and `frontend/src/save.ts` sends it as a header.

`GlobalModelAdvice` injects `boom` (the full active page tree, one query) and `gebruiker` into every HTML controller listed in its `assignableTypes`. New HTML controllers must be added there or the sidebar is empty.

### Editor island contract

`pagina-bewerken.html` renders `<div id="editor-root" data-page-id data-base-version>` plus `<script id="editor-document" type="application/json">`. `frontend/src/main.tsx` mounts BlockNote on it; Vite (`frontend/vite.config.ts`) emits stable names `editor.js` / `editor.css` under `base: /editor/` so the template can reference them directly. `PageService.embeddableJson` escapes `<` so document JSON is safe inside the script tag. An editor that fails to start must show an error and never save (spec F-06).

### Document pipeline

Page content is BlockNote block JSON in a JSONB column, the single source of truth. Every write goes through `DocumentValidator`, which whitelists block types, inline types, style keys and link hrefs (`LinkPolicy`), enforces depth/count/byte limits (`WikiLimits`, from `wiki.limits.*` in `application.yaml`), and derives `search_text` for the Postgres full-text index. It rejects unknown content rather than dropping it. `BlockRenderer` turns the same subset into escaped HTML for the read view, so reading never depends on the browser editor. Extending the supported subset means touching validator, renderer, their tests, and probably the editor.

### Concurrency and history

`Page.lockVersion` (`@Version`) is the optimistic lock; every mutating operation (save, move, delete) takes a `baseVersion` and throws `PageConflictException` → 409 when it does not match. `currentRevision` is a separate counter: each content change appends an immutable `PageRevision`, and an unchanged save creates no revision. `PageService.create` builds revision 1 before the first persist so a fresh page keeps lock version 0; tests depend on that.

### Tree, trash, audit

Pages form a tree via `parent_id`; the URL never changes on move. `PageService.move` runs SERIALIZABLE and refuses cycles. Delete is a soft delete (`deleted_at`), refused while active subpages exist; `/pages/{id}` of a trashed page returns 410 with a message page. Repository queries filter `deleted_at is null` explicitly; use `getActive` vs `getAny` deliberately. Move, delete and restore write an `AuditEvent`.

### Security

Roles ADMIN > EDITOR > VIEWER via `RoleHierarchy`. Route rules live in `SecurityConfig`; write operations are additionally guarded by `@PreAuthorize("hasRole('EDITOR')")` on service methods. Profile `local` adds in-memory users (`LocalUsersConfig`); profile `entra` enables OIDC login through Microsoft Entra when `WIKI_ENTRA_*` are set. Never add test users outside the `local` profile.

### Persistence

Flyway owns the schema (`src/main/resources/db/migration/V*.sql`); Hibernate runs with `ddl-auto: validate`, so any entity change needs a new migration. Templates (V4) are seeded with fixed UUIDs that tests and docs refer to. Search uses the `'simple'` text-search config on purpose so technical terms match unchanged.

### Tests

Integration tests are `@SpringBootTest` + `MockMvc` + `@Import(TestcontainersConfiguration.class)` with `@WithMockUser(roles = ...)` and `.with(csrf())` on writes. Pure unit tests exist for `BlockRenderer`, `DocumentValidator`, search snippets and the move rules. Playwright e2e in `frontend/e2e` is a smoke suite against a running app.
