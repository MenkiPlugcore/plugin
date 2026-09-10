package store.menkiestes.menkiafk.stats;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import store.menkiestes.menkiafk.MenkiAfkPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class StatsManager {
    public enum Metric {
        TOTAL("total", "Total AFK"),
        TODAY("today", "Hari Ini"),
        WEEK("week", "Minggu Ini"),
        LONGEST("longest", "Sesi Terlama"),
        SESSIONS("sessions", "Jumlah Sesi");

        private final String key;
        private final String displayName;

        Metric(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String key() {
            return key;
        }

        public String displayName() {
            return displayName;
        }

        public static Optional<Metric> parse(String input) {
            if (input == null) return Optional.empty();
            String normalized = input.toLowerCase(Locale.ROOT);
            for (Metric metric : values()) {
                if (metric.key.equals(normalized)) return Optional.of(metric);
            }
            return Optional.empty();
        }
    }

    public record Snapshot(
            UUID uuid,
            String name,
            long todayMillis,
            long weekMillis,
            long totalMillis,
            long longestMillis,
            int sessions
    ) {
        public long value(Metric metric) {
            return switch (metric) {
                case TOTAL -> totalMillis;
                case TODAY -> todayMillis;
                case WEEK -> weekMillis;
                case LONGEST -> longestMillis;
                case SESSIONS -> sessions;
            };
        }
    }

    public record RankedEntry(int rank, Snapshot stats) {
    }

    private static final class StoredStats {
        private String name;
        private long totalMillis;
        private long longestMillis;
        private int sessions;
        private final Map<LocalDate, Long> dailyMillis = new HashMap<>();

        private StoredStats(String name) {
            this.name = name;
        }

        private StoredStats copy() {
            StoredStats copy = new StoredStats(name);
            copy.totalMillis = totalMillis;
            copy.longestMillis = longestMillis;
            copy.sessions = sessions;
            copy.dailyMillis.putAll(dailyMillis);
            return copy;
        }
    }

    private final MenkiAfkPlugin plugin;
    private final File statsFile;
    private final Map<UUID, StoredStats> entries = new HashMap<>();
    private final Map<UUID, Long> activeStartedAt = new HashMap<>();

    private ZoneId zoneId = ZoneId.systemDefault();
    private int keepDailyDays = 35;
    private boolean dirty;

    public StatsManager(MenkiAfkPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Tidak dapat membuat folder data MENKIAFK.");
        }
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        reloadSettings();
        load();
    }

    public synchronized void reloadSettings() {
        String configured = plugin.getConfig().getString("stats.timezone", "system");
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("system")) {
            zoneId = ZoneId.systemDefault();
        } else {
            try {
                zoneId = ZoneId.of(configured.trim());
            } catch (Exception exception) {
                zoneId = ZoneId.systemDefault();
                plugin.getLogger().warning("Timezone stats tidak valid: " + configured + ". Menggunakan timezone sistem: " + zoneId);
            }
        }

        keepDailyDays = Math.max(14, plugin.getConfig().getInt("stats.keep-daily-days", 35));
        pruneOldDays();
    }

    public synchronized void registerPlayer(Player player) {
        UUID id = player.getUniqueId();
        StoredStats stats = entries.computeIfAbsent(id, ignored -> new StoredStats(player.getName()));
        if (!player.getName().equals(stats.name)) {
            stats.name = player.getName();
            dirty = true;
        }
    }

    public synchronized void startSession(Player player, long startedAt) {
        registerPlayer(player);
        activeStartedAt.putIfAbsent(player.getUniqueId(), startedAt);
        dirty = true;
    }

    public synchronized void finishSession(UUID id, long endedAt) {
        Long startedAt = activeStartedAt.remove(id);
        if (startedAt == null) return;

        StoredStats stats = entries.computeIfAbsent(id, ignored -> new StoredStats(shortUuid(id)));
        applySession(stats, startedAt, endedAt);
        dirty = true;
    }

    public synchronized void finishAllSessions(long endedAt) {
        for (UUID id : new ArrayList<>(activeStartedAt.keySet())) {
            finishSession(id, endedAt);
        }
    }

    public synchronized Snapshot snapshot(UUID id) {
        StoredStats base = entries.get(id);
        StoredStats view = base == null ? new StoredStats(shortUuid(id)) : base.copy();

        Long activeStart = activeStartedAt.get(id);
        if (activeStart != null) {
            applySession(view, activeStart, System.currentTimeMillis());
        }
        return snapshotOf(id, view);
    }

    public synchronized Optional<UUID> findUuidByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        for (Map.Entry<UUID, StoredStats> entry : entries.entrySet()) {
            if (entry.getValue().name.equalsIgnoreCase(name)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public synchronized boolean hasPlayer(UUID id) {
        return entries.containsKey(id);
    }

    public synchronized int trackedPlayerCount() {
        return entries.size();
    }

    public synchronized List<RankedEntry> leaderboard(Metric metric) {
        Set<UUID> ids = new HashSet<>(entries.keySet());
        ids.addAll(activeStartedAt.keySet());

        List<Snapshot> snapshots = new ArrayList<>();
        for (UUID id : ids) {
            Snapshot snapshot = snapshot(id);
            if (snapshot.sessions() > 0) snapshots.add(snapshot);
        }

        snapshots.sort(Comparator
                .comparingLong((Snapshot snapshot) -> snapshot.value(metric)).reversed()
                .thenComparing(Snapshot::name, String.CASE_INSENSITIVE_ORDER));

        List<RankedEntry> ranked = new ArrayList<>(snapshots.size());
        for (int index = 0; index < snapshots.size(); index++) {
            ranked.add(new RankedEntry(index + 1, snapshots.get(index)));
        }
        return ranked;
    }

    public synchronized int rank(UUID id, Metric metric) {
        for (RankedEntry entry : leaderboard(metric)) {
            if (entry.stats().uuid().equals(id)) return entry.rank();
        }
        return 0;
    }

    public synchronized void reset(UUID id, long resetAt) {
        StoredStats current = entries.get(id);
        String name = current == null ? shortUuid(id) : current.name;
        entries.put(id, new StoredStats(name));
        if (activeStartedAt.containsKey(id)) {
            activeStartedAt.put(id, resetAt);
        }
        dirty = true;
    }

    public synchronized void saveIfNeeded() {
        if (!dirty && activeStartedAt.isEmpty()) return;
        saveNow();
    }

    public synchronized void saveNow() {
        pruneOldDays();
        long now = System.currentTimeMillis();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("timezone", zoneId.getId());

        Set<UUID> ids = new HashSet<>(entries.keySet());
        ids.addAll(activeStartedAt.keySet());

        for (UUID id : ids) {
            StoredStats base = entries.get(id);
            StoredStats persisted = base == null ? new StoredStats(shortUuid(id)) : base.copy();
            Long activeStart = activeStartedAt.get(id);
            if (activeStart != null) {
                // Checkpoint active sessions without mutating RAM. If the server crashes,
                // the latest autosave still preserves AFK time up to this checkpoint.
                applySession(persisted, activeStart, now);
            }

            String root = "players." + id;
            yaml.set(root + ".name", persisted.name);
            yaml.set(root + ".total-millis", persisted.totalMillis);
            yaml.set(root + ".sessions", persisted.sessions);
            yaml.set(root + ".longest-millis", persisted.longestMillis);
            for (Map.Entry<LocalDate, Long> day : persisted.dailyMillis.entrySet()) {
                yaml.set(root + ".daily." + day.getKey(), day.getValue());
            }
        }

        try {
            yaml.save(statsFile);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Gagal menyimpan stats.yml MENKIAFK.", exception);
        }
    }

    private void load() {
        if (!statsFile.isFile()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(statsFile);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;

        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Mengabaikan UUID stats tidak valid: " + key);
                continue;
            }

            String root = "players." + key;
            StoredStats stats = new StoredStats(yaml.getString(root + ".name", shortUuid(id)));
            stats.totalMillis = Math.max(0L, yaml.getLong(root + ".total-millis", 0L));
            stats.sessions = Math.max(0, yaml.getInt(root + ".sessions", 0));
            stats.longestMillis = Math.max(0L, yaml.getLong(root + ".longest-millis", 0L));

            ConfigurationSection daily = yaml.getConfigurationSection(root + ".daily");
            if (daily != null) {
                for (String dateKey : daily.getKeys(false)) {
                    try {
                        LocalDate date = LocalDate.parse(dateKey);
                        long millis = Math.max(0L, daily.getLong(dateKey, 0L));
                        if (millis > 0L) stats.dailyMillis.put(date, millis);
                    } catch (Exception ignored) {
                        plugin.getLogger().warning("Mengabaikan tanggal stats tidak valid untuk " + stats.name + ": " + dateKey);
                    }
                }
            }
            entries.put(id, stats);
        }
        pruneOldDays();
        dirty = false;
    }

    private Snapshot snapshotOf(UUID id, StoredStats stats) {
        LocalDate today = LocalDate.now(zoneId);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        long todayMillis = stats.dailyMillis.getOrDefault(today, 0L);
        long weekMillis = 0L;
        for (Map.Entry<LocalDate, Long> entry : stats.dailyMillis.entrySet()) {
            LocalDate date = entry.getKey();
            if (!date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                weekMillis += entry.getValue();
            }
        }

        return new Snapshot(
                id,
                stats.name == null || stats.name.isBlank() ? shortUuid(id) : stats.name,
                todayMillis,
                weekMillis,
                stats.totalMillis,
                stats.longestMillis,
                stats.sessions
        );
    }

    private void applySession(StoredStats stats, long startedAt, long endedAt) {
        long safeEnd = Math.max(startedAt, endedAt);
        long duration = safeEnd - startedAt;
        stats.totalMillis += duration;
        stats.longestMillis = Math.max(stats.longestMillis, duration);
        stats.sessions++;
        addDaily(stats, startedAt, safeEnd);
    }

    private void addDaily(StoredStats stats, long startedAt, long endedAt) {
        long cursor = startedAt;
        while (cursor < endedAt) {
            ZonedDateTime current = Instant.ofEpochMilli(cursor).atZone(zoneId);
            LocalDate date = current.toLocalDate();
            long nextDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
            if (nextDay <= cursor) nextDay = cursor + 86_400_000L;
            long chunkEnd = Math.min(endedAt, nextDay);
            stats.dailyMillis.merge(date, chunkEnd - cursor, Long::sum);
            cursor = chunkEnd;
        }
    }

    private void pruneOldDays() {
        LocalDate cutoff = LocalDate.now(zoneId).minusDays(keepDailyDays - 1L);
        for (StoredStats stats : entries.values()) {
            boolean removed = stats.dailyMillis.keySet().removeIf(date -> date.isBefore(cutoff));
            if (removed) dirty = true;
        }
    }

    private static String shortUuid(UUID id) {
        String raw = id.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }
}
