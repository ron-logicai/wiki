  # Backlog LogicAI Wiki

Bijgewerkt: 9 oktober 2026. Volgorde volgt de mijlpalen uit de stageopdracht.
De begeleider bepaalt de prioriteit; nieuwe wensen komen eerst hier, niet direct in de MVP.

## Klaar


( M2 )   Login met testrollen (local-profiel), PostgreSQL, Flyway, vaste URL's per pagina ( F-01, F-03, F-05 )
( M2 )   BlockNote-editor met expliciet opslaan, versiecontrole en 409 bij conflict ( F-06, F-07, F-09, F-10 )
( M2 )   Versiehistorie bekijken per pagina en per revisie ( F-11 (bekijken) )
( M3 )   Layout volgens wireframe "Wiki Wireframes Minimaal": zijbalk met paginaboom, bovenbalk met breadcrumbs ( F-05 )
( M3 )   Pagina verplaatsen met cycluscontrole; URL blijft gelijk ( F-04 )
( M3 )   Tags: koppelen op een pagina, overzicht met aantallen, hernoemen en verwijderen door admin ( F-14 )
( M3 )   Templates met drie voorbeeldtemplates (Projectinformatie, Developer-onboarding, Werkinstructie) ( F-15 )
( M3 )   Nieuwe pagina: titel, plek in de boom en template kiezen ( F-03, F-04 )
( M4 )   Zoeken op titel en inhoud, filter op tag, paginering via queryparameters (`/search?q=&tag=&page=`) ( F-13 )
( M4 )   Prullenbak: soft delete, herstellen, weigeren zolang er actieve subpagina's zijn (`/trash`) ( F-12 )
( M4 )   Revisie terugzetten als nieuwe revisie; bestaande historie blijft intact ( F-11 (herstel) )
( M4 )   Markdown-export per actieve pagina: knop "Exporteren" en `/pages/{id}/export.md`; subset en verlies vastgelegd in `docs/markdown-export.md` ( F-17 )
( extra )   YouTube-link in een eigen alinea toont de video in de leesweergave (server-side iframe van youtube-nocookie.com; document blijft een gewone link) ( buiten MVP, op verzoek )
( extra )   Video uploaden vanaf de eigen computer: blok "video" in de editor, MP4/WebM, typecontrole op de bytes, opslag in `WIKI_ATTACHMENTS_DIR`, download alleen met sessie via `/attachments/{id}` ( naar de regels van U-01; hoort bij de back-up )
( U-01 )   Afbeeldingen en bijlagen: blokken "image" (PNG, JPEG) en "file" (PDF) in de editor, maximaal 10 MB per bestand (`WIKI_UPLOAD_MAX_FILE_SIZE`), typecontrole op de bytes (SVG/HTML geweigerd), zelfde opslag en download als video; reden van weigering zichtbaar in de editor ( U-01 )

( extra )   Beheerpagina `/admin` (alleen Admin): aantallen, links naar beheerfuncties en de laatste audit-gebeurtenissen; bereikbaar via de rij "Beheer" in de zijbalk (alleen voor beheerders) ( sectie 1, URL-contract )

( F-01, F-02 )   Gebruikers en rollen: tabel `app_user` (V7), beheer op `/admin/users` (toevoegen met herhaald wachtwoord, rol wijzigen, activeren/deactiveren, wachtwoord instellen of resetten via `POST /admin/users/{id}/password`, nooit verwijderen), wachtwoordlogin tegen de tabel, Entra-login alleen voor accounts die op gebruikersnaam of e-mail overeenkomen, deactivering en rolwijziging gelden bij het volgende verzoek ook met open sessie; eerste admin via `WIKI_BOOTSTRAP_ADMIN_*` ( sectie 1, F-01, F-02, A-07 )

( U-03 )   Persoonlijke favorieten: ster bij elke pagina in de zijbalkboom (zichtbaar bij hover, `POST /pages/{id}/favorite`), sectie "Favorieten" boven "Ruimtes", tabel `page_favorite` (V8) per gebruikersnaam; pagina's in de prullenbak worden verborgen en komen na herstel terug ( U-03 )

( U-06 (suggesties) )   Paginalinksuggesties: tijdens het invoegen of bewerken van een link (toolbar-knop, Ctrl+K of "Edit link") zoekt de editor op titel via `GET /api/pages/suggest?q=` (maximaal 10 actieve pagina's, hoofdletterongevoelig, prullenbak uitgesloten, leeg veld toont recent gewijzigde pagina's); een gekozen pagina wordt een link naar `/pages/{id}`, dus de verwijzing blijft werken na hernoemen en verplaatsen ( U-06 eerste helft, F-16 )

( N-02 )   Browserbeveiliging: CSRF actief op alle mutaties, ook voor de fetch-verzoeken van de editor (token als header; geweigerde API-verzoeken krijgen een JSON-melding, 401 zonder sessie); sessiecookie HttpOnly, Secure en SameSite=Lax (Lax omdat de Entra-callback een top-level GET is); de volledige loginflow is getest over echte HTTP, voor wachtwoordlogin en voor Entra tegen een nagebootste identity provider in de test ( N-02, A-07 )

( extra )   Exportmenu op de leesweergave: het download-icoon opent een menu met "Downloaden als .md" en "Downloaden als .pdf"; de PDF (`/pages/{id}/export.pdf`) wordt server-side gemaakt met OpenHTMLtoPDF uit dezelfde HTML als de leesweergave, afbeeldingen ingesloten, video's als link; zelfde rechten en 404-regels als de Markdown-export, vastgelegd in `docs/markdown-export.md` ( buiten MVP, op verzoek; volgt F-17 en N-01 )

( extra )   Ontwerp "LogicAI Wiki.html" overgenomen: kruimelpad begint overal met Home; paginaweergave met rechterkolom "Op deze pagina" (koppen krijgen `id="kop-n"` via `BlockRenderer.renderWithOutline`) en navy blok "Gerelateerd" met plus-knop waarmee een redacteur een bestaande pagina koppelt (tabel `page_relation` V10, gericht, `POST /pages/{id}/related` en `.../related/{relatedId}/remove`, audit `page.relation.add/remove`, prullenbakpagina's vallen weg); nieuwe pagina met template als keuzekaarten; "Alle pagina's" met titelfilter, ruimtepillen, sortering (A–Z of laatst bijgewerkt), ruimte, avatar en favorietster per rij; zoekpagina met smalle kolom, zoekbalk met wissen, "N resultaten voor …", tagfilter als pillen en resultatenkaart met icoon, ruimte, fragment, tags en auteur; tagspagina met zoekveld, sortering (meest gebruikt of A–Z), lijstkaart met badge, gebruik en potlood/prullenbak per rij en rechts de kaart "Tag hernoemen" (`GET /tags/{id}/rename`); beheer met tab-pillen (Overzicht, Gebruikers, Tags, Templates), gebruikerspagina "Gebruikers en toegang" met teltegels, zoek- en rolfilter, avatar en rolbadge per rij, actiemenu (⋯) en het aanmaakformulier rechts; "Beheer"-rij met tandwiel in de zijbalk en rolnaam onder de gebruikersnaam ( ontwerp, F-05 )

( extra )   Lijsten met pijlknop: "Recent bijgewerkt" op de startpagina (50 laatst gewijzigde actieve pagina's, `PageService.RECENT_LIMIT`) en "Recente wijzigingen" op Beheer > Overzicht (50 laatste audit-gebeurtenissen, `AdminService.RECENT_LIMIT`) tonen 10 rijen en verbergen de rest achter een pijlknop onderaan ("Nog N tonen" / "Minder tonen", pijl draait mee; fragmenten `uitklapknop` en `uitklapscript`); zonder klik blijven het er 10 ( op verzoek, sectie 9 )

( U-07, U-08 )   Eigenschappen per pagina onder de titel: eigenaar (keuze uit actieve gebruikers, opgeslagen als gebruikersnaam) en status (Concept, Actueel, Verouderd) via `POST /pages/{id}/properties`; knop "Gecontroleerd" (`POST /pages/{id}/review`) stempelt wie de pagina wanneer het laatst heeft gecontroleerd; kolommen op `page` (V9), beide met versiecontrole en audit-gebeurtenis; viewers zien alleen de labels ( U-07, U-08 (signaleren), sectie 7 )

( F-01 )   Eigen loginpagina in de huisstijl (`login.html`, `LoginController`): navy bovenbalk met het logo links, kaart met gebruikersnaam en wachtwoord, knop "Inloggen met Microsoft" zodra Entra is geconfigureerd, en Nederlandse meldingen voor `?error`, `?logout` en `?deactivated` ( F-01, F-02, N-02 )

## Volgende stappen

- Eigen wachtwoord wijzigen (nu zet alleen een admin een wachtwoord, bij het aanmaken of via "Wachtwoord instellen" op `/admin/users`).
- Backlinks: op een pagina tonen welke pagina's ernaar verwijzen (tweede helft van U-06).


(nog te bepalen met de begeleider)
