# Psykologen Backend

Spring Boot-backend för Psykologen-applikationen.

## Förutsättningar

- Java 21
- Maven

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

Skapa en lösenordshash:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--hash-password=ditt_lösenord
```

## Starta backend

```bash
mvn spring-boot:run
```

## Köra testerna

```bash
mvn test
```
Testerna täcker att API:t kräver inloggning och att två användare inte
kommer åt varandras profil.

Alternativt:
```bash
mvn clean install
java -jar target/psykologen-1.0-SNAPSHOT.jar
```

Backend startar på port 8080 (default).

## Bygga för produktion

```bash
mvn clean package
```

JAR-filen skapas i `target/` mappen.

## Teknologier

- Spring Boot 3.5.6
- Spring Security (inloggning, CSRF)
- Java 21
- Maven
- Gson för JSON-hantering