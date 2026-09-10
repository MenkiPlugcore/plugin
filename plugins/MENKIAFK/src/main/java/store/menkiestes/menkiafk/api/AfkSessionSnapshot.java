package store.menkiestes.menkiafk.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable view of one active AFK session.
 */
public record AfkSessionSnapshot(
        UUID playerId,
        String reason,
        long startedAt,
        AfkSessionType type
) {
    public AfkSessionSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        reason = reason == null ? "" : reason;
        Objects.requireNonNull(type, "type");
    }

    /**
     * Returns the current session duration using the supplied wall-clock timestamp.
     */
    public long durationMillis(long now) {
        return Math.max(0L, now - startedAt);
    }
}
