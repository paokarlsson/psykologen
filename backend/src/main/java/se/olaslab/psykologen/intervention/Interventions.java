package se.olaslab.psykologen.intervention;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Katalogen över grepp Erik kan använda, och matchningen av modellens val.
 *
 * <p>Utan katalogen är metoden helt implicit: modellen gör det den gissar att en terapeut gör,
 * vilket blir samma försiktiga följdfråga tur efter tur. Att först välja ett grepp och sedan
 * formulera repliken är en klassificering följd av en instruktion - båda sådant en liten modell
 * klarar bra - och det märks direkt i variationen.
 *
 * <p>Katalogen ligger i koden, inte bland de redigerbara promptarna: den är beteende som resten
 * av flödet vilar på, medan mallen runt omkring går att skruva på i inställningarna.
 */
public final class Interventions {

    private static final Map<String, Intervention> KATALOG = new LinkedHashMap<>();

    static {
        register(new Intervention("oppen_fraga", "Öppen fråga",
                "Standardvalet. När du behöver mer att gå på och patienten är villig att prata.",
                "Ställ en öppen fråga som bjuder in patienten att utveckla med egna ord. "
                        + "En fråga, inte flera, och inte en ja/nej-fråga."));
        register(new Intervention("spegling", "Spegling",
                "När patienten just sagt något laddat och behöver höra att det landade.",
                "Säg tillbaka det du hör, med dina egna ord och utan att lägga till tolkning. "
                        + "Avsluta med en kort kontrollfråga, eller med ingenting alls."));
        register(new Intervention("sammanfattning", "Sammanfattning",
                "När flera trådar hopat sig, eller samtalet tappat riktning.",
                "Knyt ihop det viktigaste som sagts hittills i två-tre meningar och fråga om "
                        + "du uppfattat det rätt."));
        register(new Intervention("konkretisering", "Konkretisering",
                "När patienten svarar allmänt eller abstrakt: \"det är jobbigt\", \"alltid\".",
                "Be om en specifik situation. När hände det senast, vad hände då, vad gjorde du? "
                        + "Håll dig till en konkret händelse."));
        register(new Intervention("skalfraga", "Skalfråga",
                "När något behöver mätas eller jämföras över tid - oro, ork, motivation.",
                "Be patienten sätta en siffra mellan 0 och 10 på det ni pratar om, och följ upp "
                        + "med varför det inte är en siffra lägre."));
        register(new Intervention("undantag", "Undantag",
                "När problemet beskrivs som konstant och patienten fastnat i det.",
                "Fråga efter tillfällen då problemet inte fanns, var mindre, eller gick att "
                        + "hantera. Vad var annorlunda då?"));
        register(new Intervention("normalisering", "Normalisering",
                "När patienten skäms för sin reaktion eller kallar sig konstig.",
                "Bekräfta att reaktionen är begriplig utan att avfärda den. Ingen pekpinne och "
                        + "ingen tröst som stänger ämnet."));
        register(new Intervention("utmaning", "Varsam utmaning",
                "När det finns en tydlig motsägelse och bärande förtroende. Använd sparsamt.",
                "Peka varsamt på motsägelsen mellan det patienten säger nu och något som sagts "
                        + "tidigare. Fråga, påstå inte."));
        register(new Intervention("utrymme", "Ge utrymme",
                "När patienten är mitt i något och en fråga bara skulle avbryta.",
                "Säg mycket lite. En kort kvittering som lämnar plats åt patienten att fortsätta "
                        + "själv. Högst en mening."));
        register(new Intervention("avrundning", "Avrundning",
                "När tiden nästan är slut, eller patienten själv börjar avsluta.",
                "Börja knyta ihop sessionen: vad ni landat i, vad som får bli nästa gång. "
                        + "Utan att säga rakt ut att tiden är slut."));
    }

    /** Valet när modellen inte pekar ut något grepp - den försiktigaste av dem. */
    public static final Intervention DEFAULT = KATALOG.get("oppen_fraga");

    private Interventions() {
    }

    private static void register(Intervention intervention) {
        KATALOG.put(intervention.id(), intervention);
    }

    public static List<Intervention> all() {
        return List.copyOf(KATALOG.values());
    }

    /** Katalogen som punktlista, underlaget modellen väljer ur. */
    public static String asPromptList() {
        StringBuilder lista = new StringBuilder();
        for (Intervention intervention : KATALOG.values()) {
            lista.append("- ").append(intervention.id()).append(" (").append(intervention.label())
                    .append("): ").append(intervention.whenToUse()).append("\n");
        }
        return lista.toString().stripTrailing();
    }

    /**
     * Plockar ut greppet ur modellens svar.
     *
     * <p>Svaret ska vara ett ensamt id, men en liten modell packar gärna in det i en mening.
     * Därför letas det första id:t som förekommer i texten, och hittas inget alls faller valet
     * tillbaka på {@link #DEFAULT} - ett tveksamt val ska inte kosta en tur.
     */
    public static Intervention match(String rawChoice) {
        if (rawChoice == null || rawChoice.isBlank()) {
            return DEFAULT;
        }
        String normaliserat = rawChoice.toLowerCase();

        Intervention traff = null;
        int tidigast = Integer.MAX_VALUE;
        for (Intervention intervention : KATALOG.values()) {
            int index = normaliserat.indexOf(intervention.id());
            if (index >= 0 && index < tidigast) {
                tidigast = index;
                traff = intervention;
            }
        }
        return traff != null ? traff : DEFAULT;
    }
}
