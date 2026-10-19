# Psykologen

> ⚠️ **Detta är ett labb- och lärandeprojekt.** Det är inte en riktig
> psykolog, ingen terapitjänst och ska inte användas för faktisk
> vårdrådgivning eller psykisk hälsa. Det är byggt för att lära mig
> fullstack-utveckling (Java/Spring Boot + Angular) och hur man bygger
> mot ett AI-API. Ingenting här är produktionsfärdigt.
>
> **Dela inga personuppgifter eller annan känslig information** i appen –
> varken om dig själv eller om någon annan. Det du skriver skickas vidare
> till ett externt AI-API, och appen har varken de rutiner eller det skydd
> som sådana uppgifter kräver.
>
> **Allt kan tas bort utan förvarning.** Konton, samtal, profiler och
> planer kan raderas när som helst, och appen kan stängas av helt utan
> att någon säger till i förväg. Räkna inte med att något du skriver
> finns kvar.

Samma text möter användaren i appen: på inloggningssidan, i chattens
välkomstläge och som en kort påminnelse i chattens rubrik.

## Vad är det här?

Ett litet experiment där en AI (via OpenAI:s API) spelar rollen av en
påhittad person, "Erik", i en chatt. Projektet är i första hand en
övning i att koppla ihop en backend, en frontend och ett externt API –
inte ett försök att bygga något som ska likna verklig terapi.

## Hur det fungerar

```
Angular (frontend)  →  Java på Spola (backend)  →  OpenAI API
```

- **Frontend**: Angular. Visar chatten och skickar meddelanden.
- **Backend**: Java 25 på [Spola](https://github.com/paokarlsson/spola),
  ett ramverk som ersätter Spring Boot med vanlig Java-kod och inga
  beroenden utöver JDK:n. Tar emot meddelanden, skickar dem vidare
  till OpenAI, och returnerar svaret. Tidigare var det Spring Boot;
  API:t och filerna i `backend/data/` är desamma.
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
java Build.java run --hash-password=ditt_lösenord
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

Du behöver Java 25 (JDK), Node.js 24 LTS, npm 12 och en API-nyckel
(Anthropic eller OpenAI). Backend behöver inget byggverktyg: `Build.java`
körs direkt av `java`.

**1. Starta backend**
```bash
cd backend
cp .env.example .env   # fyll i API-nyckel och minst ett konto, se "Inloggning" ovan
java Build.java run
```
`.env` måste ligga i `backend/` — backend läser den från katalogen den
startas i, inte från repo-roten. Testerna körs med `java Build.java`.
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
Backend och frontend körs i varsin container (officiella Temurin- och
Node-images, ingen egen Dockerfile) med koden volym-mountad, så
ändringar du gör lokalt speglas direkt in. Port 5005 är öppen för att
koppla på en JVM-debugger mot backend om du vill.

## Struktur

```
backend/    Java-API på Spola
frontend/   Angular-app
```

## Ute på internet

Appen går att köra skarpt bakom Caddy på en enkel server. Se **[DEPLOY.md](DEPLOY.md)**
för hela gången: DNS, brandvägg, konfiguration och deploy.

```
Internet ──► caddy (80/443, TLS)
               ├── /api/*  ──► backend  (ingen port mot hosten)
               └── /*      ──► frontend (byggd Angular-app)
```

`compose.prod.yaml` sköter det som skiljer prod från lokal körning: TLS via
Caddy, `COOKIE_SECURE=true` så sessionscookien aldrig går i klartext, samma
origin för frontend och `/api`, och en named volume för användardata.
`compose.yaml` är kvar orörd för lokal utveckling.

Images byggs i GitHub Actions vid varje push till `master` och publiceras på
GHCR. Servern hämtar dem färdiga och bygger ingenting själv — Angular-bygget
är för tungt för en liten droplet.

Kvar att göra om appen får mer trafik än en handfull inloggningar:

- **Hastighetsbegränsning per IP** i proxyn. Spärren i appen räknar per
  användarnamn, vilket skyddar ett känt konto men inte mot försök spridda
  över många användarnamn. Caddy kan det inte utan plugin, så det kräver en
  egen Caddy-build — inte gjort.

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
finns, men någon fullständig säkerhetsgranskning är inte gjord – backend
bygger på Spola 0.x, vars säkerhetsmoduler inte är granskade av en
människa – och
varningen högst upp gäller fortfarande: lägg inte in riktiga
patientuppgifter här.