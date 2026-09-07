# Deploy till en DigitalOcean-droplet

Så här kommer Psykologen ut på internet: tre containrar bakom Caddy, som äger
port 80 och 443 och löser TLS automatiskt.

```
Internet ──► caddy (80/443, TLS)
               ├── /api/*  ──► backend  (Spring Boot, ingen port mot hosten)
               └── /*      ──► frontend (byggd Angular-app, statiska filer)
```

Backend nås bara över det interna docker-nätverket. Användardata och Caddys
certifikat ligger i named volumes och överlever både omstart och rebuild.

---

## Del 1: en gång, på en fräsch droplet

### 1. DNS

Peka domänen på droppens IP **innan** du startar något. Let's Encrypt verifierar
mot domänen, och utan fungerande DNS får du inget certifikat.

| Typ | Namn | Värde |
| --- | --- | --- |
| A | `@` (eller subdomänen) | droppens IPv4 |

Kontrollera att det slagit igenom:
```bash
dig +short din-doman.se
```

### 2. Brandvägg

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

Öppna ingenting mer. Varken 8080 eller 4200 ska vara nåbara utifrån — i den
här uppsättningen publicerar backend ingen port mot hosten över huvud taget.

### 3. Hämta koden

```bash
cd /opt
sudo git clone <repo-url> psykologen
sudo chown -R $USER:$USER psykologen
cd psykologen
```

Är repot privat: lägg upp en read-only deploy key på droppen
(`ssh-keygen -t ed25519 -C "droplet"`, lägg till den publika nyckeln under
repots Deploy keys på GitHub) och klona över SSH.

### 4. Konfiguration

Två filer, båda gitignorerade. Uppdelningen finns av ett skäl: Docker Compose
tolkar `$` i filer den läser själv, och en BCrypt-hash är full av `$`.
`backend/.env` monteras rakt in i containern och passerar aldrig Compose, så
hashen kan klistras in precis som den ser ut.

**`.env` i repo-roten** — det Caddy behöver:
```bash
cp .env.example .env
nano .env
```
```
DOMAIN=din-doman.se
ACME_EMAIL=du@example.com
```

**`backend/.env`** — API-nyckel och konton:
```bash
cp backend/.env.example backend/.env
nano backend/.env
```

### 5. Skapa en lösenordshash

Bygg backend-imagen och låt den skriva ut hashen. Ingen Java eller Maven
behöver installeras på droppen.

```bash
docker build -t psykologen-hash ./backend
docker run --rm -e ANTHROPIC_API_KEY=dummy psykologen-hash --hash-password='ditt lösenord'
```

(`ANTHROPIC_API_KEY=dummy` behövs bara för att appen ska starta så långt att
den hinner skriva ut hashen — den används inte till något.)

Utskriften ser ut så här:
```
APP_AUTH_USERS_0_USERNAME=ditt_användarnamn
APP_AUTH_USERS_0_PASSWORDHASH={bcrypt}$2a$10$zsJ/Q.w6...
```

Klistra in båda raderna i `backend/.env`, byt användarnamnet mot det du vill
ha, och **sätt inga citattecken runt hashen** — dotenv-läsaren strippar dem
inte, och en hash med citattecken ger "fel användarnamn eller lösenord" utan
någon annan ledtråd. Fler konton numreras vidare: `APP_AUTH_USERS_1_...`.

Lösenordet i klartext lagras aldrig någonstans.

### 6. Första starten

```bash
./deploy.sh
```

Skriptet kontrollerar konfigurationen, bygger, startar och väntar tills backend
svarar. Certifikatet hämtas automatiskt vid första anropet — det tar några
sekunder. Följ förloppet med:

```bash
docker compose -f compose.prod.yaml logs -f caddy
```

Öppna sedan `https://din-doman.se` och logga in.

---

## Del 2: varje efterföljande deploy

```bash
ssh du@droppens-ip
cd /opt/psykologen
./deploy.sh
```

Det är hela flödet. Skriptet gör `git pull`, bygger om det som ändrats, startar
om, väntar in healthchecken och visar status. Det går att köra hur många gånger
som helst — användardata och certifikat rörs inte.

Har du ändrat i `.env` eller `backend/.env` räcker det också med `./deploy.sh`;
`git pull` hoppar över dem eftersom de inte ligger i repot.

---

## Vardagskommandon

```bash
# Loggar
docker compose -f compose.prod.yaml logs -f backend
docker compose -f compose.prod.yaml logs -f caddy

# Status och hälsa
docker compose -f compose.prod.yaml ps

# Starta om utan att bygga
docker compose -f compose.prod.yaml restart backend

# Stoppa allt (data och certifikat ligger kvar)
docker compose -f compose.prod.yaml down
```

## Backup

Profiler, planer, historik och promptinställningar ligger i volymen
`psykologen_psykologen-data`. Det är riktigt samtalsinnehåll — ta en kopia då
och då:

```bash
docker run --rm \
  -v psykologen_psykologen-data:/data:ro \
  -v "$PWD":/backup \
  alpine tar czf /backup/psykologen-data-$(date +%F).tar.gz -C /data .
```

Återställ:
```bash
docker compose -f compose.prod.yaml down
docker run --rm \
  -v psykologen_psykologen-data:/data \
  -v "$PWD":/backup \
  alpine sh -c "rm -rf /data/* && tar xzf /backup/psykologen-data-2026-01-01.tar.gz -C /data"
./deploy.sh
```

---

## Om något går fel

**`deploy.sh` klagar på konfigurationen.** Felmeddelandet säger vilken rad i
vilken fil. Inget har startats om vid det laget.

**Backend blir inte frisk.** `deploy.sh` skriver ut de sista 40 loggraderna.
Appen vägrar medvetet starta vid saknad API-nyckel eller trasiga konton och
säger då vad som fattas.

**Certifikatet hämtas inte.** Kolla i `logs caddy`. Vanligaste orsakerna är att
DNS inte pekar rätt än, eller att port 80 är stängd — Let's Encrypt behöver den
för verifieringen, även om sidan sedan kör på 443.

**Inloggningen säger fel lösenord fast det är rätt.** Kontrollera hashen i
`backend/.env`: den ska börja med `{bcrypt}$2` och innehålla tre `$`, utan
citattecken runt.

**Let's Encrypt börjar neka.** Certifikaten ligger i volymen `caddy-data` och
återanvänds mellan deploys, så det ska inte hända. Radera aldrig den volymen
för att felsöka något annat — det finns en gräns på fem certifikat per domän
och vecka.
