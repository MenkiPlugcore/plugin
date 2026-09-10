package store.menkiestes.menkiafk.api;

import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.UUID;

/**
 * Stable public read-only API for MENKIAFK.
 *
 * Obtain the active implementation with {@link #get()} after MENKIAFK has enabled,
 * or through Bukkit's ServicesManager directly.
 */
public interface MenkiAfkAPI {

    /**
     * Returns the currently registered MENKIAFK API service.
     *
     * @throws IllegalStateException if MENKIAFK is not enabled or its service is unavailable
     */
    static MenkiAfkAPI get() {
        MenkiAfkAPI api = Bukkit.getServicesManager().load(MenkiAfkAPI.class);
        if (api == null) {
            throw new IllegalStateException("MENKIAFK API is not available. Ensure MENKIAFK is enabled first.");
        }
        return api;
    }

    boolean isAfk(UUID playerId);

    Optional<AfkSessionSnapshot> getCurrentSession(UUID playerId);

    AfkStatisticsSnapshot getStatistics(UUID playerId);

    default long getTotalAfkTime(UUID playerId) {
        return getStatistics(playerId).totalMillis();
    }

    int getAfkCount();
}
