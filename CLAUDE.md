# Psykologen

Angular-frontend i `frontend/`, Java-backend i `backend/`. Hur appen körs lokalt och i
produktion står i `README.md`, `backend/README.md` och `DEPLOY.md`.

- Backend byggs och testas med `cd backend && java Build.java` (Java 25, inget byggverktyg).
- Frontend och backend delar origin: inloggningen är en sessionscookie och CSRF-skyddet bygger
  på cookien `XSRF-TOKEN` som Angular skickar tillbaka i `X-XSRF-TOKEN`.

## Spola

Projektet bygger på [Spola](https://github.com/paokarlsson/spola) 0.2.0, ett
Java 25-ramverk som ersätter Spring och Lombok med explicit kod och bara JDK:n.

- Läs [`framework/MONSTER.md`](https://github.com/paokarlsson/spola/blob/main/framework/MONSTER.md)
  i Spola innan du skriver infrastruktur. Där står vilka moduler som finns, hur ett Spola-API ser
  ut och vad som hör hemma i Spola.
- Spola-modulerna ligger som jar-filer från Spolas release i `backend/lib/`, med releasens
  kontrollsummor i `backend/lib/SHA256SUMS`, och används på modulvägen
  (`requires spola.web;` i `backend/src/main/java/module-info.java`). Hur de byts står i
  `backend/README.md`.
- Lägg inte till Spring, Lombok eller andra bibliotek för sådant som Spring eller Lombok
  hade gett projektet. Använd Spola, eller beställ det (se nedan).
- Inga annoteringar utöver JDK:ns, ingen reflektion för wiring och ingen classpath-scanning.
  Koppla ihop allt i vanlig Java-kod (`PsykologenApplication`).

### När något saknas i Spola
Om projektet behöver något som Spring eller Lombok har men Spola saknar:

1. Skriv ett behov enligt `uppdrag/BEHOV-MALL.md` i Spola och spara det som
   `<sökväg till Spola>/uppdrag/behov/<ÅÅÅÅ-MM-DD>-<namn>.md`. Finns Spola inte lokalt: skapa
   ett GitHub-issue i `paokarlsson/spola` med samma innehåll och etiketten `behov`.
2. Det viktigaste i behovet är användningsfallet, alltså koden du vill kunna skriva.
3. Bygg under tiden en minimal lösning i projektet, i Spolas stil, i ett eget paket
   (`se.olaslab.psykologen.spolabehov.<namn>`). Den ska vara lätt att byta ut.
4. När Spola har släppt modulen: uppdatera jar-filerna i `backend/lib/`, byt ut den lokala
   lösningen och ta bort paketet.

Det som bara gäller det här projektet, till exempel affärsregler och klienter för ett visst
företags API, hör hemma i projektet och beställs inte.

Väntar på Spola just nu: omförsök (Spring Retry), lokalt i `spolabehov/retry`.
