package store.menkiestes.menkiafk.api.internal;

import store.menkiestes.menkiafk.afk.AfkManager;
import store.menkiestes.menkiafk.afk.AfkSession;
import store.menkiestes.menkiafk.afk.AfkType;
import store.menkiestes.menkiafk.api.AfkSessionSnapshot;
import store.menkiestes.menkiafk.api.AfkSessionType;
import store.menkiestes.menkiafk.api.AfkStatisticsSnapshot;
import store.menkiestes.menkiafk.api.MenkiAfkAPI;
import store.menkiestes.menkiafk.stats.StatsManager;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MenkiAfkApiImpl implements MenkiAfkAPI {
    private final AfkManager afkManager;
    private final StatsManager statsManager;

    public MenkiAfkApiImpl(AfkManager afkManager, StatsManager statsManager) {
        this.afkManager = Objects.requireNonNull(afkManager, "afkManager");
        this.statsManager = Objects.requireNonNull(statsManager, "statsManager");
    }

    @Override
    public boolean isAfk(UUID playerId) {
        return afkManager.isAfk(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public Optional<AfkSessionSnapshot> getCurrentSession(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        AfkSession session = afkManager.getSession(playerId);
        return session == null ? Optional.empty() : Optional.of(toPublicSession(playerId, session));
    }

    @Override
    public AfkStatisticsSnapshot getStatistics(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        StatsManager.Snapshot stats = statsManager.snapshot(playerId);
        return new AfkStatisticsSnapshot(
                stats.uuid(), stats.name(), stats.todayMillis(), stats.weekMillis(), stats.totalMillis(),
                stats.longestMillis(), stats.sessions(), stats.manualSessions(), stats.autoSessions(), stats.lastAfkAt());
    }

    @Override
    public int getAfkCount() {
        return afkManager.afkCount();
    }

    public static AfkSessionSnapshot toPublicSession(UUID playerId, AfkSession session) {
        AfkSessionType type = session.type() == AfkType.AUTO ? AfkSessionType.AUTO : AfkSessionType.MANUAL;
        return new AfkSessionSnapshot(playerId, session.reason(), session.startedAt(), type);
    }
}
