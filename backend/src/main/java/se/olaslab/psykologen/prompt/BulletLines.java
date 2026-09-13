package se.olaslab.psykologen.prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Punktlistor som modellen skrivit, tolkade till rader.
 *
 * <p>Både Eriks tankar och de öppna trådarna kommer tillbaka som en punkt per rad. Tolkningen
 * är förlåtande med bindestreck och blankrader, men lägger inte till något som inte står där.
 */
public final class BulletLines {

    private BulletLines() {
    }

    public static List<String> parse(String raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : raw.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("- ")) {
                lines.add(line.substring(2));
            } else if (!line.isEmpty() && !line.startsWith("-")) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Sant när modellen svarat att listan är tom - "Inga öppna trådar.", "Inga förändringar." */
    public static boolean isEmptyAnswer(String raw) {
        return raw == null || raw.isBlank() || raw.stripLeading().startsWith("Inga");
    }
}
