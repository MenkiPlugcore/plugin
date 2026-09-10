package id.cadera.menkiestesparty.architecture;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Immutable revision signal emitted by the v2 architecture layer. */
public record NetworkEnvelope(
        int schemaVersion,
        UUID eventId,
        String nodeId,
        String partyKey,
        long revision,
        String eventType,
        long createdAt
) {
    public static final int CURRENT_SCHEMA = 1;

    public NetworkEnvelope {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1");
        eventId = Objects.requireNonNull(eventId, "eventId");
        nodeId = require(nodeId, "nodeId");
        partyKey = require(partyKey, "partyKey");
        if (revision < 1L) throw new IllegalArgumentException("revision must be >= 1");
        eventType = require(eventType, "eventType").toUpperCase(Locale.ROOT);
        if (createdAt < 0L) throw new IllegalArgumentException("createdAt cannot be negative");
    }

    public static NetworkEnvelope create(String nodeId, String partyKey, long revision,
                                         String eventType, long createdAt) {
        return new NetworkEnvelope(CURRENT_SCHEMA, UUID.randomUUID(), nodeId, partyKey,
                revision, eventType, createdAt);
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " cannot be blank");
        return value.trim();
    }
}
