# Psykologen Backend

Backend för Psykologen-applikationen, byggd på [Spola](https://github.com/paokarlsson/spola) 0.2.0:
ett Java 25-ramverk som ersätter Spring och Lombok med vanlig Java-kod. Inga beroenden utöver
JDK:n och Spola-modulerna i `lib/`.

## Förutsättningar

- Java 25 (en JDK, eftersom bygget kompilerar)

Inget byggverktyg behövs. `Build.java` körs direkt av `java`.

## Konfiguration

`backend/.env` (gitignorerad) håller både AI-nyckeln och kontona:

| Variabel | Betydelse |
| --- | --- |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` | API-nyckel för vald leverantör |
| `AI_PROVIDER` | `anthropic` (default) eller `openai` |
| `APP_AUTH_USERS_<n>_USERNAME` | Användarnamn, 3–32 tecken: `a-z`, `0-9`, `-`, `_` |
| `APP_AUTH_USERS_<n>_PASSWORDHASH` | BCrypt-hash, med prefixet `{bcrypt}` |
| `APP_STORAGE_BASE_DIR` | Var användarnas data hamnar (default `data/users`) |
| `COOKIE_SECURE` | `true` när appen ligger bakom HTTPS |
| `TRUSTED_PROXIES` | Valfri. Proxyer vars `X-Forwarded-*` appen litar på, t.ex. `172.30.0.10`. Utan den litar appen på de privata näten, som Spring Boot gjorde |

`.env` läses från katalogen appen startas i. Miljövariabler med samma namn vinner över filen,
och argument (`--AI_PROVIDER=openai`) vinner över båda. Filen läses som en properties-fil:
citattecken runt ett värde blir en del av värdet, och `export` framför en rad fungerar inte.

Skapa en lösenordshash:
```bash
java Build.java run --hash-password=ditt_lösenord
```
Hashar som Spring skrev (`{bcrypt}$2a$10$...`) fungerar som de är.

## Starta backend

```bash
java Build.java run
```
Backend startar på port 8080.

## Köra testerna

```bash
java Build.java
```
Kompilerar appen och kör alla tester med `spola.test`. Testerna täcker bland annat att API:t
kräver inloggning och CSRF-huvudet, att två användare inte kommer åt varandras profil och
trace, att JSON-svaren har de fält frontenden läser, AI-klienterna mot låtsasservrar (också
omförsöken vid 529) och att filer som Spring-versionen skrev går att läsa.

## Bygga för produktion

Dockerfilen kompilerar med `java Build.java compile` och kör appen på modulvägen:
```bash
java --module-path lib:out/main --module psykologen/se.olaslab.psykologen.PsykologenApplication
```

## Struktur

```
Build.java                    bygget: compile, test (standard) och run
lib/                          Spola 0.2.0 från releasen, en jar per modul, och SHA256SUMS
src/main/java/module-info.java vilka Spola-moduler appen använder
src/main/java/se/olaslab/psykologen/
  PsykologenApplication.java  main: konfigurationen och all wiring, i vanlig Java
  auth/                       inloggning, sessioner, CSRF och lösenordshashar
  controller/                 /api/psykologen
  spolabehov/retry/           omförsök mot AI-API:erna, tills Spola har en motsvarighet
src/test/java/                tester; AllTests kör alla
```

## Spola-jarfilerna

Jar-filerna i `lib/` är hämtade från Spolas
[release v0.2.0](https://github.com/paokarlsson/spola/releases/tag/v0.2.0), och `lib/SHA256SUMS`
är releasens kontrollsummor för dem. De ligger i repot så att bygget fungerar utan nätverk och en
uppgradering syns i diffen. Kontrollera dem med:
```bash
cd lib && sha256sum -c SHA256SUMS
```

En ny version hämtas så här (byt `v`), och sedan körs `java Build.java`:
```bash
v=0.3.0; base=https://github.com/paokarlsson/spola/releases/download/v$v
modules="http json validation web config util security security.web client test"
cd lib && rm -f spola.*.jar SHA256SUMS
curl -fsSL "$base/SHA256SUMS" -o release.sums
for m in $modules; do
  curl -fsSLO "$base/spola.$m-$v.jar"
  grep " spola.$m-$v.jar\$" release.sums >> SHA256SUMS
done
rm release.sums && sha256sum -c SHA256SUMS
```
Uppdatera också versionen i `CLAUDE.md`. Behöver appen något som Spring hade gett den men som
Spola saknar, se `CLAUDE.md`.
