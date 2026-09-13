package se.olaslab.psykologen.storage;

public record HistoryEntry(String type, long timestamp, double elapsedMinutes, String change) {

    public static final String PROFILE = "profile";
    public static final String PLAN = "plan";
    public static final String THOUGHTS = "thoughts";

    public static HistoryEntry now(String type, double elapsedMinutes, String change) {
        return new HistoryEntry(type, System.currentTimeMillis(), elapsedMinutes, change);
    }
}
