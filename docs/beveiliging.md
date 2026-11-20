# Beveiliging: kwetsbaarheden in Java en afhankelijkheden

Dit document beschrijft waar de wiki van afhangt, hoe nieuwe kwetsbaarheden opvallen en wat je doet als er een
bekend wordt. Het hoort bij spec N-04 en N-08.

## Java4Shell (CVE-2026-114938), november 2026

Een fout in de Java-runtime zelf, niet in een library. De versnelde Base64-decoder van OpenJDK 17.0.6 t/m 27
schrijft buiten zijn buffer. Een aanvaller zonder account kan de JVM laten crashen, geheugen uitlezen of code
uitvoeren. De fout speelt alleen op x86-64-processors met AVX-512 VBMI.

De wiki draait op Java 25 en decodeert Base64 al vóór het inloggen (onder meer het CSRF-token), dus de wiki
geldt als bereikbaar voor deze aanval.

**Stand op 20 november 2026**

| Onderdeel | Stand |
|---|---|
| Gecorrigeerde runtime | Temurin 25.0.5.1+2 (bevestigd door Adoptium) |
| Docker Hub | `eclipse-temurin:25-jre` is nog 25.0.5+8, er bestaat nog geen 25.0.5.1-tag |
| Noodmaatregel | `-XX:-UseBASE64Intrinsics` in `Dockerfile` en `fly.toml` |
| Productie (Fly) | pas beschermd na een deploy; de vlag werkt alleen na een herstart |

**Wat nog moet gebeuren**

1. Deployen naar Fly, zodat de vlag in productie actief is.
2. Controleren in productie: `fly ssh console -C "jcmd 1 VM.flags -all"` toont `-XX:-UseBASE64Intrinsics`.
3. Zodra Docker Hub een 25.0.5.1-tag heeft: opnieuw bouwen en deployen, en nagaan dat `java -version` in de
   container 25.0.5.1 of hoger meldt. De CI-job "Image scan" drukt die versie bij elke run af.
4. Daarna mag de vlag weg uit `Dockerfile` en `fly.toml`, samen met de stap "Base64-intrinsic staat uit" in
   `.github/workflows/ci.yml`.
5. De JDK op ontwikkelmachines bijwerken naar 25.0.5.1 of hoger.

Niet van toepassing op de wiki: aanvallen zoals Log4Shell. De wiki gebruikt logback, geen `log4j-core`, en
bevat geen JNDI-lookups of Java-deserialisatie.

## Waar Java draait

| Plek | Runtime | Hoe bijwerken |
|---|---|---|
| Productie op Fly | `eclipse-temurin:25-jre` uit de `Dockerfile` | opnieuw bouwen en deployen |
| `docker compose up` | dezelfde image | `docker compose build --pull` |
| CI | `actions/setup-java`, Temurin 25 | haalt bij elke run de nieuwste 25 op |
| Ontwikkelmachine | lokaal geïnstalleerde Temurin JDK 25 | handmatig, via adoptium.net |

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
