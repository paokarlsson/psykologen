package com.example.prompt;

/**
 * Parsar profil-/plan-uppdateringssvar som består av två delar: en kort
 * ändringslogg och hela det uppdaterade dokumentet, separerade av de fasta
 * rubrikraderna i {@link PromptStore#defaults()} (nycklarna
 * {@value PromptStore#PROFILE_UPDATE}/{@value PromptStore#PLAN_UPDATE}).
 *
 * Om AI-svaret inte följer formatet - t.ex. efter en egen redigerad prompt
 * utan rubrikerna - behålls hela svaret som dokumentet och {@code changelog}
 * blir {@code null}. Att logga historik ska aldrig krascha eller förvränga
 * själva dokumentet.
 */
public record ChangelogResponse(String changelog, String document) {

    private static final String CHANGELOG_MARKER = "===ÄNDRINGAR===";
    private static final String DOCUMENT_MARKER = "===DOKUMENT===";

    public static ChangelogResponse parse(String raw) {
        int changelogIndex = raw.indexOf(CHANGELOG_MARKER);
        int documentIndex = raw.indexOf(DOCUMENT_MARKER);
        if (changelogIndex < 0 || documentIndex < 0 || documentIndex < changelogIndex) {
            return new ChangelogResponse(null, raw.trim());
        }
        String changelog = raw.substring(changelogIndex + CHANGELOG_MARKER.length(), documentIndex).trim();
        String document = raw.substring(documentIndex + DOCUMENT_MARKER.length()).trim();
        return new ChangelogResponse(changelog, document);
    }

    /** True om det inte finns något meningsfullt att logga (tomt, eller AI:t skrev "Inga förändringar"). */
    public boolean hasNoChange() {
        return changelog == null || changelog.isBlank()
                || changelog.strip().toLowerCase().startsWith("inga förändringar");
    }
}
