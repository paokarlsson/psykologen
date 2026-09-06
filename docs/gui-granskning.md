# Psykologen — GUI-granskning

En genomgång av gränssnittet i `frontend/` med fokus på användarvänlighet, WCAG 2.1 (nivå AA), intuitiv navigering och best practice. Baserad på en fullständig kodgenomgång av samtliga komponenter, mallar och stilmallar.

- **Datum:** 6 september 2026
- **Omfattning:** Angular-appen i `frontend/`
- **Metod:** Statisk kodgranskning
- **Standard:** WCAG 2.1, nivå AA

**Fynd per allvarlighetsgrad:** 6 kritiska · 4 höga · 4 medel · 2 låga

En interaktiv version av rapporten (med kontrastprover och sorterbar tabell) finns publicerad här: https://claude.ai/code/artifact/1899817e-54dc-4adf-9e71-6efa9971a803

---

## Sammanfattning

Grundfunktionaliteten fungerar och koden är lättläst, men gränssnittet saknar de mest grundläggande tillgänglighetsstöden och ett sammanhållet designsystem. Ingen av bristerna kräver en omskrivning — de flesta är lokala, konkreta fixar.

- **Sidspråket är fel** — `lang="en"` på en helt svensk app gör att skärmläsare uttalar allt innehåll med fel röst.
- **Ingen tangentbords- eller skärmläsarvänlig struktur** — inget skip-länk, inga landmärken (`main`/`nav`/`header`), och dynamiska meddelanden är helt tysta.
- **Färgkontrasten brister** på flera ställen där vit text läggs på ljusa pastellfärger — headerrubriker, knappar och fokusringar hamnar under WCAG:s minimikrav.
- **Ikonknappar saknar namn** — uppdateringsknapparna i Profil och Plan är bara en emoji, utan `aria-label`.
- **Inget designsystem** — den globala stilmallen är tom, så färger, knappar och felmeddelanden är återuppfunna och dupplicerade i varje komponent.
- Ingenting av detta är strukturellt svårt att åtgärda — se den prioriterade listan längst ner.

## Om appen

Enligt projektets egen README är detta ett labb- och lärandeprojekt — en fiktiv AI-persona ("Erik") som spelar terapeut i ett chattgränssnitt, byggt för att öva Angular och backend-integration. Det är alltså inte en publik bokningssajt, vilket sätter ramarna för granskningen: det finns ingen bokningskalender, inget kontaktformulär och ingen sidfot att bedöma — appen är en enda vy.

| | |
|---|---|
| **Typ av app** | Inloggat AI-chattgränssnitt (ej publik webbplats) |
| **Vy-struktur** | En enda vy med tillståndsväxling: Laddar → Inloggning → Chatt-dashboard. Routern är konfigurerad men `app.routes.ts` är tom. |
| **Huvudkomponenter** | `Chat`, `Profile`, `Plan`, plus flytande widgets `History` och `Settings` |
| **UI-ramverk** | Inget — handskriven CSS per komponent, ingen delad `styles.css` |

## Navigering & informationsarkitektur

Det finns inget renodlat nav-mönster i appen — bara två flytande knappar för Historik och Inställningar i headern. Det är rimligt för en enda-vy-app, men själva strukturen runt omkring saknar de landmärken som gör sidan navigerbar för tangentbord och skärmläsare.

### 🔴 Kritisk — Ingen skip-länk och inga semantiska landmärken (WCAG 2.4.1)
Hela appen är byggd av generiska `<div>`-element (`app-container`, `app-header`, `app-content`) — ingen `<header>`, `<main>`, `<nav>` eller `<section>` finns i `app.html`. Det finns heller ingen skip-länk. En tangentbordsanvändare måste tabba genom historik- och inställningsknapparna på varje sidladdning innan de når chatten.

> **Åtgärd:** lägg till en skip-länk längst upp, och byt ut de viktigaste `<div>`-omslagen mot `<header>`/`<main>`/`<nav>` i `app.html`.

### 🟠 Hög — Historik- och inställningspanelerna saknar programmatisk koppling (WCAG 4.1.2)
Bägge togglingsknapparna (`history.html`, `settings.html`) använder korrekt `[attr.aria-expanded]="isOpen"` — bra! Men ingen har `aria-controls` som pekar på panelens id, och själva panelen saknar `role="region"` med en tillgänglig titel. Relationen knapp ↔ panel är alltså bara visuell.

> **Åtgärd:** koppla ihop med `aria-controls="history-panel"`/`id="history-panel"` och ge panelen `role="region" aria-label="Historik"`.

### 🟠 Hög — Ingen fokushantering vid öppna/stäng av paneler (Best practice)
Varken `history.ts` eller `settings.ts` flyttar fokus in i panelen när den öppnas, eller tillbaka till togglingsknappen när den stängs. Det finns heller ingen Escape-hantering. En tangentbords- eller skärmläsaranvändare kan tappa bort var i sidan fokus befinner sig, och panelen kan bli kvar öppen medan man navigerar vidare i övrigt innehåll.

> **Åtgärd:** flytta fokus till panelens första fokuserbara element vid öppning, lyssna på `Escape` för att stänga, och återställ fokus till knappen vid stängning.

## Tillgänglighet (WCAG 2.1, nivå AA)

Sex av granskningens allvarligaste fynd hör hemma här. Flera av dem är enkla enrads-fixar med stor effekt.

### 🔴 Kritisk — Fel sidspråk deklarerat (WCAG 3.1.1)
`frontend/src/index.html` anger `<html lang="en">`, men allt synligt innehåll är svenska — "Laddar…", "Logga in", "Användarnamn", "Starta Samtal" och så vidare. Skärmläsare kommer läsa upp texten med engelskt uttal.

> **Åtgärd:** ändra till `lang="sv"`.

### 🔴 Kritisk — Ikonknappar helt utan tillgängligt namn (WCAG 4.1.2)
Uppdateringsknapparna i `plan.html` och `profile.html` innehåller bara en emoji (🔄/⏳) och ingen text eller `aria-label`:

```html
<button class="refresh-btn" (click)="refreshPlan()"><span>🔄</span></button>
```

> **Åtgärd:** lägg till `aria-label="Uppdatera plan"` respektive `"Uppdatera profil"`.

### 🔴 Kritisk — Inga aria-live-regioner för dynamiskt innehåll (WCAG 4.1.3)
Felbannrar i `chat.html`, `history.html` och `settings.html` saknar `role`/`aria-live` — bara inloggningsformuläret har `role="alert"`. Profil- och plan-panelerna uppdateras dessutom automatiskt var 5:e sekund via `pollingResource`, helt tyst för skärmläsare.

> **Åtgärd:** ge felmeddelanden `role="alert"`, och statusmeddelanden/auto-uppdateringar en diskret `aria-live="polite"`-region.

### 🔴 Kritisk — Fokusindikatorn tas bort utan tillräcklig ersättning (WCAG 2.4.11)
Både `login.css` och `chat.css` nollställer webbläsarens fokusring: `input:focus { outline: none; border-color: #e891a6; }`. Ersättningsfärgen har en kontrast på bara **~2,33:1** mot vit bakgrund — under kravet på 3:1 för en fokusindikator.

> **Åtgärd:** använd `:focus-visible` med en synlig ring (t.ex. `outline` + `outline-offset`) i en färg som klarar 3:1, istället för att bara byta kantfärg.

### 🔴 Kritisk — Otillräcklig färgkontrast i flera headers och texter (WCAG 1.4.3 / 1.4.11)
Vit text på ljusa pastellgradienter används genomgående för rubriker:

| Plats | Färger | Kontrast | Krav |
|---|---|---|---|
| `app.css` — h1 | vit text på `#f8b4c4` | 1,70:1 | 4,5:1 (normal text) |
| `plan.css` — h2 | vit text på `#fd7e14` | 2,57:1 | 3:1 (stor/fet text) |
| `chat.css` — tidsstämpel | `#999` på vitt | 2,85:1 | 4,5:1 |
| `login.css` — `:focus`-ram | `#e891a6` på vitt | 2,33:1 | 3:1 (UI-komponent) |

> **Åtgärd:** mörkna basfärgerna (eller använd mörk text på ljus botten) tills normal text når 4,5:1 och stor/fet text 3:1.

### 🟠 Hög — Formulärfält utan kopplad etikett (WCAG 1.3.1 / 3.3.2)
I `settings.html` har `<label class="duration-label">` inget `for`-attribut och sessionslängd-fältet inget `id` — kopplingen är bara visuell. Prompt-textarean har ingen etikett alls, bara en fetstilt rubrik bredvid. Chattens textarea har enbart en placeholder, som försvinner så fort man börjar skriva.

> **Åtgärd:** koppla `for`/`id` på sessionslängd-fältet, och lägg till `aria-label` på prompt-textarean och chattens textarea.

## Användarvänlighet & UX-mönster

Inget av detta är fel i teknisk mening, men det gör upplevelsen mindre sammanhållen och förutsägbar.

### 🟡 Medel — Tre olika sätt att visa "laddar"
Chatten använder animerade "typing dots", Profil/Plan byter emoji (🔄→⏳), och Historik/Inställningar visar bara texten "Laddar...". Ingen gemensam spinner- eller skelettkomponent finns, vilket gör att appen känns som flera olika verktyg ihopklistrade.

> **Åtgärd:** inför en delad laddningsindikator-komponent och använd den överallt.

### 🟡 Medel — Sessionsåterställning bekräftas med webbläsarens native `confirm()`
`settings.ts` använder `confirm()` för "🔄 Starta om session" — fungerar tekniskt, men går inte att styla, kan inte matcha appens språk och ton fullt ut, och stannar inte kvar i samma fokushanteringsflöde som resten av appen.

> **Åtgärd:** ersätt med en egen bekräftelsedialog som återanvänder appens formspråk.

### 🟡 Medel — Mobilanpassning finns bara på ett ställe
Enda filen med `@media`-frågor i hela projektet är `app.css` (brytpunkter 1024px och 768px, desktop-först). Chatten, inloggningskortet och de flytande Historik/Inställningar-panelerna saknar helt egna mobilanpassningar.

> **Åtgärd:** lägg till komponentspecifika brytpunkter, särskilt för de bredd-fixerade panelerna (`min(420px, 90vw)` täcker inte alla skärmar bra).

### 🟢 Låg — Felmeddelanden riktade till utvecklare, inte användare
Inloggningens felmeddelande vid nätverksfel lyder *"Ingen kontakt med servern. Är backend igång?"* — begripligt för en utvecklare, men förvirrande för en faktisk användare av appen.

> **Åtgärd:** skriv om till t.ex. "Kunde inte nå tjänsten just nu. Försök igen om en liten stund."

## Kodkvalitet med UX-påverkan

Dessa punkter är inte tillgänglighetsbrister i sig, men de är orsaken till att flera av bristerna ovan sprids: utan ett delat designsystem uppfinns knappar, banners och färger på nytt i varje komponent.

### 🟡 Medel — Tom global stilmall, allt dupplicerat lokalt
`frontend/src/styles.css` är helt tom. Resultatet: `.refresh-btn` är ordagrant dupplicerad mellan `plan.css` och `profile.css`, `.banner`/`.banner-error` är separat definierad i både `settings.css` och `history.css`, och `login.css` har sin egen tredje variant kallad `.error`. Sex-plus knappklasser gör samma jobb med små, oavsiktliga skillnader.

> **Åtgärd:** flytta färger till CSS-variabler i `styles.css`, och gör en delad knapp- och bannerkomponent.

### 🟢 Låg — Kvarlämnad Angular-CLI-titel
`<title>PsykologenGui</title>` i `index.html` är scaffold-standardvärdet, aldrig uppdaterat. Ingen `<meta name="description">` finns heller.

> **Åtgärd:** sätt en beskrivande titel, t.ex. "Psykologen — digitalt terapisamtal".

## Prioriterad åtgärdslista

| Nivå | Fynd | Fil | Rekommendation | Ref. |
|---|---|---|---|---|
| 🔴 Kritisk | Fel sidspråk (`lang="en"` på svenskt innehåll) | `index.html` | Byt till `lang="sv"` | 3.1.1 |
| 🔴 Kritisk | Ingen skip-länk eller semantiska landmärken | `app.html` | Lägg till skip-länk + `header`/`main`/`nav` | 2.4.1 |
| 🔴 Kritisk | Otillräcklig kontrast i headers/text | `app.css`, `plan.css`, `chat.css` | Mörkna basfärger tills 4,5:1/3:1 nås | 1.4.3 |
| 🔴 Kritisk | Fokusindikator borttagen utan ersättning | `login.css`, `chat.css` | Synlig `:focus-visible`, ≥3:1 | 2.4.11 |
| 🔴 Kritisk | Ikonknappar utan tillgängligt namn | `plan.html`, `profile.html` | Lägg till `aria-label` | 4.1.2 |
| 🔴 Kritisk | Inga aria-live-regioner för fel/statusar | `chat.html`, `settings.html`, `history.html` | `role="alert"` / `aria-live="polite"` | 4.1.3 |
| 🟠 Hög | Formulärfält utan kopplad etikett | `settings.html`, `chat.html` | `for`/`id`, `aria-label` | 1.3.1 |
| 🟠 Hög | Panel-knappar saknar `aria-controls`/region-roll | `history.html`, `settings.html` | Koppla knapp↔panel, `role="region"` | 4.1.2 |
| 🟠 Hög | Ingen fokushantering / Escape på flytande paneler | `history.ts`, `settings.ts` | Fokusflytt in/ut, Escape stänger | Best practice |
| 🟠 Hög | Diskret text/inputkant under kontrastkrav | flera `.css` | Mörkare gråton, ≥3:1 kantfärg | 1.4.11 |
| 🟡 Medel | Native `confirm()` för destruktiv åtgärd | `settings.ts` | Egen tillgänglig bekräftelsedialog | Best practice |
| 🟡 Medel | Tre inkonsekventa laddningsmönster | chat/profile/plan/history | Gemensam laddningskomponent | UX |
| 🟡 Medel | Dupplicerad banner-/knapp-CSS | `settings.css`, `history.css`, `plan.css`, `profile.css` | Dela via `styles.css` + tokens | Kodkvalitet |
| 🟡 Medel | Mobilanpassning finns bara i en fil | `chat.css`, `login.css`, `settings.css`, `history.css` | Egna brytpunkter per komponent | UX |
| 🟢 Låg | Utvecklarorienterad felkopia | `login.ts` | Skriv om till användarspråk | UX-text |
| 🟢 Låg | Kvarlämnad CLI-titel, ingen meta description | `index.html` | Beskrivande `<title>`/meta | Best practice |

## Källor & granskade filer

Samtliga fynd är grundade i en fullständig läsning av dessa filer i `frontend/src/app/`:

- `index.html`
- `app.html` / `app.css` / `app.ts`
- `app.routes.ts`
- `components/login/*`
- `components/chat/*`
- `components/profile/*`
- `components/plan/*`
- `components/history/*`
- `components/settings/*`
- `shared/polling-resource.ts`
- `services/api.service.ts`
- `services/auth.service.ts`
