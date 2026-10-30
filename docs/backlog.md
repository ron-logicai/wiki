  # Backlog LogicAI Wiki

Bijgewerkt: 29 oktober 2026. Volgorde volgt de mijlpalen uit de stageopdracht.
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

( F-01, F-02 )   Gebruikers en rollen: tabel `app_user` (V7), beheer op `/admin/users` (uitnodigen per e-mail, zie hieronder; rol wijzigen, activeren/deactiveren, wachtwoord instellen of resetten via `POST /admin/users/{id}/password`, nooit verwijderen), wachtwoordlogin tegen de tabel, Entra-login alleen voor accounts die op gebruikersnaam of e-mail overeenkomen, deactivering en rolwijziging gelden bij het volgende verzoek ook met open sessie; eerste admin via `WIKI_BOOTSTRAP_ADMIN_*` ( sectie 1, F-01, F-02, A-07 )

( U-03 )   Persoonlijke favorieten: ster bij elke pagina in de zijbalkboom (zichtbaar bij hover, `POST /pages/{id}/favorite`), sectie "Favorieten" boven "Ruimtes", tabel `page_favorite` (V8) per gebruikersnaam; pagina's in de prullenbak worden verborgen en komen na herstel terug ( U-03 )

( U-06 (suggesties) )   Paginalinksuggesties: tijdens het invoegen of bewerken van een link (toolbar-knop, Ctrl+K of "Edit link") zoekt de editor op titel via `GET /api/pages/suggest?q=` (maximaal 10 actieve pagina's, hoofdletterongevoelig, prullenbak uitgesloten, leeg veld toont recent gewijzigde pagina's); een gekozen pagina wordt een link naar `/pages/{id}`, dus de verwijzing blijft werken na hernoemen en verplaatsen ( U-06 eerste helft, F-16 )
( extra )   Zoeksuggesties: de zoekvelden op `/search` en de startpagina tonen tijdens het typen bestaande pagina's op titel via hetzelfde `GET /api/pages/suggest?q=` (fragment `zoeksuggestiescript` in `fragments.html`; pijltjes en Enter openen de pagina, Enter zonder keuze zoekt gewoon; geen treffers = geen lijst) ( op verzoek, F-13 )

( N-02 )   Browserbeveiliging: CSRF actief op alle mutaties, ook voor de fetch-verzoeken van de editor (token als header; geweigerde API-verzoeken krijgen een JSON-melding, 401 zonder sessie); sessiecookie HttpOnly, Secure en SameSite=Lax (Lax omdat de Entra-callback een top-level GET is); de volledige loginflow is getest over echte HTTP, voor wachtwoordlogin en voor Entra tegen een nagebootste identity provider in de test ( N-02, A-07 )

( extra )   Exportmenu op de leesweergave: het download-icoon opent een menu met "Downloaden als .md" en "Downloaden als .pdf"; de PDF (`/pages/{id}/export.pdf`) wordt server-side gemaakt met OpenHTMLtoPDF uit dezelfde HTML als de leesweergave, afbeeldingen ingesloten, video's als link; zelfde rechten en 404-regels als de Markdown-export, vastgelegd in `docs/markdown-export.md` ( buiten MVP, op verzoek; volgt F-17 en N-01 )

( extra )   Ontwerp "LogicAI Wiki.html" overgenomen: kruimelpad begint overal met Home; paginaweergave met rechterkolom "Op deze pagina" (koppen krijgen `id="kop-n"` via `BlockRenderer.renderWithOutline`) en navy blok "Gerelateerd" met plus-knop waarmee een redacteur een bestaande pagina koppelt (tabel `page_relation` V10, gericht, `POST /pages/{id}/related` en `.../related/{relatedId}/remove`, audit `page.relation.add/remove`, prullenbakpagina's vallen weg); nieuwe pagina met template als keuzekaarten; "Alle pagina's" met titelfilter, ruimtepillen, sortering (A–Z of laatst bijgewerkt), ruimte, avatar en favorietster per rij; zoekpagina met smalle kolom, zoekbalk met wissen, "N resultaten voor …", tagfilter als pillen en resultatenkaart met icoon, ruimte, fragment, tags en auteur; tagspagina met zoekveld, sortering (meest gebruikt of A–Z), lijstkaart met badge, gebruik en potlood/prullenbak per rij en rechts de kaart "Tag hernoemen" (`GET /tags/{id}/rename`); beheer met tab-pillen (Overzicht, Gebruikers, Tags, Templates), gebruikerspagina "Gebruikers en toegang" met teltegels, zoek- en rolfilter, avatar en rolbadge per rij, actiemenu (⋯) en het aanmaakformulier rechts; "Beheer"-rij met tandwiel in de zijbalk en rolnaam onder de gebruikersnaam ( ontwerp, F-05 )

( extra )   Lijsten met pijlknop: "Recent bijgewerkt" op de startpagina (50 laatst gewijzigde actieve pagina's, `PageService.RECENT_LIMIT`) en "Recente wijzigingen" op Beheer > Overzicht (50 laatste audit-gebeurtenissen, `AdminService.RECENT_LIMIT`) tonen 10 rijen en verbergen de rest achter een pijlknop onderaan ("Nog N tonen" / "Minder tonen", pijl draait mee; fragmenten `uitklapknop` en `uitklapscript`); zonder klik blijven het er 10 ( op verzoek, sectie 9 )

( U-07, U-08 )   Eigenschappen per pagina onder de titel: eigenaar (keuze uit actieve gebruikers, opgeslagen als gebruikersnaam) en status (Concept, Actueel, Verouderd) via `POST /pages/{id}/properties`; knop "Gecontroleerd" (`POST /pages/{id}/review`) stempelt wie de pagina wanneer het laatst heeft gecontroleerd; kolommen op `page` (V9), beide met versiecontrole en audit-gebeurtenis; viewers zien alleen de labels ( U-07, U-08 (signaleren), sectie 7 )

( F-01 )   Eigen loginpagina in de huisstijl (`login.html`, `LoginController`): navy bovenbalk met het logo links, kaart met gebruikersnaam en wachtwoord, knop "Inloggen met Microsoft" zodra Entra is geconfigureerd, en Nederlandse meldingen voor `?error`, `?logout` en `?deactivated` ( F-01, F-02, N-02 )

( F-01 )   Wachtwoord vergeten en eigen wachtwoord wijzigen: "Wachtwoord vergeten?" op de loginpagina vraagt een e-mailadres (`/wachtwoord-vergeten`), bekend actief account met wachtwoord krijgt een mail met een eenmalige link (`/wachtwoord-herstellen?token=`, één uur geldig, alleen de SHA-256-hash in tabel `password_reset_token` V12, wachttijd van 2 minuten tussen aanvragen); onbekend adres geeft "Uw account bestaat niet"; SMTP via `WIKI_MAIL_*`, zonder `WIKI_MAIL_HOST` wordt de link gelogd (`LogMailSender`), link gebouwd uit `WIKI_BASE_URL`; ingelogde gebruikers wijzigen hun wachtwoord op `/account/wachtwoord` (naam in de zijbalk) met controle van het huidige wachtwoord; audit `user.password.reset-request`, `user.password.reset` en `user.password.change` ( F-01, N-02, N-04 )

( F-01 )   Gebruiker uitnodigen per e-mail: het formulier op `/admin/users` vraagt alleen e-mail en rol; de genodigde krijgt een mail met een eenmalige link (`/accountaanmaken?token=`, één uur geldig via `WIKI_INVITATION_TTL`, alleen de SHA-256-hash in tabel `user_invitation` V13, een nieuwe uitnodiging voor hetzelfde adres vervangt de oudere) en kiest daar zelf naam (weergavenaam), gebruikersnaam en wachtwoord; de pagina volgt het ontwerp van 28-10-2026 (navy welkomstpaneel links met logo en mascotte, formulier rechts, `.aanmaken` in `style.css`) en noemt wie uitnodigde, de rol en het e-mailadres; de `app_user`-rij ontstaat pas bij het accepteren; zonder geldig token is de pagina onbruikbaar en ze staat nergens in de navigatie, dus nog steeds geen openbare registratie; een adres dat al bij een gebruiker hoort wordt geweigerd; audit `user.invite` (beheerder) en `user.create` (genodigde, "via uitnodiging van"); SMTP via `WIKI_MAIL_*`, zonder host wordt de link gelogd (`LogMailSender`); een Entra-only account zonder wachtwoord is niet meer via het formulier te maken, Entra-login werkt voor genodigden via hun e-mailadres ( F-01, N-02, N-04 )

( F-01, N-04 )   Mail via Microsoft Graph: met `WIKI_MAIL_GRAPH_SENDER` verstuurt de wiki uitnodigingen en herstel-links als applicatie (client credentials van de Entra-appregistratie, toepassingsmachtiging `Mail.Send`, beperkt tot die ene mailbox met een Exchange application access policy) vanuit bijvoorbeeld een gedeelde mailbox `wiki@logicai.nl`; geen mailboxwachtwoord meer in `.env`, en het blijft werken na december 2026 (MC786329); `GraphMailSender` naast `SmtpMailSender`, `MailConfig` kiest Graph, dan SMTP, dan log; fouten van Entra of Graph komen als rode melding op `/admin/users` ( F-01, N-02, N-04 )

( F-01 )   HTML-mails in huisstijl (ontwerp 28-10-2026): de uitnodiging en de "wachtwoord vergeten"-mail zijn Thymeleaf-templates in `templates/mail/` (`MailTemplates`, tabellen en inline CSS zodat Outlook en Gmail ze tonen; navy kop, oranje knop, kader met de geldigheid en de volledige link als tekst, platte tekst als fallback); SMTP stuurt `multipart/alternative`, Graph stuurt HTML; de beheerder wordt met weergavenaam genoemd; preview op `/dev/mail/uitnodiging` en `/dev/mail/wachtwoord-herstellen` onder profiel `local` (admin); tot de Graph-inrichting klaar is verstuurt `info@logicai.nl` via SMTP ( F-01, N-02 )

( extra )   Uitrol op Fly.io: twee apps in regio `ams` (de wiki uit de Dockerfile, remote gebouwd door Fly, dus lokaal geen Docker nodig; de database als los `postgres:18`-image, alleen privé bereikbaar), volumes voor uploads en databasebestanden, geheimen via `fly secrets`, handmatige deploy met flyctl; `fly.toml` en `deploy/fly/db/fly.toml`, stappen en back-up in de README ( N-04, sectie 6 )

( extra )   Gebruiker verwijderen: de knop "Deactiveren" in het actiemenu op `/admin/users` is vervangen door "Verwijderen" (met bevestiging, `POST /admin/users/{id}/delete`, `UserService.delete`); de rij in `app_user` en de bijbehorende reset-tokens gaan weg, gebruikersnamen op pagina's en in de audit blijven als tekst staan; jezelf en de laatste actieve beheerder kun je niet verwijderen; een verwijderde gebruiker met een open sessie wordt bij het volgende verzoek uitgelogd (`/login?verwijderd`); audit `user.delete`; deactiveren/activeren bestaat nog als endpoint maar niet meer in het menu ( op verzoek 29-10-2026, F-02 )


( F-01 )   Inloggen met e-mailadres in plaats van gebruikersnaam: de loginpagina vraagt e-mailadres en wachtwoord (`WikiUserDetailsService` zoekt op `email`, de principal blijft de interne `username`), het accountformulier achter de uitnodigingslink vraagt alleen naam, nieuw wachtwoord en wachtwoord herhalen en de interne gebruikersnaam wordt afgeleid van het deel vóór de `@` (`UserService.usernameFor`, `ron2` als die bezet is); overal in de wiki (zijbalk, bewerkt door, geschiedenis, prullenbak, audit, gebruikerslijst, meldingen, herstelmail) staat de weergavenaam via de `namen`-bean (`${@namen.weergave(...)}`), de gebruikersnaam is alleen nog de sleutel op pagina's, favorieten en in de audit; lokale testgebruikers loggen in als `viewer@wiki.nl`/`editor@wiki.nl`/`admin@wiki.nl` (bestaande lokale databases krijgen het nieuwe adres bij het opstarten); `WIKI_BOOTSTRAP_ADMIN_EMAIL` vervangt `_USERNAME` (optioneel `_NAME`); een account zonder e-mailadres van vóór deze wijziging logt in met de gebruikersnaam tot een beheerder op `/admin/users` een adres instelt (`POST /admin/users/{id}/email`, audit `user.email`) ( op verzoek 30-10-2026, F-01, N-02 )

## Volgende stappen

- Backlinks: op een pagina tonen welke pagina's ernaar verwijzen (tweede helft van U-06).


(nog te bepalen met de begeleider)
