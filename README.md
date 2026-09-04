# Psykologen

> ⚠️ **Detta är ett labb- och lärandeprojekt.** Det är inte en riktig
> psykolog, ingen terapitjänst och ska inte användas för faktisk
> vårdrådgivning eller psykisk hälsa. Det är byggt för att lära mig
> fullstack-utveckling (Java/Spring Boot + Angular) och hur man bygger
> mot ett AI-API. Ingenting här är produktionsfärdigt.

## Vad är det här?

Ett litet experiment där en AI (via OpenAI:s API) spelar rollen av en
påhittad person, "Erik", i en chatt. Projektet är i första hand en
övning i att koppla ihop en backend, en frontend och ett externt API –
inte ett försök att bygga något som ska likna verklig terapi.

## Hur det fungerar

```
Angular (frontend)  →  Spring Boot (backend)  →  OpenAI API
```

- **Frontend**: Angular. Visar chatten och skickar meddelanden.
- **Backend**: Spring Boot. Tar emot meddelanden, skickar dem vidare
  till OpenAI, och returnerar svaret.
- **AI:n** styrs av en systemprompt som beskriver vem "Erik" är.

## Inloggning

Appen kräver inloggning. Varje konto får sitt **eget** samtal, sin egen
patientprofil, sin egen sessionsplan och sina egna promptinställningar —
ingen ser någon annans.

Kontona ligger i `backend/.env` som BCrypt-hashar; inga lösenord i
klartext sparas någonstans i projektet.

**1. Skapa en lösenordshash**
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments=--hash-password=ditt_lösenord
```
Kommandot skriver ut de två raderna du behöver och avslutar.

**2. Lägg in kontot i `backend/.env`**
```
APP_AUTH_USERS_0_USERNAME=anna
APP_AUTH_USERS_0_PASSWORDHASH={bcrypt}$2a$10$...
```
Fler konton numreras vidare: `APP_AUTH_USERS_1_USERNAME` och så vidare.
Användarnamnet blir också namnet på användarens lagringskatalog under
`backend/data/users/`, därför tillåts bara 3–32 tecken med gemener,
siffror, `-` och `_`.

Saknas konton vägrar backend starta och berättar vad som behöver göras.

## Kom igång

Du behöver Java 21, Node.js, Maven och en API-nyckel (Anthropic eller OpenAI).

**1. Starta backend**
```bash
cd backend
echo "ANTHROPIC_API_KEY=din_nyckel_här" > .env
# lägg även in minst ett konto enligt "Inloggning" ovan
mvn spring-boot:run
```
`.env` måste ligga i `backend/` — spring-dotenv letar i processens
working directory, inte i repo-roten.
Backend körs nu på `http://localhost:8080`.

**2. Starta frontend**
```bash
cd frontend
npm install
npm start
```
Öppna `http://localhost:4200` i webbläsaren och logga in.

Frontend anropar backend via `/api`, som Angulars dev-server proxar vidare
till port 8080 (se `frontend/proxy.conf.js`). Att allt ligger på samma
origin är inte bara bekvämt: inloggningen bygger på en sessionscookie, och
en cookie som ska följa med cross-origin kräver CORS med `allowCredentials`
— vilket i sin tur är oförenligt med jokertecknet `origins="*"`.

### Alternativ: Docker Compose

Vill du slippa installera Java/Node lokalt: skapa `backend/.env` som i
steg 1 ovan, kör sedan
```bash
docker compose up
```
Backend och frontend körs i varsin container (officiella Maven- och
Node-images, ingen egen Dockerfile) med koden volym-mountad, så
ändringar du gör lokalt speglas direkt in. Port 5005 är öppen för att
koppla på en JVM-debugger mot backend om du vill.

## Struktur

```
backend/    Spring Boot API
frontend/   Angular-app
```

## Om appen ska ut på internet

Den nuvarande uppsättningen är byggd för att köras lokalt. Innan den
exponeras utåt behövs åtminstone:

- **HTTPS.** Utan TLS går lösenord och sessionscookie i klartext över
  nätet, och då är inloggningen ingen inloggning. Terminera TLS i en
  reverse proxy (Caddy, nginx, Traefik) framför backend.
- **`COOKIE_SECURE=true`** i miljön, så sessionscookien bara skickas över
  HTTPS. (Sätt den inte utan TLS — då kan ingen logga in alls.)
- **Servera frontend från samma origin** som backend, t.ex. genom att låta
  reverse proxyn skicka `/api` till backend och allt annat till den byggda
  Angular-appen. Dev-proxyn i `proxy.conf.js` gäller bara `npm start`.
- **Hastighetsbegränsning per IP** i proxyn. Spärren i appen räknar per
  användarnamn, vilket skyddar ett känt konto men inte mot försök spridda
  över många användarnamn.

## Vad jag lärde mig / övade på

- Bygga en REST-API i Spring Boot och koppla den mot ett externt AI-API
- Angular-komponenter och kommunikation mellan frontend och backend
- Hantera hemligheter (API-nycklar) korrekt via miljövariabler istället
  för att hårdkoda dem
- Autentisering med Spring Security: sessionscookie i stället för JWT i
  `localStorage` (cookien är `HttpOnly` och därmed oåtkomlig för XSS),
  CSRF-skydd, BCrypt-hashade lösenord och spärr mot lösenordsgissning
- Att hålla per-användardata isär i en app som från början bara hade ett
  enda globalt tillstånd

## Status

Fungerande prototyp, men inte klar. Inloggning och användarseparation
finns, men någon fullständig säkerhetsgranskning är inte gjord – och
varningen högst upp gäller fortfarande: lägg inte in riktiga
patientuppgifter här.