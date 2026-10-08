# Export: Markdown (F-17) en PDF

Iedere actieve pagina is te downloaden via het download-icoon op de leesweergave. Dat opent een menu met twee keuzes:
**Downloaden als .md** (`GET /pages/{id}/export.md`, spec F-17) en **Downloaden als .pdf** (`GET /pages/{id}/export.pdf`,
een extra buiten de MVP). Voor beide geldt: iedere ingelogde rol mag exporteren (ook Viewer, sectie 1 en N-01); een pagina
in de prullenbak geeft 404; de bestandsnaam is een slug van de titel (`deploy-handleiding.md` / `.pdf`), zonder letters of
cijfers in de titel `pagina-{id}`. De export is een gebruikersfunctie en geen back-up: historie en rechten zitten er niet in.

## PDF-export

De PDF wordt server-side gemaakt door `PdfExporter` (OpenHTMLtoPDF) uit dezelfde HTML die `BlockRenderer` voor de
leesweergave maakt; het sjabloon is `templates/export-pdf.html` (A4, titel, regel met versie, datum en tags, voettekst met
de permanente paginalink en paginanummers). Er is dus geen derde vertaling van de blokken naast HTML en Markdown.
Verschillen met de leesweergave, getest in `PdfExporterTest`:

| Onderdeel | In de PDF | Opmerking |
| --- | --- | --- |
| geüploade afbeelding | ingesloten, op de gekozen breedte | anders dan bij Markdown is geen wikisessie nodig om haar te zien; ontbreekt het bestand, dan staat er "Afbeelding ontbreekt" |
| geüploade video | link "Video: bijschrift" naar `/attachments/{id}` | een PDF speelt niets af; de link werkt alleen met een wikisessie |
| YouTube-link in een eigen alinea | alleen de link | de speler vervalt |
| geüploade PDF (blok file) | link naar `/attachments/{id}` | het bestand zelf wordt niet samengevoegd |
| checklist | `[x]` / `[ ]` als tekst | |
| interne links (`/pages/{id}`, `/attachments/{id}`) | absoluut gemaakt met de wiki-URL | klikbaar vanuit de PDF |
| tekstkleur en achtergrondkleur | **vervalt** | de leesweergave toont ze ook niet |
| lettertype | standaard PDF-fonts (Helvetica, Courier) | West-Europese tekens werken; emoji en niet-Latijnse schriften worden niet weergegeven (bekende beperking, een gebundeld lettertype is een latere verbetering) |

## Markdown-export

De export is bewust lossy (stageopdracht, sectie 7). Onderstaande tabel legt vast wat behouden blijft en wat niet.
De regels zijn getest in `MarkdownExporterTest`; de code staat in `MarkdownExporter`.

## Opbouw van het bestand

```
---
title: "Paginatitel"
tags:
  - "java"
source: https://wiki.example/pages/{id}
version: 3
updated: 2026-09-18T10:00:00Z
---

# Paginatitel

...inhoud...
```

De front matter bevat de permanente paginalink (de UUID is de identiteit, sectie 5), de tags en het revisienummer.
De bestandsnaam is een slug van de titel (`deploy-handleiding.md`); zonder letters of cijfers in de titel wordt het `pagina-{id}.md`.

## Vertaling per blok

| BlockNote | Markdown | Opmerking |
| --- | --- | --- |
| paragraph | tekst, lege regel tussen blokken | regelovergang binnen een alinea wordt een harde regelbreuk (`\` aan het regeleinde) |
| heading (niveau 1–6) | `#` … `######` | regelovergangen in een kop worden een spatie |
| bulletListItem | `- ` | |
| numberedListItem | `1. `, `2. `, … | nummering telt per aaneengesloten lijst vanaf 1 |
| checkListItem | `- [ ] ` / `- [x] ` | checkliststatus blijft behouden |
| geneste blokken onder een lijstitem | vier spaties ingesprongen | een alinea onder een lijstitem krijgt een lege regel ervoor |
| codeBlock | ``` ```taal ``` … ``` ``` ``` | hek wordt langer als de code zelf backticks bevat; taal `text` wordt weggelaten |
| quote | `> ` per regel | |
| divider | `---` | |
| image (geüploade PNG/JPEG) | `![bestandsnaam](https://wiki.example/attachments/{id})` + bijschrift cursief | de afbeelding zelf zit niet in de export; de link werkt alleen met een wikisessie; de gekozen breedte gaat verloren |
| file (geüploade PDF) | `[Bijlage: bestandsnaam](https://wiki.example/attachments/{id})` + bijschrift cursief | het bestand zelf zit niet in de export; de link werkt alleen met een wikisessie |
| video (geüpload bestand) | `[Video: bestandsnaam](https://wiki.example/attachments/{id})` + bijschrift cursief | het videobestand zelf zit niet in de export; de link werkt alleen met een wikisessie |

## Vertaling van tekstopmaak en links

| Opmaak | Markdown | Opmerking |
| --- | --- | --- |
| bold | `**tekst**` | |
| italic | `*tekst*` | |
| strike | `~~tekst~~` | GitHub-Flavored Markdown |
| code | `` `tekst` `` | |
| underline | `<u>tekst</u>` | Markdown kent geen onderstreping; inline HTML |
| textColor, backgroundColor | **vervalt** | Markdown kent geen kleuren; dit is het enige verlies van opmaak |
| link | `[tekst](bestemming)` | interne links (`/pages/{id}`, `/attachments/{id}`) worden absoluut gemaakt met de wiki-URL; bestemmingen met spaties of haakjes staan tussen `<` en `>` |

Tekens die in Markdown een betekenis hebben (`*`, `_`, `#`, `[`, `<`, `>`, `` ` ``, `~`, `\`) worden in gewone tekst
ge-escaped, zodat een pagina met de letterlijke tekst `*niet vet*` ook in de export niet vet wordt. Ook `- `, `1. `,
`#` en `>` aan het begin van een regel worden ge-escaped.

## Bekende beperkingen

- Nesting van niet-lijstblokken (een alinea onder een alinea) heeft geen Markdown-vorm: de kinderen volgen het blok op hetzelfde niveau.
- Kleuren vervallen (zie boven).
- Er is geen Markdown-import (U-02, buiten de MVP); een roundtrip is niet beloofd.
