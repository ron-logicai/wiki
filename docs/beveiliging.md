# Beveiliging: kwetsbaarheden in Java en afhankelijkheden

Dit document beschrijft waar de wiki van afhangt, hoe nieuwe kwetsbaarheden opvallen en wat je doet als er een
bekend wordt. Het hoort bij spec N-04 en N-08.

## Java4Shell (CVE-2026-114938), november 2026

Een fout in de Java-runtime zelf, niet in een library. De versnelde Base64-decoder van OpenJDK 17.0.6 t/m 27
schrijft buiten zijn buffer. Een aanvaller zonder account kan de JVM laten crashen, geheugen uitlezen of code
uitvoeren. De fout speelt alleen op x86-64-processors met AVX-512 VBMI.

De wiki decodeert Base64 al vóór het inloggen (onder meer het CSRF-token), dus de wiki geldt als bereikbaar
voor deze aanval. Op 20 november 2026 is de wiki op verzoek van Java 25 naar Java 27 gegaan. Dat lost het lek
niet op: Java 27 is ook getroffen.

**Stand op 20 november 2026**

| Onderdeel | Stand |
|---|---|
| Gecorrigeerde runtime | Temurin 27.0.1.1+4 (bevestigd door Adoptium) |
| Docker Hub | `eclipse-temurin:27-jre` is nog 27.0.1.0, er bestaat nog geen 27.0.1.1-tag |
| Noodmaatregel | `-XX:-UseBASE64Intrinsics` in `Dockerfile` en `fly.toml` |
| Productie (Fly) | pas beschermd na een deploy; de vlag werkt alleen na een herstart |

**Wat nog moet gebeuren**

1. Deployen naar Fly, zodat de vlag in productie actief is.
2. Controleren in productie: `fly ssh console -C "jcmd 1 VM.flags -all"` toont `-XX:-UseBASE64Intrinsics`.
3. Zodra Docker Hub een 27.0.1.1-tag heeft: opnieuw bouwen en deployen, en nagaan dat `java -version` in de
   container 27.0.1.1 of hoger meldt. De CI-job "Image scan" drukt die versie bij elke run af.
4. Daarna mag de vlag weg uit `Dockerfile` en `fly.toml`, samen met de stap "Base64-intrinsic staat uit" in
   `.github/workflows/ci.yml`.
5. Op ontwikkelmachines Temurin JDK 27.0.1.1 of hoger installeren.

Let op bij Java 27: het is geen LTS (de spec noemt Java 25 LTS) en de ondersteuning stopt in maart 2027. Dan
moet de wiki naar Java 28 of terug naar een LTS. Spring Boot 4.1.1 noemt Java 26 als hoogste ondersteunde versie.

Niet van toepassing op de wiki: aanvallen zoals Log4Shell. De wiki gebruikt logback, geen `log4j-core`, en
bevat geen JNDI-lookups of Java-deserialisatie.

## Waar Java draait

| Plek | Runtime | Hoe bijwerken |
|---|---|---|
| Productie op Fly | `eclipse-temurin:27-jre` uit de `Dockerfile` | opnieuw bouwen en deployen |
| `docker compose up` | dezelfde image | `docker compose build --pull` |
| CI | `actions/setup-java`, Temurin 27 | haalt bij elke run de nieuwste 27 op |
| Ontwikkelmachine | lokaal geïnstalleerde Temurin JDK 27 | handmatig, via adoptium.net |

Versie controleren: `java -version`, of in een container `docker run --rm --entrypoint java <image> -version`.

## Hoe nieuwe kwetsbaarheden opvallen

- **Dependabot** (`.github/dependabot.yml`) controleert wekelijks Maven, npm (`frontend/`), de Docker-images en
  de GitHub Actions, en opent een pull request per update.
- **Image scan in CI** (job `image-scan`): Trivy scant de gebouwde image en laat de build falen op kritieke
  kwetsbaarheden waarvoor een fix bestaat. Trivy ziet besturingssysteempakketten en jars, maar herkent de
  Java-runtime zelf niet betrouwbaar. Een fout in de JDK zoals Java4Shell komt dus niet vanzelf uit deze scan;
  volg daarvoor de berichten van Adoptium.

## Stappen bij een nieuwe kwetsbaarheid

1. Vaststellen of de wiki het getroffen onderdeel en de getroffen versie gebruikt (`./mvnw dependency:tree`,
   `npm ls` in `frontend/`, `java -version`).
2. Is er een fix: versie verhogen, `./mvnw verify` en de Playwright-suite draaien, deployen.
3. Is er nog geen fix: de noodmaatregel van de leverancier toepassen en hier vastleggen wanneer die weer weg mag.
4. In productie controleren dat de nieuwe versie of maatregel echt actief is.
5. De stand in dit document bijwerken.
