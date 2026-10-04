# Deploy till en DigitalOcean-droplet

Så här kommer Psykologen ut på internet: två containrar bakom den Caddy som
delar droppen mellan flera appar. Caddy, domänerna och TLS ligger i repot
[drift](https://github.com/paokarlsson/drift), inte här.

```
Internet ──► caddy (repot drift, 80/443, TLS)
               ├── /api/*  ──► psykologen-backend  (Spring Boot, ingen port mot hosten)
               └── /*      ──► psykologen-frontend (byggd Angular-app, statiska filer)
```

Containrarna ansluter till nätverket `edge` och nås där under sina alias.
Ingen av dem publicerar en port. Användardata ligger i en named volume och
överlever både omstart och rebuild.

Droppen bygger ingenting. Varje push till `master` bygger backend- och
frontend-images i GitHub Actions och publicerar dem på GHCR; `deploy.sh` hämtar
dem färdiga. Maven- och Angular-byggena är det enda i den här appen som kräver
en maskin med minne, och de hör inte hemma på en liten droplet.

---

## Del 1: en gång, på en fräsch droplet

### 1. drift först

DNS, brandvägg, Docker och Caddy sätts upp av repot drift: följ dess README
under *Första gången*. Domänen anges där, som `PSYKOLOGEN_DOMAIN` i `drift/.env`.
Det här repot behöver bara nätverket `edge` som drift skapar.

Kör du psykologen med egen Caddy sedan tidigare, se *Flytt till drift* längst ned.

### 2. Hämta koden

```bash
cd /opt
sudo git clone <repo-url> psykologen
sudo chown -R $USER:$USER psykologen
cd psykologen
```

Är repot privat: lägg upp en read-only deploy key på droppen
(`ssh-keygen -t ed25519 -C "droplet"`, lägg till den publika nyckeln under
repots Deploy keys på GitHub) och klona över SSH.

### 3. Inloggning mot GHCR

Behövs bara om paketen är privata. Publika images går att hämta utan
inloggning — och först då slipper du också GitHubs kvoter för privata packages
(500 MB lagring och 1 GB trafik i månaden på gratisplanen). Paketens synlighet
sätts per paket under **Packages → Package settings**, oberoende av repots.

Är de privata: skapa en PAT med enbart scopet `read:packages`
(GitHub → Settings → Developer settings → Personal access tokens) och logga in
på droppen:

```bash
echo <token> | docker login ghcr.io -u paokarlsson --password-stdin
```

Inloggningen sparas i `~/.docker/config.json` och behöver inte göras om.

### 4. Konfiguration

En fil, gitignorerad. Den monteras rakt in i containern och passerar aldrig
Docker Compose, som annars tolkar `$` i filer den läser själv; en BCrypt-hash är
full av `$`. Hashen kan därför klistras in precis som den ser ut.

**`backend/.env`** — API-nyckel och konton:
```bash
cp backend/.env.example backend/.env
nano backend/.env
```

### 5. Skapa en lösenordshash

Låt backend-imagen skriva ut hashen. Ingen Java eller Maven behöver installeras
på droppen.

```bash
docker pull ghcr.io/paokarlsson/psykologen-backend:latest
docker run --rm -e ANTHROPIC_API_KEY=dummy \
  ghcr.io/paokarlsson/psykologen-backend:latest --hash-password='ditt lösenord'
```

Det förutsätter att bygget i Actions har körts minst en gång. Har det inte det
går samma sak att göra på din egen dator med `docker build -t hash ./backend`.

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

Skriptet kontrollerar konfigurationen, hämtar images, startar och väntar tills
backend svarar. Caddy i drift hittar containrarna så fort de är uppe. Öppna
sedan domänen och logga in.

---

## Del 2: varje efterföljande deploy

Pusha till `master` och vänta in bygget under fliken **Actions** — ett par
minuter när cachen är varm. Sen:

```bash
ssh du@droppens-ip
cd /opt/psykologen
./deploy.sh
```

Skriptet gör `git pull`, hämtar de nya imagerna, startar om, väntar in
healthchecken och visar status. Det går att köra hur många gånger som helst —
användardata och certifikat rörs inte.

Har du ändrat i `backend/.env` räcker det också med `./deploy.sh`; `git pull`
hoppar över den eftersom den inte ligger i repot.

Deployar du innan bygget är klart hämtas den föregående imagen, utan att något
klagar. Kolla Actions först.

### Rollback

Varje bygge taggas både `latest` och med sin commit-sha, så en trasig deploy
backas med shan som argument:

```bash
./deploy.sh 9e6ab6a1c2...
```

Då lämnas koden på droppen orörd — bara imagerna byts. Skiljer sig
`compose.prod.yaml` åt mellan versionerna får du checka ut samma sha manuellt. Städjobbet i Actions sparar de tio senaste versionerna, så äldre
taggar än så kan vara borta.

---

## Vardagskommandon

```bash
# Loggar
docker compose -f compose.prod.yaml logs -f backend

# Status och hälsa
docker compose -f compose.prod.yaml ps

# Starta om utan att hämta nytt
docker compose -f compose.prod.yaml restart backend

# Stoppa allt (data och certifikat ligger kvar)
docker compose -f compose.prod.yaml down
```

## Backup

Profiler, planer, historik och promptinställningar ligger i volymen
`psykologen_psykologen-data`. Det är riktigt samtalsinnehåll, och volymen tas
med i driftens nattliga backup (`drift/backup.sh`). För en kopia för hand:

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

**`deploy.sh` kan inte hämta images.** Antingen har bygget i Actions inte gått
klart (eller misslyckats — kolla fliken Actions), eller så är paketen privata
och droppen inte inloggad. Se steg 4. En PAT som gått ut ger samma fel; logga
in igen med en ny.

**Lagringen tar slut.** Privata packages har 500 MB på gratisplanen, delat med
Actions-artefakter. Städjobbet i workflowet sparar de tio senaste versionerna,
men kräver att repot har rollen Admin under paketets **Package settings →
Manage Actions access**. Saknas den hoppas städningen tyst över (den är satt
till `continue-on-error` för att inte fälla ett i övrigt lyckat bygge) och
gamla versioner måste rensas för hand under Packages. Enklast av allt är att
göra paketen publika — då försvinner kvoten.

**Backend blir inte frisk.** `deploy.sh` skriver ut de sista 40 loggraderna.
Appen vägrar medvetet starta vid saknad API-nyckel eller trasiga konton och
säger då vad som fattas.

**Certifikatet hämtas inte, eller sidan svarar 502.** Certifikaten sköts i
drift, se dess README. 502 betyder att Caddy inte hittar containrarna: kolla att
de är uppe (`docker compose -f compose.prod.yaml ps`) och anslutna till `edge`.

**Inloggningen säger fel lösenord fast det är rätt.** Kontrollera hashen i
`backend/.env`: den ska börja med `{bcrypt}$2` och innehålla tre `$`, utan
citattecken runt.

## Flytt till drift

En gång, om psykologen körs med egen Caddy från en äldre version av det här
repot. Sidan är nere någon minut medan Caddy byts.

```bash
# 1. drift på plats men inte startad: README där, Första gången steg 1–4.
# 2. Stoppa den gamla Caddyn så att 80 och 443 blir lediga. Före git pull:
#    den nya compose.prod.yaml känner inte till någon caddy.
cd /opt/psykologen
docker compose -f compose.prod.yaml stop caddy
# 3. Hämta den nya koden först, så att det är nya deploy.sh som körs.
git pull --ff-only
./deploy.sh
# 4. Starta driftens Caddy.
cd /opt/drift && docker compose up -d
```

`deploy.sh` kör `up -d --remove-orphans`, så den gamla Caddy-containern tas bort.
Volymerna `psykologen_caddy-data` och `psykologen_caddy-config` blir kvar och kan
raderas när allt fungerar: driftens Caddy hämtar ett eget certifikat. Den gamla
`.env` i repo-roten (`DOMAIN`, `ACME_EMAIL`) behövs inte längre.
