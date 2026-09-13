package se.olaslab.psykologen.intervention;

/**
 * Ett terapeutiskt grepp Erik kan använda i en replik.
 *
 * @param id          nyckeln modellen svarar med när den väljer
 * @param label       namnet som visas för människor
 * @param whenToUse   kort beskrivning av när greppet passar - underlaget för valet
 * @param instruction instruktionen som följer med in i svarsanropet när greppet valts
 */
public record Intervention(String id, String label, String whenToUse, String instruction) {
}
