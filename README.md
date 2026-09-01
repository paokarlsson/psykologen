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

## Kom igång

Du behöver Java 21, Node.js, Maven och en OpenAI API-nyckel.

**1. Sätt din API-nyckel**
```bash
echo "OPENAI_API_KEY=din_nyckel_här" > .env
```

**2. Starta backend**
```bash
cd backend
mvn spring-boot:run
```
Backend körs nu på `http://localhost:8080`.

**3. Starta frontend**
```bash
cd frontend
npm install
npm start
```
Öppna `http://localhost:4200` i webbläsaren.

## Struktur

```
backend/    Spring Boot API
frontend/   Angular-app
```

## Vad jag lärde mig / övade på

- Bygga en REST-API i Spring Boot och koppla den mot ett externt AI-API
- Angular-komponenter och kommunikation mellan frontend och backend
- Hantera hemligheter (API-nycklar) korrekt via miljövariabler istället
  för att hårdkoda dem

## Status

Fungerande prototyp, men inte klar. Inget felhanteringsflöde eller
säkerhetsgranskning är gjord utöver grunderna – bygg inte vidare på
det här för något som möter riktiga användare utan att se över det
ordentligt först.