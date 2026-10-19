package se.olaslab.psykologen.storage;

import static spola.json.codec.JsonCodec.field;
import static spola.json.codec.JsonCodecs.DOUBLE;
import static spola.json.codec.JsonCodecs.LONG;
import static spola.json.codec.JsonCodecs.STRING;
import static spola.json.codec.JsonCodecs.nullable;

import spola.json.codec.JsonCodec;

public record HistoryEntry(String type, long timestamp, double elapsedMinutes, String change) {

    public static final String PROFILE = "profile";
    public static final String PLAN = "plan";
    public static final String THOUGHTS = "thoughts";
    public static final String THREADS = "threads";

    public static final JsonCodec<HistoryEntry> CODEC = JsonCodec.object(HistoryEntry::new,
            field("type", STRING, HistoryEntry::type),
            field("timestamp", LONG, HistoryEntry::timestamp),
            field("elapsedMinutes", DOUBLE, HistoryEntry::elapsedMinutes),
            field("change", nullable(STRING), HistoryEntry::change));

    public static HistoryEntry now(String type, double elapsedMinutes, String change) {
        return new HistoryEntry(type, System.currentTimeMillis(), elapsedMinutes, change);
    }
}
