package se.olaslab.psykologen.prompt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import se.olaslab.psykologen.context.ContextStrategies;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class PromptStore {

    public static final String SYSTEM_PROMPT = "systemPrompt";
    public static final String OPENING_INSTRUCTION = "openingInstruction";
    public static final String THOUGHT_REFLECTION = "thoughtReflection";
    public static final String INTERVENTION_CHOICE = "interventionChoice";
    public static final String ERIK_RESPONSE = "erikResponse";
    public static final String PROFILE_UPDATE = "profileUpdate";
    public static final String PLAN_UPDATE = "planUpdate";
    public static final String THREADS_UPDATE = "threadsUpdate";

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
                Du är den tysta inre rösten hos psykologen Erik under ett pågående samtal.

                DINA NUVARANDE INRE REFLEKTIONER:
                {{currentThoughts}}

                Patienten sa just: "{{userInput}}"

                Revidera reflektionerna. Du ska inte bara lägga till - listan ska vara ett arbetsredskap, inte ett arkiv:
                - Lägg till nya observationer, hypoteser eller insikter
                - Skriv om hypoteser som fått nytt stöd, så att stödet framgår
                - Stryk det som visat sig fel, redan är besvarat eller inte längre spelar roll
                - Slå ihop tankar som säger samma sak
                - Håll listan kort, som mest ett tiotal punkter. Prioritera det du har nytta av i nästa replik.

                Svara i EXAKT detta format, med de två rubrikraderna ordagrant (ändra inget i dem):

                ===ÄNDRINGAR===
                [Kort punktlista med vad du la till, skrev om eller strök, t.ex. "Struken: hypotesen om sömnbrist - patienten sover fint" eller "Reviderad: allmän stress → troligen konflikten på jobbet". Om inget nytt: skriv "Inga förändringar."]

                ===DOKUMENT===
                [Hela den reviderade tankelistan, en tanke per rad med inledande bindestreck. Inga rubriker, ingen numrering.]
                """,
            INTERVENTION_CHOICE, """
                Du är handledare åt psykologen Erik och väljer vilket grepp han ska använda i sin nästa replik.

                ERIKS INRE REFLEKTIONER:
                {{currentThoughts}}

                SESSIONSPLAN:
                {{sessionPlan}}

                SESSIONSTID: {{elapsedMinutes}} av {{sessionDurationMinutes}} minuter, {{remainingMinutes}} minuter kvar.

                Patienten sa just: "{{userInput}}"

                GREPP ATT VÄLJA MELLAN:
                {{interventionList}}

                Välj det grepp som för samtalet framåt just nu. Tänk på:
                - Vad patienten precis gav dig: ett laddat besked, ett svävande svar, en öppning?
                - Vad Erik gjorde förra repliken - samma grepp två gånger i rad blir en utfrågning
                - Var i sessionen ni är. Är tiden nästan slut ska samtalet rundas av, inte fördjupas

                Svara med ENDAST id:t för det valda greppet, till exempel: spegling
                Ingen förklaring, inga andra ord.
                """,
            ERIK_RESPONSE, """
                Du har tillgång till:

                DINA INRE REFLEKTION:
                {{currentThoughts}}

                PATIENT-PROFIL (vad du hittills vet om patienten):
                {{patientProfile}}

                ÖPPNA TRÅDAR (nämnt men aldrig utvecklat - återkom till en av dem när det passar):
                {{openThreads}}

                SESSIONSPLAN (följ denna strategiskt):
                {{sessionPlan}}

                SESSIONSTID:
                - Pågått: {{sessionTimeMinutes}} av {{sessionDurationMinutes}} planerade minuter
                - Tid kvar: {{remainingMinutes}} minuter

                VALT GREPP FÖR DEN HÄR REPLIKEN:
                {{intervention}}

                Användarens senaste meddelande: "{{userInput}}"

                Som professionell terapeut ska du:
                - Använda det valda greppet ovan - det är formen för just den här repliken
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
                Psykolog Erik (föregående replik): {{previousResponse}}
                Patient (det som just sades): {{userInput}}

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
            THREADS_UPDATE, """
                Du håller reda på lösa trådar i ett psykologsamtal: sådant patienten öppnat men som ingen följt upp.

                En tråd är något patienten nämnt i förbigående och släppt, en känsla som passerade obemött, en person som dök upp utan sammanhang, eller en fråga från Erik som aldrig besvarades.

                BEFINTLIGA ÖPPNA TRÅDAR:
                {{existingThreads}}

                NYTT SAMTALSUTDRAG:
                Psykolog Erik (föregående replik): {{previousResponse}}
                Patient (det som just sades): {{userInput}}

                Uppdatera listan:
                - Lägg till trådar som öppnades i utdraget
                - Stryk trådar som nu är utforskade eller besvarade
                - Stryk trådar som visat sig sakna betydelse
                - Håll listan kort, som mest sex trådar. Prioritera det som verkar bära något.

                Svara i EXAKT detta format, med de två rubrikraderna ordagrant (ändra inget i dem):

                ===ÄNDRINGAR===
                [Kort punktlista med vilka trådar som öppnades och vilka som stängdes, t.ex. "Öppnad: nämnde brodern, bytte genast ämne" eller "Stängd: sömnen - utforskad nu". Om inget nytt: skriv "Inga förändringar."]

                ===DOKUMENT===
                [De öppna trådarna, en per rad med inledande bindestreck. Skriv tråden så att Erik kan återkomma till den: vad som sades och vad som är ofullbordat. Finns inga öppna trådar alls: skriv "Inga öppna trådar."]
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
    private final Path storePath;
    private final Map<String, String> customPrompts;
    private boolean useCustomPrompts;
    private double sessionDurationMinutes;
    private String contextStrategy;

    public PromptStore(Path baseDir) {
        this.storePath = baseDir.resolve("prompts.json");
        Persisted loaded = load();
        this.customPrompts = new LinkedHashMap<>(loaded.customPrompts());
        this.useCustomPrompts = loaded.useCustomPrompts();
        this.sessionDurationMinutes = loaded.sessionDurationMinutes() != null
                ? loaded.sessionDurationMinutes()
                : DEFAULT_SESSION_DURATION_MINUTES;
        this.contextStrategy = ContextStrategies.exists(loaded.contextStrategy())
                ? loaded.contextStrategy()
                : ContextStrategies.DEFAULT_ID;
    }

    public static Map<String, String> defaults() {
        return DEFAULTS;
    }

    public boolean isUseCustomPrompts() {
        return useCustomPrompts;
    }

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

    public String getInterventionChoiceTemplate() {
        return get(INTERVENTION_CHOICE);
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

    public String getThreadsUpdateTemplate() {
        return get(THREADS_UPDATE);
    }

    public double getSessionDurationMinutes() {
        return sessionDurationMinutes;
    }

    public void setSessionDurationMinutes(double minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Sessionslängden måste vara större än 0 minuter.");
        }
        this.sessionDurationMinutes = minutes;
        persist();
    }

    public String getContextStrategy() {
        return contextStrategy;
    }

    public void setContextStrategy(String id) {
        if (!ContextStrategies.exists(id)) {
            throw new IllegalArgumentException("Okänd kontextstrategi: " + id);
        }
        this.contextStrategy = id;
        persist();
    }

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
            Files.writeString(storePath, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new Persisted(useCustomPrompts, customPrompts,
                            sessionDurationMinutes, contextStrategy)));
        } catch (IOException | JacksonException e) {
            // Att spara promptinställningar ska inte krascha appen
        }
    }

    private Persisted load() {
        try {
            if (Files.exists(storePath)) {
                return mapper.readValue(Files.readString(storePath), Persisted.class);
            }
        } catch (IOException | JacksonException e) {
            // Korrupt fil: kör vidare med standardvärden
        }
        return new Persisted(false, Map.of(), null, null);
    }

    // sessionDurationMinutes är boxad så en äldre prompts.json utan fältet kan
    // skiljas från en som uttryckligen sparat 0 - se konstruktorn. contextStrategy
    // saknas på samma sätt i äldre filer och faller då tillbaka på standarden.
    private record Persisted(boolean useCustomPrompts, Map<String, String> customPrompts,
            Double sessionDurationMinutes, String contextStrategy) {
    }
}
