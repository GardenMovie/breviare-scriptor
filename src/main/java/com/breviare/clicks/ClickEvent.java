package com.breviare.clicks;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One redirect, captured on the request thread. Carries the click time so the batched insert records
 * when the click happened, not when the consumer got around to writing it.
 */
public record ClickEvent(Type type, UUID targetId, Instant clickedAt,
                         String referrer, String userAgent, String ipHash) {

    public enum Type { LINK, VANITY }

    // Bounds Redis memory per event; Postgres columns are unbounded TEXT, but nobody needs 100 KB of UA.
    private static final int MAX_REFERRER = 2048;
    private static final int MAX_USER_AGENT = 1024;

    Map<String, String> toFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("t", type == Type.LINK ? "L" : "V");
        fields.put("i", targetId.toString());
        fields.put("ts", Long.toString(clickedAt.toEpochMilli()));
        if (referrer != null) fields.put("r", cap(referrer, MAX_REFERRER));
        if (userAgent != null) fields.put("u", cap(userAgent, MAX_USER_AGENT));
        if (ipHash != null) fields.put("h", ipHash);
        return fields;
    }

    /** Throws on malformed input; the flusher treats that as a poison message and drops it. */
    static ClickEvent fromFields(Map<?, ?> fields) {
        String t = (String) fields.get("t");
        Type type = switch (t) {
            case "L" -> Type.LINK;
            case "V" -> Type.VANITY;
            default -> throw new IllegalArgumentException("unknown click type: " + t);
        };
        return new ClickEvent(
                type,
                UUID.fromString((String) fields.get("i")),
                Instant.ofEpochMilli(Long.parseLong((String) fields.get("ts"))),
                (String) fields.get("r"),
                (String) fields.get("u"),
                (String) fields.get("h"));
    }

    private static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
