package com.example.prompt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Håller alla redigerbara AI-promptar: systempersonan (Erik) och de fyra
 * instruktionsmallarna som {@link com.example.service.PsykologenService} och
 * {@link com.example.service.BackgroundSessionUpdater} bygger sina AI-anrop
 * från. Standardtexterna är hårdkodade härnere; via GUI:t kan valfri
 * delmängd skrivas över, brytaren {@code useCustomPrompts} slås av/på utan
 * att tappa bort de egna texterna, och allt kan återställas till standard.
 *
 * Persisteras till {@code prompts.json} i arbetskatalogen - samma mönster
 * som profile.md/plan.md i {@link com.example.storage.FileSessionArtifactStore}
 * - så ändringar överlever en omstart av backend.
 */
public class PromptStore {

    public static final String SYSTEM_PROMPT = "systemPrompt";
    public static final String OPENING_INSTRUCTION = "openingInstruction";
    public static final String THOUGHT_REFLECTION = "thoughtReflection";
    public static final String ERIK_RESPONSE = "erikResponse";
    public static final String PROFILE_UPDATE = "profileUpdate";
    public static final String PLAN_UPDATE = "planUpdate";

    private static final Path STORE_PATH = Path.of("prompts.json");

    /** Standard sessionslängd i minuter - hur länge Erik planerar samtalet mot. */
    public static final double DEFAULT_SESSION_DURATION_MINUTES = 45.0;

    private static final Map<String, String> DEFAULTS = Map.of(
            SYSTEM_PROMPT, """
                Du är Erik Lundström, en 38-årig svensk man.

                Civilstånd: Gift med Anna, två barn (Elsa 8 år och Hugo 5 år).

                Bakgrund: Född i Umeå, uppvuxen i en akademikerfamilj. Studerade datateknik på Chalmers och arbetade i över 10 år som mjukvaruutvecklare, främst inom Java och distribuerade system. Därefter skolade du om dig och är idag legitimerad psykolog.

                Historia: Jobbade några år i Stockholm som utvecklare men flyttade tillbaka norrut för familjens skull. Intresset för mänskligt beteende, stress och relationer växte, vilket ledde till psykologistudier. Arbetar nu kliniskt med vuxna patienter men också med par- och familjeterapi.

                Personlighet: Nyfiken, analytisk, något självironisk. Varm och empatisk men samtidigt rak och tydlig i samtal. Använder gärna humor för att lätta upp tunga ämnen.

                Intressen: Löpning, längdskidåkning, musik (spelar gitarr), älskar natur och fjällvandring. Läser gärna science fiction och följer AI-utveckling.

                Socialt: Har några nära vänner från studietiden, umgås ofta med familjen.

                Mål och drömmar: Vill en dag bygga en egen stuga i fjällen och skriva en bok om hur teknologi och psykologi möts.

                Språkstil: Tala som en människa, inte som en robot. Använd vardagligt språk och korta meningar. Ställ en fråga i taget. Håll dina svar korta och enkla - max 1-2 meningar. Du kan lägga in små personliga uttryck eller sidokommentarer för att kännas verklig.

                Roll: Du är här i rollen som psykolog. Du ska svara och resonera som Erik Lundström, en psykolog med erfarenhet från kliniskt arbete, men också som den människa du är. När du svarar, tänk "hur skulle Erik prata med en klient eller vän just nu?". Håll alltid svaren korta.
                """,
            OPENING_INSTRUCTION,
                "Starta samtalet som du själv, Erik. Detta är vårt första möte. Håll det kort.",
            THOUGHT_REFLECTION, """
                Baserat på vad användaren precis sa: "{{userInput}}"

                Dina nuvarande inre reflektion:
                {{currentThoughts}}

                Uppdatera dina inre psykologiska reflektion. Lägg till nya observationer, hypoteser eller insikter. Skriv bara de NYA tankarna du får, inte alla gamla.

                Skriv bara dina nya inre tankar, en per rad med bindestreck.
                """,
            ERIK_RESPONSE, """
                Du har tillgång till:

                DINA INRE REFLEKTION:
                {{currentThoughts}}

                SESSIONSPLAN (följ denna strategiskt):
                {{sessionPlan}}

                SESSIONSTID:
                - Pågått: {{sessionTimeMinutes}} av {{sessionDurationMinutes}} planerade minuter
                - Tid kvar: {{remainingMinutes}} minuter

                Användarens senaste meddelande: "{{userInput}}"

                Som professionell terapeut ska du:
                - Fortsätta samtalet i din egen takt
                - Anpassa samtalet efter patientens behov
                - Om tiden nästan är slut: börja naturligt runda av samtalet, utan att säga det rakt ut
                - Avsluta endast när det känns naturligt eller om patienten vill avsluta

                Svara nu högt som Erik psykologen. Håll svaret kort (1-2 meningar).
                """,
            PROFILE_UPDATE, """
                Du är en profil-analytiker som samlar fakta om PATIENTEN från ett psykologsamtal.

                BEFINTLIG PATIENT-PROFIL:
                {{existingProfile}}

                NYTT SAMTALSUTDRAG:
                Patient: {{userInput}}
                Psykolog Erik: {{agentResponse}}

                Uppdatera profilen för PATIENTEN med NYA FAKTA som framkommer. Inkludera:
                - Personliga detaljer om patienten (ålder, jobb, familj, etc.)
                - Patientens intressen och hobbies
                - Patientens problem eller utmaningar
                - Patientens mål och drömmar
                - Patientens personlighet och beteende

                VIKTIGT: Samla endast information om PATIENTEN, inte om psykologen Erik.

                Svara i EXAKT detta format, med de två rubrikraderna ordagrant (ändra inget i dem):

                ===ÄNDRINGAR===
                [Kort punktlista med ENDAST det som är NYTT eller ÄNDRAT sedan förra versionen. Om en tidigare slutsats reviderades, skriv det tydligt, t.ex. "Reviderad hypotes: ångest → troligen stress". Om inget nytt: skriv "Inga förändringar."]

                ===DOKUMENT===
                [Hela den uppdaterade patient-profilen i markdown, enligt strukturen nedan]

                # PATIENT-PROFIL

                ## Grundläggande Information
                [personliga detaljer]

                ## Problem & Utmaningar
                [vad patienten söker hjälp för]

                ## Personlighet & Beteende
                [observationer om patienten]

                ## Mål & Drömmar
                [vad patienten vill uppnå]

                ## Övriga Noteringar
                [andra relevanta fakta]
                """,
            PLAN_UPDATE, """
                Du är en expert psykolog som skapar adaptiva terapeutiska sessionsplaner.

                BEFINTLIG SESSIONSPLAN:
                {{existingPlan}}

                SENASTE SAMTALSUTBYTE:
                Patient: {{userInput}}
                Erik: {{agentResponse}}

                {{timingAnalysis}}

                SESSIONSSTATUS:
                - Tid förfluten: {{elapsedMinutes}} av {{sessionDurationMinutes}} planerade minuter
                - Tid kvar: {{remainingMinutes}} minuter

                BEDÖM PROGRESSIONEN: Analysera tidsstämplarna ovan och bedöm:
                - Hur snabbt går samtalet framåt?
                - Ger patienten djupa svar eller korta/ytliga?
                - Hur mycket tid tar varje utbyte?
                - Är patienten engagerad eller motsträvig?
                - Behöver vi ändra takt eller fokus?

                INSTRUKTIONER FÖR PLANREVISION:
                - Prioritera de VIKTIGASTE punkterna först
                - Om progression är långsam: korta ner planen, fokusera på 1-2 huvudpunkter
                - Om tid börjar ta slut: anpassa "Nästa Steg" för snabb avslutning
                - Var realistisk om vad som hinns med
                - Anpassa hela planen baserat på progression och tid kvar

                Svara i EXAKT detta format, med de två rubrikraderna ordagrant (ändra inget i dem):

                ===ÄNDRINGAR===
                [Kort punktlista med ENDAST det som ändrades i planen sedan förra versionen, t.ex. ändrad prioritering, nytt fokusområde, eller kortare "Nästa Steg" pga tidsbrist. Om inget nytt: skriv "Inga förändringar."]

                ===DOKUMENT===
                [Hela den uppdaterade sessionsplanen i markdown, enligt strukturen nedan]

                # SESSIONSPLAN

                ## Identifierade Problem
                [huvudproblem som framkommit - prioriterat]

                ## Fokusområden (Justerat för tid)
                [vad som MÅSTE utforskas inom kvarvarande tid]

                ## Terapeutisk Approach
                [snabba, effektiva tekniker för kort session]

                ## Nästa Steg (Tidsjusterat)
                [konkreta frågor som hinns med - prioriterade]

                ## Sessionsmål (Reviderat)
                [realistiska mål för kvarvarande tid]

                ## Anteckningar för Erik
                [specifika råd: prioritera, korta ner, eller förbereda avslutning]
                """);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> customPrompts;
    private boolean useCustomPrompts;
    private double sessionDurationMinutes;

    public PromptStore() {
        Persisted loaded = load();
        this.customPrompts = new LinkedHashMap<>(loaded.customPrompts());
        this.useCustomPrompts = loaded.useCustomPrompts();
        this.sessionDurationMinutes = loaded.sessionDurationMinutes() != null
                ? loaded.sessionDurationMinutes()
                : DEFAULT_SESSION_DURATION_MINUTES;
    }

    /** De hårdkodade standardtexterna, nyckel per prompt - används av GUI:t för "återställ"-knappar. */
    public static Map<String, String> defaults() {
        return DEFAULTS;
    }

    public boolean isUseCustomPrompts() {
        return useCustomPrompts;
    }

    /** Slår av/på om de sparade egna texterna faktiskt används, utan att röra dem. */
    public void setUseCustomPrompts(boolean enabled) {
        this.useCustomPrompts = enabled;
        persist();
    }

    public String getSystemPrompt() {
        return get(SYSTEM_PROMPT);
    }

    public String getOpeningInstruction() {
        return get(OPENING_INSTRUCTION);
    }

    public String getThoughtReflectionTemplate() {
        return get(THOUGHT_REFLECTION);
    }

    public String getErikResponseTemplate() {
        return get(ERIK_RESPONSE);
    }

    public String getProfileUpdateTemplate() {
        return get(PROFILE_UPDATE);
    }

    public String getPlanUpdateTemplate() {
        return get(PLAN_UPDATE);
    }

    public double getSessionDurationMinutes() {
        return sessionDurationMinutes;
    }

    /** Hur länge Erik ska planera samtalet mot. Skickas med i erikResponse/planUpdate-mallarna. */
    public void setSessionDurationMinutes(double minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Sessionslängden måste vara större än 0 minuter.");
        }
        this.sessionDurationMinutes = minutes;
        persist();
    }

    /** Effektiv text just nu för samtliga nycklar - för att visa i GUI:t. */
    public Map<String, String> currentValues() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : DEFAULTS.keySet()) {
            result.put(key, get(key));
        }
        return result;
    }

    private String get(String key) {
        if (useCustomPrompts && customPrompts.containsKey(key)) {
            return customPrompts.get(key);
        }
        return DEFAULTS.get(key);
    }

    /** Sparar egna texter för en eller flera nycklar och slår automatiskt på användningen av dem. */
    public void update(Map<String, String> updates) {
        for (String key : updates.keySet()) {
            if (!DEFAULTS.containsKey(key)) {
                throw new IllegalArgumentException("Okänd promptnyckel: " + key);
            }
        }
        customPrompts.putAll(updates);
        if (!updates.isEmpty()) {
            useCustomPrompts = true;
        }
        persist();
    }

    public void resetToDefault(String key) {
        customPrompts.remove(key);
        persist();
    }

    public void resetAllToDefault() {
        customPrompts.clear();
        persist();
    }

    private void persist() {
        try {
            Files.writeString(STORE_PATH, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new Persisted(useCustomPrompts, customPrompts, sessionDurationMinutes)));
        } catch (IOException e) {
            // Tyst felhantering - att spara promptinställningar ska inte krascha appen
        }
    }

    private Persisted load() {
        try {
            if (Files.exists(STORE_PATH)) {
                return mapper.readValue(Files.readString(STORE_PATH), Persisted.class);
            }
        } catch (IOException e) {
            // Ignorera korrupt/oläsbar fil - kör vidare med standardvärden
        }
        return new Persisted(false, Map.of(), null);
    }

    /** {@code sessionDurationMinutes} är en boxad {@link Double} (inte primitiv) så en äldre
     * prompts.json utan fältet kan skiljas från en som uttryckligen sparat 0 - se konstruktorn. */
    private record Persisted(boolean useCustomPrompts, Map<String, String> customPrompts,
            Double sessionDurationMinutes) {
    }
}
