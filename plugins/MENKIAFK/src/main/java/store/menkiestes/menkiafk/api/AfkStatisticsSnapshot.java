package store.menkiestes.menkiafk.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable AFK statistics view. Active valid sessions may be reflected in the values,
 * matching MENKIAFK's command/placeholder statistics behavior.
 */
public record AfkStatisticsSnapshot(
        UUID playerId,
        String playerName,
        long todayMillis,
        long weekMillis,
        long totalMillis,
        long longestMillis,
        int sessions,
        int manualSessions,
        int autoSessions,
        long lastAfkAt
) {
    public AfkStatisticsSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        playerName = playerName == null ? "" : playerName;
    }

    public int legacySessions() {
        return Math.max(0, sessions - manualSessions - autoSessions);
    }
}
