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
( extra )   YouTube-link in een eigen alinea toont de video in de leesweergave (server-side iframe van youtube-nocookie.com; document blijft een gewone link) ( buiten MVP, op verzoek )
( extra )   Video uploaden vanaf de eigen computer: blok "video" in de editor, MP4/WebM, typecontrole op de bytes, opslag in `WIKI_ATTACHMENTS_DIR`, download alleen met sessie via `/attachments/{id}` ( naar de regels van U-01; hoort bij de back-up )

## Volgende stappen


(nog te bepalen met de begeleider)
