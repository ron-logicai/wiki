  # Backlog LogicAI Wiki

Bijgewerkt: 18 september 2026. Volgorde volgt de mijlpalen uit de stageopdracht.
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

( extra )   Beheerpagina `/admin` (alleen Admin): aantallen, links naar beheerfuncties en de laatste 20 audit-gebeurtenissen; de gebruikersrij onderin de zijbalk is voor beheerders de link ernaartoe ( sectie 1, URL-contract )

( F-01, F-02 )   Gebruikers en rollen: tabel `app_user` (V7), beheer op `/admin/users` (toevoegen, rol wijzigen, activeren/deactiveren, nooit verwijderen), wachtwoordlogin tegen de tabel, Entra-login alleen voor accounts die op gebruikersnaam of e-mail overeenkomen, deactivering en rolwijziging gelden bij het volgende verzoek ook met open sessie; eerste admin via `WIKI_BOOTSTRAP_ADMIN_*` ( sectie 1, F-01, F-02, A-07 )

## Volgende stappen

- Eigen wachtwoord wijzigen (nu zet alleen een admin een wachtwoord bij het aanmaken; wachtwoord resetten voor bestaande gebruikers ontbreekt ook).
- Nederlandse loginpagina met melding na deactivering (`/login?deactivated` toont nu de standaardpagina van Spring Security zonder tekst).


(nog te bepalen met de begeleider)
