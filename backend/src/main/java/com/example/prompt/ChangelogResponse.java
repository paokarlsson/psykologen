package com.example.prompt;

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

    public boolean hasNoChange() {
        return changelog == null || changelog.isBlank()
                || changelog.strip().toLowerCase().startsWith("inga förändringar");
    }
}
