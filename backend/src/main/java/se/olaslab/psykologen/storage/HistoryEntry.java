package se.olaslab.psykologen.storage;

public record HistoryEntry(String type, long timestamp, double elapsedMinutes, String change) {

    public static final String PROFILE = "profile";
    public static final String PLAN = "plan";
}
