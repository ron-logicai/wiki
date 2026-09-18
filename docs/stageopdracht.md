<!-- Geconverteerd uit Stageopdracht_LogicAI_Wiki.docx (v1.0, 15 september 2026). De Word-versie is leidend. -->

# LogicAI Wiki

_Stageopdracht softwareontwikkeling_

Een interne kenniswerkplek met pagina’s, blokken en blijvende links.

| Onderdeel | Afspraak |
| --- | --- |
| Opdrachtgever | LogicAI / Code Art BV |
| Verplichte basis | Java, Spring Boot, Spring MVC en Thymeleaf |
| Product | Interne wiki voor één organisatie; geen SaaS-platform |
| Versie | 1.0 — 15 september 2026 |
| Planning | Mijlpalen; kalenderdata worden bij de start gekoppeld aan de stageduur |

### De opdracht

Ontwikkel een webapplicatie waarmee medewerkers bedrijfskennis kunnen vastleggen, organiseren, terugvinden en delen. De applicatie biedt een Notion-achtige schrijfervaring, maar heeft een bewust kleinere scope: een betrouwbare interne wiki, geen volledige Notion-kloon.

De wiki wordt gebruikt voor projectinformatie, technische documentatie, onboarding, werkinstructies, besluiten en interne procedures. Projecten en onboarding zijn gewone pagina’s met passende templates; hiervoor worden geen afzonderlijke CRM- of HR-systemen gebouwd.

Iedere pagina krijgt een vaste, rechtstreeks benaderbare server-URL. Spring Boot bepaalt de routes. JavaScript mag onderdelen interactief maken, maar neemt de paginarouting niet over.

### Het beoogde resultaat

Na oplevering kan een collega zonder hulp een projectpagina maken, subpagina’s toevoegen, opgemaakte inhoud schrijven, informatie zoeken en een blijvende link delen. Een beheerder kan de applicatie installeren, back-uppen en herstellen. Een andere ontwikkelaar kan het project overnemen.

AI mag als ontwikkelhulpmiddel worden gebruikt. Het opgeleverde product heeft geen LLM-integratie nodig en moet zonder AI-dienst functioneren.

### Leeswijzer

Pagina 2 beschrijft scope en gebruikers. Pagina’s 3–5 bevatten de requirements. Pagina’s 6–10 beschrijven routing, techniek, data, betrouwbaarheid en kwaliteit. Pagina’s 11–13 bevatten tests, werkwijze en beoordeling. Pagina 14 bevat de technische bronnen.

## 1. Scope en gebruikers

### Verplichte basis versus uitbreidingen

M — Must: nodig om de stageopdracht als werkend basisproduct te accepteren. S — Should: waardevolle uitbreiding na een stabiele basis. C — Could: alleen na overleg, wanneer tijd en niveau dit toelaten. Een extra feature compenseert geen ontbrekende beveiliging, databehoud of vaste URL’s.

De verplichte basis omvat inloggen, globale rollen, geneste pagina’s, een blokeditor, Markdown-shortcuts, zoeken, tags, templates, versiehistorie, herstel, Markdown-export en reproduceerbare installatie. S- en C-onderdelen staan op pagina 5. Nieuwe wensen worden eerst in de backlog gezet; ze vergroten niet automatisch de afgesproken MVP.

### Gebruikers en rechten

| Rol | Bevoegdheden in de verplichte basis |
| --- | --- |
| Viewer | Actieve pagina’s lezen, zoeken, historie bekijken en Markdown exporteren. |
| Editor | Alles van Viewer; pagina’s maken, bewerken, verplaatsen, naar de prullenbak verplaatsen en herstellen; tags toekennen en templates beheren. |
| Admin | Alles van Editor; gebruikers activeren/deactiveren, rollen beheren en globale tags beheren. |

Alle toegelaten gebruikers kunnen dezelfde actieve inhoud lezen. Er zijn in de MVP geen privépagina’s of rechten per pagina. De wiki is daarom in deze fase niet bedoeld voor personeelsdossiers, individuele beoordelingen, medische gegevens of andere informatie die niet voor alle toegelaten medewerkers bestemd is.

Bewaar geen wachtwoorden, API-sleutels of andere geheimen in de wiki. Een pagina mag wel verwijzen naar een item in de bestaande wachtwoordmanager. Onboarding betreft instructies en praktische checklists, geen vertrouwelijk HR-dossier.

### Buiten scope

Geen multi-tenancy, publieke registratie, klantenportaal, publieke deel-links, realtime samen typen, offline synchronisatie, Notion-databases, formules, kanban, workflow-automatisering of AI-functionaliteit. Ook een eigen richtext-editor vanaf nul en een volledige import van Notion-workspaces vallen buiten scope.

### Verantwoordelijkheden

De stagiair verzorgt analyse, ontwerp, implementatie, tests, documentatie en demonstraties. De bedrijfsbegeleider prioriteert de backlog, beoordeelt keuzes en stelt tijdig testgebruikers, een repository en een acceptatieomgeving beschikbaar.

De voorgestelde bedrijfslogin is Microsoft Entra ID via OpenID Connect. De begeleider levert hiervoor de app-registratie en configuratie. Voor lokaal ontwikkelen en geautomatiseerde tests zijn aparte testgebruikers toegestaan; deze mogen niet actief zijn in productie. Ontbrekende infrastructuur wordt als afhankelijkheid geregistreerd en niet opgelost door beveiliging uit te schakelen.

## 2. Functionele requirements — basis

Alle eisen op deze pagina hebben prioriteit M. De beschreven controles moeten aantoonbaar slagen.

| ID | Requirement en acceptatiecriterium |
| --- | --- |
| F-01 | Inloggen. Alleen expliciet toegelaten bedrijfsgebruikers krijgen toegang. Een directe paginalink leidt zo nodig via de login terug naar diezelfde pagina. Er is geen openbare registratie. |
| F-02 | Rollen en deactivering. De backend controleert iedere lees- en schrijfactie. Een Viewer kan ook met een handmatig HTTP-verzoek niets wijzigen. Een gedeactiveerde gebruiker wordt bij het volgende verzoek geweigerd, ook met een bestaande sessie. |
| F-03 | Pagina’s beheren. Een Editor kan een pagina aanmaken, lezen, bewerken en hernoemen. Elke pagina heeft een onveranderlijke UUID, titel, aanmaker, laatste bewerker en tijdstempels. Een lege titel wordt geweigerd; ingevoerde inhoud blijft beschikbaar. |
| F-04 | Hiërarchie. Pagina’s kunnen hoofdpagina of subpagina zijn. Een Editor kan een pagina inclusief haar bestaande onderliggende structuur verplaatsen. De backend voorkomt plaatsing onder zichzelf of een eigen afstammeling. De URL verandert niet. |
| F-05 | Navigatie en lezen. Een zijbalk toont de paginaorganisatie; breadcrumbs tonen de positie. Titel, inhoud en navigatie staan bij een leesverzoek al in de server-HTML. Lezen en gewone navigatie functioneren zonder JavaScript. |
| F-06 | Blokeditor. Ondersteun alinea’s, koppen, opsommingen, genummerde lijsten, checklists, codeblokken, citaten en scheidingslijnen. De gebruiker kan blokken toevoegen, wijzigen, verwijderen en verplaatsen. Knoppen voor verplaatsen zijn voldoende; drag-and-drop is S. |
| F-07 | Tekstopmaak. Ondersteun vet, cursief, inline code en hyperlinks. Code behoudt witruimte. Checkliststatus en lijstvolgorde blijven behouden na opslaan en herladen. Checklistitems wijzigen alleen in bewerkmodus. |
| F-08 | Markdown-shortcuts. Ondersteun minimaal koppen met #, lijsten met -, genummerde lijsten, checklists met - [ ], citaten met > en codeblokken met drie backticks. De precieze toetsinteractie wordt kort in de handleiding beschreven en getest. |

### Aanvullende afspraken voor de editor

De editor mag JavaScript nodig hebben. Bij ontbrekend JavaScript of een initialisatiefout verschijnt een duidelijke melding; een lege of kapotte editor mag nooit bestaande inhoud overschrijven. De leesweergave mag niet afhankelijk zijn van het starten van de editor.

De verplichte bediening bestaat uit een eenvoudige toolbar of blokkeuzemenu. Een slash-commandmenu, uitgebreide zwevende werkbalken, tabellen, kolommen en embeds zijn geen voorwaarden voor de eerste oplevering.

Directe links zijn een apart acceptatieonderdeel, geen cosmetische frontend-keuze. De uitwerking en routeafspraken staan op pagina 6.

## 3. Functionele requirements — kennisbeheer

Alle eisen op deze pagina hebben prioriteit M.

| ID | Requirement en acceptatiecriterium |
| --- | --- |
| F-09 | Betrouwbaar opslaan. Er is een expliciete knop Opslaan met status voor niet opgeslagen, bezig, opgeslagen en fout. Alleen na bevestiging van de server wordt “opgeslagen” getoond. Bij een netwerkfout of verlopen sessie blijft de tekst in de open editor beschikbaar. |
| F-10 | Gelijktijdig bewerken. Iedere wijziging bevat het versienummer waarop zij is gebaseerd. Een verouderde wijziging krijgt een conflictmelding en HTTP 409. Het systeem overschrijft niets stilzwijgend; de gebruiker kan eigen tekst behouden of kopiëren en de nieuwste versie apart openen. |
| F-11 | Versiehistorie. Bij aanmaken en iedere succesvolle wijziging van titel of inhoud ontstaat een onveranderlijke revisie met auteur en tijdstip. Revisies zijn leesbaar via een eigen URL. Herstel zet titel en inhoud terug als een nieuwe revisie; bestaande historie blijft intact. |
| F-12 | Prullenbak. Verwijderen is soft delete. Een pagina met niet-verwijderde subpagina’s kan niet worden verwijderd voordat die zijn verplaatst of verwijderd. Verwijderde pagina’s verdwijnen uit navigatie en zoekresultaten. Editors kunnen ze herstellen; permanent wissen valt buiten de MVP. |
| F-13 | Zoeken. Zoek op titel en tekstinhoud, niet alleen op metadata. Resultaten tonen titel, kort tekstfragment en een paginalink. Filteren op tag en paginering werken via queryparameters. Na opslaan, herstellen of verwijderen is het zoekresultaat bijgewerkt. |
| F-14 | Tags. Tags hebben een unieke naam zonder onderscheid tussen hoofd- en kleine letters. Editors kunnen tags maken en aan pagina’s koppelen. Admins kunnen tags hernoemen/verwijderen; daardoor verdwijnen geen pagina’s. |
| F-15 | Templates. Een Editor kan templates maken en bewerken en hiermee een nieuwe pagina aanmaken. De pagina krijgt een eigen UUID en een zelfstandige kopie van de inhoud. Latere wijzigingen in het template wijzigen bestaande pagina’s niet. |
| F-16 | Interne links. Een gebruiker kan een paginalink kopiëren en als gewone hyperlink invoegen. De verwijzing blijft werken na hernoemen en verplaatsen. Een verwijzing naar een verwijderde pagina toont een duidelijke melding in plaats van een willekeurige andere pagina. |
| F-17 | Markdown-export. Actieve pagina’s zijn per pagina als .md te exporteren. Alle verplichte blokken, basisopmaak, checkliststatus en linkbestemmingen blijven betekenisvol behouden. Ondersteuning voor aanvullende bloktypes wordt expliciet vastgelegd; verlies gebeurt niet stilzwijgend. |

### Nadere regels

Herstel van een revisie verandert geen rechten, bovenliggende pagina of tags. De prullenbak bewaart de pagina-identiteit; bij herstel onder een inmiddels verwijderde ouder kiest de gebruiker een actieve ouder of het hoofdniveau. Een geldige oude paginalink blijft zo naar hetzelfde object verwijzen.

De MVP kent geen apart publicatieproces: een geslaagde opslag is direct zichtbaar voor alle toegelaten lezers. Een concept/publicatiestroom is een mogelijke latere uitbreiding, geen impliciete requirement.

## 4. Uitbreidingen en voorbeeldinhoud

Uitbreidingen worden pas ingepland wanneer de verplichte basis aantoonbaar werkt. Hun acceptatiecriteria blijven gelden zodra ze daadwerkelijk worden gebouwd.

| ID / prio | Uitbreiding | Afbakening en acceptatie |
| --- | --- | --- |
| U-01 / S | Afbeeldingen en bijlagen | PNG, JPEG en PDF; standaard maximaal 10 MB per bestand. Servercontrole op formaat en grootte; opslag buiten publieke static-bestanden; download via autorisatie. SVG/HTML en overige actieve formaten zijn niet toegestaan. |
| U-02 / S | Markdown importeren | .md-import met preview voor de gedocumenteerde subset. Onbekende constructies leveren een melding op. Geen belofte van volledige Notion- of Markdown-roundtrips. |
| U-03 / S | Persoonlijke favorieten | Iedere gebruiker beheert alleen de eigen favorieten. Verwijderde pagina’s worden niet als normale actieve link getoond. |
| U-04 / S | Snellere blokbediening | Slash-commandmenu en drag-and-drop, met een toetsenbordalternatief. Bestaande inhoud blijft intact. |
| U-05 / S | Automatisch opslaan | Duidelijke status, begrensde frequentie, conflictcontrole en een bewuste revisiestrategie. Geen nieuwe historische versie per toetsaanslag. |
| U-06 / C | Paginalinksuggesties en backlinks | Zoeken naar een pagina tijdens het invoegen van een link; tonen welke pagina’s ernaar verwijzen. De pagina-ID blijft leidend. |
| U-07 / C | Eenvoudige properties | Een klein, vast gekozen setje velden, bijvoorbeeld eigenaar, status en reviewdatum. Geen generieke database-/formule-engine. |
| U-08 / C | Reviewherinneringen en bloklinks | Een eigenaar kan verouderde inhoud signaleren; optionele blijvende ankers naar specifieke blokken. Beide zijn afzonderlijke features. |

### Verplichte voorbeeldtemplates

Lever drie bruikbare templates op: Projectinformatie met contact, doel, repositorylinks, omgevingen, stack en onderhoud; Developer-onboarding met accounts, ontwikkelomgeving en eerste taken; Werkinstructie met doel, voorwaarden, stappen, controle en gerelateerde links.

Deze templates zijn documenten, geen integraties met GitHub, hosting, Microsoft 365 of andere systemen. Externe links worden niet automatisch opgehaald of uitgevoerd.

### Startinhoud voor de interne pilot

Richt drie hoofdpagina’s in: Projecten, Onboarding en Werkinstructies. Maak ten minste twaalf voorbeeldpagina’s, verspreid over minimaal drie niveaus, met alle verplichte bloktypen, meerdere tags en interne verwijzingen.

Gebruik fictieve of expliciet goedgekeurde inhoud. De begeleider kan bijvoorbeeld een echt projectdossier laten invoeren, maar bestaande technische details worden niet door de stagiair of een AI-model verzonnen.

### Geen volledige Notion-kopie

De acceptatie is gebaseerd op de requirements in dit document, niet op visuele of functionele gelijkheid met Notion. Een eenvoudige, consistente interface heeft voorrang op het nabouwen van alle interacties.

## 5. Harde URL’s en serverrouting

### Architectuurcontract — verplicht

Elke zichtbare applicatiepagina is rechtstreeks op te vragen via een Spring MVC-controller en wordt met Thymeleaf als volledige HTML-pagina geleverd. Een app-shell die pas na JavaScript de pagina bepaalt en ophaalt voldoet niet. Thymeleaf biedt hiervoor server-side integratie met Spring MVC. [3]

| GET-route | Betekenis |
| --- | --- |
| / | Startpagina met navigatie en recent gewijzigde pagina’s |
| /pages | Paginaoverzicht |
| /pages/new?parentId={id} | Nieuwe pagina aanmaken |
| /pages/{id} | Pagina lezen; permanente referentie |
| /pages/{id}/edit | Pagina bewerken |
| /pages/{id}/history | Revisieoverzicht |
| /pages/{id}/history/{revisionId} | Eén historische revisie lezen |
| /pages/{id}/export.md | Markdown-export downloaden |
| /search?q=deploy&tag=java&page=1 | Deelbaar zoekresultaat |
| /templates en /templates/{id}/edit | Templates bekijken en beheren |
| /trash | Prullenbak voor Editors en Admins |
| /admin/users | Gebruikers- en rollenbeheer |

De UUID is de identiteit; titel en plaats in de paginaboom zijn dat niet. Begin met /pages/{id}. Een leesbare slug mag later als toevoeging, maar mag nooit vereist zijn om het object terug te vinden. Twee pagina’s met dezelfde titel hebben verschillende ID’s en links.

### Wat JavaScript wel en niet doet

JavaScript, TypeScript, Vue of React mogen een editor, dialoog, upload of andere lokale component verzorgen. Ook een klein JSON-endpoint voor opslaan is toegestaan. Normale paginalinks blijven echte anchors met een href naar de server.

Geen React Router, Vue Router, hash-routing, client-side historyrouter of algemene SPA-fallback. Geen opvang van alle navigatie om schermen uitsluitend in de browser te wisselen. HTMX is alleen toegestaan voor lokale fragmentupdates, niet als vervanging van het routecontract.

### HTTP-gedrag

Normale formulieren gebruiken POST en na succes een serverredirect naar een GET-pagina. GET-verzoeken wijzigen geen data. De editor mag inhoud via fetch opslaan, bijvoorbeeld met PUT /api/pages/{id}/content; dat endpoint bestuurt geen schermrouting.

Een onbekende pagina geeft 404; onvoldoende rechten 403; een versieconflict 409. HTML-verzoeken zonder sessie leiden naar login; editor-API-verzoeken krijgen 401 zodat de editor geen login-HTML voor een succesvolle opslag aanziet. Terugkeer-URL’s na login blijven binnen de applicatie.

Acceptatie: plakken in een nieuw tabblad, vernieuwen, terug/vooruit, openen vanuit een extern document en opnieuw openen na inloggen werken zonder voorafgaande clientstatus.

## 6. Tech stack en applicatieopbouw

### Voorgeschreven en voorgestelde keuzes

De versies hieronder zijn het voorgestelde startpunt. De stagiair legt concrete patchversies vast in de repository. Gebruik stabiele releases en laat compatibele Spring-componenten door Spring Boot dependency management beheren; geen snapshots of losse willekeurige Spring-versies. Java 25 is LTS; de geraadpleegde Boot-documentatie noemt 4.1.1 als stabiele versie die Java 25 ondersteunt. [1, 2]

| Onderdeel | Keuze |
| --- | --- |
| Taal en runtime | Java 25 LTS, Eclipse Temurin |
| Backend | Spring Boot 4.1.x; Spring MVC, Data JPA, Security en Validation |
| HTML | Thymeleaf, inclusief herbruikbare layout- en formulierfragmenten |
| Database | PostgreSQL 18; dezelfde databasefamilie in test en productie |
| Migraties | Flyway; productie-schema niet automatisch laten wijzigen door Hibernate |
| Build | Maven Wrapper; reproduceerbare installatie en testcommando’s |
| Editor | BlockNote via lokale React-island; geen client-side router |
| Browsercode | TypeScript + React + Vite voor lokale BlockNote-editor; geen client-side router |
| Vormgeving | Eén consistente set eigen CSS of Bootstrap; lokale assets |
| Authenticatie | Spring Security met sessies; Entra ID via OpenID Connect voor bedrijfslogin |
| Tests | Spring Boot Test, JUnit, MockMvc, Testcontainers/PostgreSQL; Playwright voor browserflows |
| Uitrollen | Dockerfile en Docker Compose voor app en database; persistente volumes |
| CI | GitHub Actions: backendtests, frontendbuild en afgesproken browsertests |

BlockNote is de voorgeschreven block-based editor en wordt als lokale React-component op de bewerkpagina gemount. Spring MVC en Thymeleaf blijven eigenaar van routing, navigatie en server-HTML. [4]

Bewaar pagina-inhoud als BlockNote block-JSON. Het documentmodel bevat block-ID, type, properties, content en child blocks. Markdown-export is lossy; alleen de afgesproken en geteste subset is acceptatie-eis. [5, 6]

### Opbouw

Bouw één applicatie met herkenbare modules, bijvoorbeeld pages, templates, search, users en security. Controllers handelen HTTP af; services bevatten transacties en bedrijfsregels; repositories verzorgen persistentie. Formulieren en API-verzoeken gebruiken eigen inputmodellen, geen onbeperkte binding rechtstreeks op JPA-entiteiten.

De BlockNote-editor wordt alleen op /pages/{id}/edit geladen als lokale React-component in een door Thymeleaf geleverde pagina. Node.js/Vite is uitsluitend een buildhulpmiddel; Spring Boot blijft de enige productiewebserver.

## 7. Datamodel en documentopslag

### Richtinggevend model

Onderstaande indeling is een vertrekpunt. De stagiair werkt het uit tot een relationeel schema met sleutels, constraints, indexen en migraties en licht afwijkingen toe.

| Entiteit | Minimale verantwoordelijkheid |
| --- | --- |
| User | Lokale ID, externe issuer en subject, weergavenaam, e-mail, rol en actiefstatus. De combinatie issuer/subject identificeert de login; e-mail is geen onveranderlijke sleutel. |
| Page | UUID, optionele parent, titel, JSON-document, documentschemaversie, afgeleide zoektekst, makers/bewerkers, tijdstempels, prullenbakstatus en lockVersion. |
| PageRevision | Pagina-ID, uniek revisienummer, snapshot van titel en document, documentschemaversie, auteur en tijdstip. Onveranderlijk. |
| Tag / PageTag | Tags en de veel-op-veelrelatie met pagina’s; normalisatie en unieke naam. |
| PageTemplate | Titel, omschrijving, JSON-document, documentschemaversie, auteur, tijdstempels en eigen lockVersion. |
| AuditEvent | Wie deed wat en wanneer: verplaatsen, verwijderen, herstellen en wijzigen van rollen/actiefstatus. Geen volledige documentinhoud of geheimen in het event. |

Favorite en Attachment worden alleen toegevoegd wanneer de bijbehorende uitbreiding wordt ingepland. Geen Company- of Tenant-entiteit voor een toekomstig SaaS-product: één organisatie is een bewuste beperking.

### Eén bron van waarheid voor documentinhoud

Sla de inhoud op als een gevalideerd blokdocument in PostgreSQL JSONB. Gebruik in de MVP niet tegelijk een los Markdown-bestand, HTML-document én relationele bloktabellen als onafhankelijk bewerkbare bronnen. PostgreSQL ondersteunt JSONB naast relationele gegevens; tekstzoeken kan eveneens in PostgreSQL worden uitgevoerd. [7, 8]

Het BlockNote-documentformaat is het vertrekpunt, niet een tweede zelfverzonnen editorstandaard. Maak een gedocumenteerde subset van toegestane block-types, properties en tekstopmaak. Bewaar volgorde en nesting volgens het BlockNote-model. Onbekende of ongeldige inhoud wordt met een fout geweigerd, niet ongemerkt weggegooid.

Het veld documentSchemaVersion beschrijft de structuur van de inhoud. Het veld lockVersion detecteert gelijktijdige wijzigingen. Het revisienummer beschrijft de historie. Deze drie begrippen zijn niet onderling uitwisselbaar.

### Lezen en zoeken

Een Java-renderer of set Thymeleaf-fragmenten vertaalt de ondersteunde BlockNote-blocks naar veilige server-HTML. Willekeurige HTML uit de browser wordt niet vertrouwd. Dezelfde gevalideerde inhoud levert server-side de gewone tekst voor de zoekindex op.

Werk zoektekst en revisie bij in dezelfde database-transactie als de paginaopslag. Zoek minimaal op titel en inhoud met een expliciet gekozen taalconfiguratie die ook technische termen bruikbaar vindt. Test Nederlands, Engels en termen zoals Spring Boot en PostgreSQL; leg bekende zoekbeperkingen vast.

Migraties. Databasewijzigingen lopen via Flyway. Wijzigingen van het documentformaat vragen een expliciete compatibiliteits- of migratiestrategie, inclusief oude revisies. Een editor-upgrade mag bestaande pagina’s niet onleesbaar maken.

## 8. Opslaan, versiebeheer en herstel

### Het verplichte opslagpad

De editor verstuurt bij een handmatige opslag het document, de gewijzigde titel en de versie waarop de bewerking is gebaseerd. De server valideert gebruiker, rechten, paginastatus, documentstructuur en versie.

Binnen één transactie worden de pagina, afgeleide zoektekst en nieuwe revisie opgeslagen. Pas na een succesvolle commit ontvangt de editor een bevestiging met de nieuwe versie en het opslagtijdstip. De applicatie toont bij een fout geen succesmelding.

Een ongewijzigde opslag hoeft geen extra revisie op te leveren. De eerste opgeslagen pagina heeft wel een beginrevisie. Het herstel van een historische revisie wordt als een nieuwe wijziging opgeslagen en doorloopt dezelfde validatie en conflictcontrole.

### Gelijktijdig bewerken zonder realtime samenwerking

Gebruik optimistic locking, bijvoorbeeld via JPA @Version. Controleer ook de door de gebruiker aangeleverde basisversie; alleen een versiekolom op een vers geladen entiteit is niet voldoende om verouderde browserinhoud te herkennen.

Scenario: Editor A en Editor B openen versie 7. Editor A slaat versie 8 op. De wijziging van Editor B op basis van versie 7 wordt geweigerd met HTTP 409. De eigen tekst van Editor B blijft beschikbaar. De gebruiker kan de actuele versie apart openen en de wijziging opnieuw verwerken. De MVP hoeft geen automatische merge te bouwen.

Die controle geldt ook voor relevante metadatawijzigingen, herstelacties en templates. Verplaatsen van pagina’s mag ook bij gelijktijdige verzoeken geen cyclus in de boom opleveren; leg de gekozen transactiestrategie vast en test deze.

### Niet-opgeslagen werk

Waarschuw bij het verlaten van een gewijzigde editor waar de browser dit ondersteunt. Bij netwerkproblemen of een verlopen sessie blijft de open editor intact en kan de gebruiker de eigen inhoud kopiëren of exporteren. Beloftes over herstel na een browsercrash of een bewust herladen tabblad horen niet bij de MVP; daarvoor is later een apart draftmechanisme nodig.

### Back-up en restore

Lever scripts of reproduceerbare commando’s voor databaseback-up en herstel op. Bijlagen worden, zodra aanwezig, als onderdeel van dezelfde herstelprocedure behandeld. Een Markdown-export is een gebruikersfunctie en vervangt geen databaseback-up met historie, gebruikers en metadata.

De begeleider wijst vóór ingebruikname een back-uplocatie en eigenaar aan. Voor de pilot is dagelijks back-uppen het voorstel, met een expliciet vastgelegde bewaartermijn. Toon tijdens acceptatie een echte restore in een lege omgeving en controleer inhoud, links, revisies en eventuele bijlagen.

### Vertrouwen boven feature-aantal

Een mooie editor met kans op stil gegevensverlies is geen geslaagde oplevering. Betrouwbaar bewaren, kunnen terugvinden en kunnen herstellen hebben voorrang op extra opmaakmogelijkheden.

## 9. Niet-functionele requirements

| ID | Verplichte eis |
| --- | --- |
| N-01 | Server-side autorisatie. Controleer sessie, actiefstatus en rol bij alle pagina-, historie-, zoek-, export- en mutatieroutes. Een UUID is geen beveiligingsmaatregel. Toegang buiten de eigen toegelaten bedrijfsaccounts wordt geweigerd. |
| N-02 | Browserbeveiliging. CSRF-bescherming blijft actief bij mutaties, ook voor fetch-verzoeken. Cookies zijn in productie Secure en HttpOnly; configureer SameSite passend bij de OIDC-loginflow. Test de volledige loginflow. [9, 10, 11] |
| N-03 | Veilige inhoud. Escape tekst en valideer node-types, attributen en linkbestemmingen server-side. Geen script-URL’s, raw-HTML-blokken of ongefilterde HTML-injectie. Inline scripts in geplakte tekst mogen nooit worden uitgevoerd. |
| N-04 | Configuratie. Geen wachtwoorden of secrets in Git, frontendbundels, wiki-inhoud of logs. Productie gebruikt HTTPS. Testaccounts en testconfiguratie kunnen niet per ongeluk als productielogin worden gebruikt. |
| N-05 | Validatie en limieten. Stel expliciete grenzen in voor titels, documentgrootte, nesting en blokken. Voorstel: 200 tekens per titel en 2 MB per document. Valideer op de server en geef een herstelbare foutmelding. |
| N-06 | Bruikbaarheid. Een collega kan zonder ontwikkelhulp een template kiezen, een pagina invullen en de link delen. Er zijn herkenbare lege toestanden, foutmeldingen, opslagstatussen en bevestigingen bij verwijderen/herstellen. |
| N-07 | Toegankelijkheid. Kernbediening via toetsenbord, zichtbare focus, labels op formulieren en knoppen, en betekenisvolle koppen. Drag-and-drop heeft altijd een alternatief. Kleur is niet de enige informatiedrager. |
| N-08 | Onderhoudbaarheid. Kleine, verklaarbare modules; centrale bedrijfsregels; geen duplicatie van beveiligings- of opslaglogica. Versies zijn vastgelegd en gebruikte licenties geïnventariseerd. |
| N-09 | Installatie en beheer. Een collega kan met README en configuratievoorbeeld de applicatie opstarten. Docker-volumes bewaren data na herstart. Healthchecks en technische logging bestaan zonder documentinhoud of secrets te lekken. |

### Schaal en prestaties — afgesproken testdoel

Gebruik als voorstel een testdataset met 10.000 pagina’s van gemiddeld 20 blokken en een afzonderlijke pagina met 100 blokken. Test tien gelijktijdige gebruikers op een vastgelegde omgeving, bijvoorbeeld 2 vCPU en 4 GB RAM voor app en database samen.

Streef na opwarmen naar p95 serverresponstijd onder 500 ms voor leesroutes en onder 1 seconde voor zoeken, exclusief externe login en netwerkvertraging. Dit zijn ontwerp- en acceptatiedoelen, geen gemeten prestaties of huidige gebruikersaantallen. Leg dataset, hardware, meetmethode en resultaten vast; bespreek afwijkingen vóór de eindacceptatie.

Laad grote overzichten niet onbeperkt in één keer. Navigatie mag met serverlinks per tak of niveau worden opgebouwd; duizenden pagina’s hoeven niet allemaal tegelijk in de zijbalk te staan.

## 10. Testplan en acceptatiescenario’s

De stagiair koppelt requirements aan tests in een eenvoudige matrix: requirement-ID, test, resultaat en eventueel resterend issue. Test de kernlogica geautomatiseerd; handmatige controles vullen aan maar vervangen de regressietests niet.

| Niveau | Minimale dekking |
| --- | --- |
| Unit | Titel- en documentvalidatie, linkvalidatie, cycli in de boom, kopiëren van templates, Markdown-export en regels voor herstel. |
| Integratie | PostgreSQL via Testcontainers; JPA-mapping, JSON-opslag, Flyway, zoekquery’s, revisies, transacties en versieconflicten. |
| MVC/security | Routes en statuscodes, rollen, deactivering, CSRF, redirects en server-side rendering. Geen alleen-H2-teststrategie voor PostgreSQL-specifieke functies. |
| Browser | Aanmaken, schrijven, opslaan, herladen, hernoemen, verplaatsen, conflict afhandelen en herstellen. Test ook leesnavigatie met JavaScript uitgeschakeld. |
| Beheer | Reproduceerbare installatie, data na herstart en herstel van back-up in een lege omgeving. |

### Verplichte einddemonstraties

| Test | Scenario en verwacht resultaat |
| --- | --- |
| A-01 | Maak een projectpagina uit een template, voeg een subpagina en code/checklist toe, sla op en herlaad. Inhoud, opmaak en relaties zijn behouden. |
| A-02 | Kopieer de link, hernoem en verplaats de pagina. Open de oorspronkelijke link in een nieuwe browsersessie; dezelfde pagina verschijnt, zo nodig na login. |
| A-03 | Open rechtstreeks de edit-, historie- en zoek-URL. Refresh en terug/vooruit werken. Met JavaScript uit zijn actieve inhoud en gewone navigatie nog beschikbaar. |
| A-04 | Open dezelfde pagina in twee editors en sla verschillende wijzigingen op. De tweede verouderde opslag wordt geblokkeerd; geen tekst wordt stilzwijgend overschreven. |
| A-05 | Herstel een eerdere revisie. De eerdere inhoud is actueel en de tussenliggende revisies bestaan nog. |
| A-06 | Verwijder een bladpagina en herstel haar. Zoekresultaten worden correct bijgewerkt. Verwijderen van een pagina met actieve kinderen wordt geweigerd. |
| A-07 | Probeer als Viewer een mutatie en als gedeactiveerde gebruiker een bestaand sessieverzoek. Beide worden server-side geweigerd. Test eveneens CSRF en onveilige inhoud. |
| A-08 | Simuleer netwerkuitval en een verlopen sessie tijdens opslaan. Geen valse succesmelding; de open editor behoudt de eigen inhoud. |
| A-09 | Wijzig een template na het aanmaken van een pagina. De bestaande pagina verandert niet. Exporteer de pagina en controleer de Markdown-subset. |
| A-10 | Start vanuit een lege installatie en herstel de back-up. De eerder gedeelde links, inhoud en versiehistorie werken opnieuw. |

Een feature is gereed wanneer de relevante tests slagen, de code is gereviewd, gebruikersfouten begrijpelijk worden afgehandeld en de documentatie is bijgewerkt. Een build zonder compilerfouten alleen is onvoldoende.

## 11. Aanpak, mijlpalen en oplevering

De duur van de stage is nog niet vastgelegd in deze opdracht. Onderstaande mijlpalen zijn daarom inhoudelijk, niet gekoppeld aan verzonnen kalenderweken. Plan ze bij de start en reserveer expliciet ruimte voor testen, pilotfeedback en overdracht.

| Mijlpaal | Werkend resultaat en beslismoment |
| --- | --- |
| M1 — Analyse en ontwerp | Kort gesprek met minimaal twee toekomstige gebruikers; drie wireframes, domeinmodel, URL-overzicht, backlog en risico’s. Begeleider keurt de afbakening goed. |
| M2 — Verticale basis | Login/testrollen, PostgreSQL, migraties, Thymeleaf-layout en één pagina die via een vaste URL aangemaakt, opgeslagen en gelezen kan worden. Bewijs eerst dit hele pad. |
| M3 — Schrijven en ordenen | Alle verplichte blokken, Markdown-shortcuts, paginaboom, hernoemen/verplaatsen, tags, templates en veilige leesrendering. |
| M4 — Betrouwbare wiki | Zoekfunctie, prullenbak, versiehistorie, conflictbehandeling, herstel en Markdown-export; bijbehorende regressietests. |
| M5 — Interne pilot | Voorbeeldinhoud, gebruik door minimaal twee collega’s, geregistreerde feedback, deployment, back-up/restore en securitychecks. |
| M6 — Overdracht | Open issues geprioriteerd, tests en acceptatiescenario’s uitgevoerd, technische documentatie afgerond en demonstratie door de stagiair. |

S- en C-features worden alleen tussen deze mijlpalen ingepland na expliciete prioritering. Toon bij ieder reviewmoment werkende software, niet alleen screenshots of een hoeveelheid gegenereerde code.

### Op te leveren

Lever één repository met broncode, Maven Wrapper, frontend-lockfile, migraties, testdata, tests, CI-configuratie, Dockerfile en Compose-configuratie. Voeg een configuratievoorbeeld zonder secrets toe.

De documentatie omvat minimaal: README voor installatie; gebruikershandleiding; technisch ontwerp met datamodel en URL-contract; afspraken voor het documentformaat; test-/acceptatiematrix; beheerhandleiding voor back-up en restore; bekende beperkingen en een geprioriteerde vervolgbacklog.

Leg belangrijke keuzes vast in korte Architecture Decision Records. Minimaal: editor en licenties, serverrouting, documentopslag/rendering, gelijktijdig bewerken en authenticatie. Beschrijf keuze, alternatief en consequentie; geen omvangrijk theoretisch ontwerp vooraf.

### Eerste concrete ontwikkeltaak

Bouw na het ontwerp één verticale flow: een toegelaten gebruiker maakt een pagina, voert titel en één alinea in, slaat op en opent de vaste URL in een nieuw tabblad. Inhoud komt uit PostgreSQL en staat in de Thymeleaf-HTML. Voeg hier een integratietest aan toe.

Pas daarna worden uitgebreidere blokken, templates en geschiedenis toegevoegd. Zo staat niet eerst een mooie editor naast een nog niet werkende backend.

## 12. Leerdoelen en beoordeling

### Wat de stagiair aan het einde moet kunnen uitleggen

| Leerdoel | Aantoonbaar bewijs |
| --- | --- |
| Requirements en scope | Van gebruikerswens naar afgebakende user story met toetsbare acceptatiecriteria; onderbouwd onderscheid tussen M, S en C. |
| Java en Spring | Uitleg van request naar controller, service, transactie en repository; werkende validatie, foutafhandeling en autorisatie. |
| Databases | Relaties, constraints, indexen, JSONB, migraties en de keuze tussen actuele data en historische snapshots. |
| Webontwikkeling | Uitleg van serverrouting versus lokale browserinteractie; waarom directe links en server-HTML blijven werken. |
| Betrouwbaarheid | Een versieconflict kunnen reproduceren en verklaren; verschillen tussen revisies, locks, exports en back-ups begrijpen. |
| Testen en beheer | Een regressietest toevoegen, de CI-resultaten interpreteren en zelfstandig een lege installatie en restore uitvoeren. |

### Gebruik van Codex en Claude Code

AI-ontwikkeltools zijn toegestaan voor onderzoek, uitleg, codevoorstellen, tests en reviews. De stagiair blijft verantwoordelijk voor de code en moet belangrijke keuzes en wijzigingen zelf kunnen toelichten. De applicatie bevat geen LLM-functie als onderdeel van deze opdracht.

Werk in kleine, reviewbare stappen. Beschrijf bij een relevante pull request de bedoeling, gekozen aanpak, uitgevoerde tests en bekende beperkingen. Noteer bij belangrijke door AI voorgestelde oplossingen wat is gecontroleerd of aangepast; een logboek van iedere prompt is niet nodig.

Productiegegevens, persoonsgegevens, wachtwoorden en niet-goedgekeurde klantcode worden niet aan ontwikkeltools verstrekt. Gebruik alleen de door LogicAI toegelaten accounts en werkwijze. Schakel tests, CSRF, autorisatie of validatie niet uit om een gegenereerde oplossing “werkend” te krijgen.

### Voorgesteld beoordelingskader

| Onderdeel | Gewicht |
| --- | --- |
| Functioneel resultaat en bruikbaarheid | 30% |
| Java/Spring, architectuur en datamodel | 25% |
| Tests, beveiliging en gegevensbehoud | 25% |
| Werkwijze, uitleg en overdracht | 20% |

Dit is een voorstel voor de bedrijfsbeoordeling, niet een vervanging van eventuele opleidingseisen. Stem het bij de start af. Aantallen features, regels code en AI-prompts zijn geen zelfstandige maat voor een goede stage.

### Eindacceptatie

Alle M-requirements en A-scenario’s zijn aangetoond. Er zijn geen openstaande kritieke problemen met toegang, dataverlies of herstel. Een collega kan de applicatie installeren en gebruiken met de documentatie. De pilotfeedback is verwerkt of expliciet als vervolgwerk geaccepteerd.

## Technische bronnen

Geraadpleegd op 15 september 2026. De requirements en acceptatiegrenzen zijn ontwerpkeuzes voor deze opdracht; onderstaande primaire bronnen onderbouwen de genoemde technologieën. Controleer concrete patchversies en licenties opnieuw bij de projectstart.

[1] Eclipse Adoptium — Temurin Support. Ondersteuning en LTS-status van Java 25.

https://adoptium.net/support

[2] Spring Boot — System Requirements. Stabiele versies, Java-compatibiliteit en buildvoorwaarden.

https://docs.spring.io/spring-boot/system-requirements.html

[3] Thymeleaf — Thymeleaf + Spring. Integratie met Spring MVC, formulieren en templates.

https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html

[4] BlockNote — Getting Started. Block-based editor met React-integratie en kant-en-klare editor-UI.

https://www.blocknotejs.org/docs/getting-started

[5] BlockNote — Document Structure. Block-ID’s, types, properties, content en child blocks als documentmodel.

https://www.blocknotejs.org/docs/foundations/document-structure

[6] BlockNote — Markdown Export. Markdown-export is lossy; native block-JSON is de opslagvorm voor documentinhoud.

https://www.blocknotejs.org/docs/features/export/markdown

[7] PostgreSQL 18 — JSON Types. JSONB en het combineren van documenten met relationele data.

https://www.postgresql.org/docs/18/datatype-json.html

[8] PostgreSQL — Full Text Search Introduction. Zoeken in documenten en rangschikken van resultaten.

https://www.postgresql.org/docs/current/textsearch-intro.html

[9] Spring Security — CSRF. Bescherming voor formulieren en JavaScript-verzoeken.

https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html

[10] Spring Security — OAuth 2.0 Login Core Configuration. Login-integratie, inclusief OpenID Connect.

https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html

[11] Microsoft Learn — OpenID Connect on the Microsoft identity platform. Bedrijfslogin via OIDC.

https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols-oidc

