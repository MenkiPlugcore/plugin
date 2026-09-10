package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.storage.JdbcStorageBackend;
import id.cadera.menkiestesparty.storage.StorageBackend;
import id.cadera.menkiestesparty.storage.StorageIntegrity;
import id.cadera.menkiestesparty.storage.YamlStorageBackend;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * v1.5.1 storage facade.
 *
 * Gameplay continues to use in-memory YamlConfiguration documents. Persistence
 * is isolated behind YAML/SQLite/MySQL backends. v1.5.1 adds verified live
 * migration, rollback, health recovery, queue diagnostics and crash markers
 * without changing the public MenkiPartyAPI v1.0 contract.
 */
public final class StorageBundle implements AutoCloseable {
    public static final int STORAGE_SCHEMA_VERSION = 1;

    private static final Set<String> DOCUMENT_KEYS = Set.of(
            "parties", "wars", "season", "hall", "interactions"
    );
    private static final DateTimeFormatter BACKUP_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final JavaPlugin plugin;
    private final Path dataDirectory;
    private final Path settingsFile;
    private final Path stateFile;
    private final Path journalFile;
    private final YamlConfiguration settings;
    private final YamlStorageBackend yamlBackend;
    private final ExecutorService writer;

    private final AtomicReference<Snapshot> pending = new AtomicReference<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);
    private final AtomicBoolean maintenanceScheduled = new AtomicBoolean(false);
    private final AtomicBoolean migrationInProgress = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong asyncSubmissions = new AtomicLong();
    private final AtomicLong coalescedSnapshots = new AtomicLong();
    private final AtomicLong successfulWrites = new AtomicLong();
    private final AtomicLong failedWrites = new AtomicLong();
    private final AtomicLong recoveryAttempts = new AtomicLong();
    private final Object ioLock = new Object();

    private volatile StorageBackend backend;
    private volatile String configuredBackend;
    private volatile String activeBackend;
    private volatile boolean degraded;
    private volatile String lastError = "";
    private volatile long lastSuccessfulWriteAt;
    private volatile long lastPersistedSequence;
    private volatile long oldestPendingAt;
    private volatile long lastQueueDelayMillis;
    private volatile long lastHealthCheckAt;
    private volatile boolean lastHealthHealthy = true;
    private volatile int consecutiveHealthFailures;
    private volatile long lastRecoveryAt;
    private volatile long lastBacklogWarningAt;
    private volatile boolean uncleanShutdownDetected;

    public final YamlConfiguration parties = new YamlConfiguration();
    public final YamlConfiguration wars = new YamlConfiguration();
    public final YamlConfiguration season = new YamlConfiguration();
    public final YamlConfiguration hall = new YamlConfiguration();
    public final YamlConfiguration interactions = new YamlConfiguration();

    public StorageBundle(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataFolder().toPath();
        this.settingsFile = dataDirectory.resolve("storage.yml");
        this.stateFile = dataDirectory.resolve("storage-state.yml");
        this.journalFile = dataDirectory.resolve("storage-migration.yml");
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MENKIESTESParty-Storage");
            thread.setDaemon(true);
            return thread;
        });

        try {
            Files.createDirectories(dataDirectory);
            if (!Files.isRegularFile(settingsFile)) plugin.saveResource("storage.yml", false);
            this.settings = YamlConfiguration.loadConfiguration(settingsFile.toFile());
            mergeStorageDefaults();
            this.yamlBackend = new YamlStorageBackend(dataDirectory);
            this.yamlBackend.initialize();
            this.configuredBackend = normalizeBackend(settings.getString("storage.backend", "YAML"));

            inspectPreviousShutdown();
            initializeBackendAndLoad();
            recoverIncompleteMigrationJournal();
            if (uncleanShutdownDetected
                    && settings.getBoolean("storage.recovery.backup-after-unclean-shutdown", true)) {
                try {
                    Path recovery = createBackup("recovery", snapshotDocuments(), activeBackend);
                    plugin.getLogger().warning("Unclean shutdown detected; recovery snapshot created at "
                            + recovery.toAbsolutePath());
                } catch (Exception failure) {
                    plugin.getLogger().warning("Unable to create unclean-shutdown recovery snapshot: "
                            + safeMessage(failure));
                }
            }
            markRuntimeState(false);
        } catch (Exception failure) {
            writer.shutdownNow();
            throw new IllegalStateException(
                    "Unable to initialize MENKIESTESParty storage: " + safeMessage(failure), failure);
        }
    }

    private void mergeStorageDefaults() throws Exception {
        try (InputStream input = plugin.getResource("storage.yml")) {
            if (input == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            settings.setDefaults(defaults);
            settings.options().copyDefaults(true);
            writeAtomic(settingsFile, settings.saveToString());
        }
    }

    private void inspectPreviousShutdown() {
        if (!Files.isRegularFile(stateFile)) {
            uncleanShutdownDetected = false;
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile.toFile());
        uncleanShutdownDetected = !state.getBoolean("clean-shutdown", true);
    }

    private void markRuntimeState(boolean cleanShutdown) {
        try {
            YamlConfiguration state = Files.isRegularFile(stateFile)
                    ? YamlConfiguration.loadConfiguration(stateFile.toFile())
                    : new YamlConfiguration();
            state.set("schema-version", STORAGE_SCHEMA_VERSION);
            state.set("plugin-version", plugin.getDescription().getVersion());
            state.set("clean-shutdown", cleanShutdown);
            if (cleanShutdown) {
                state.set("last-clean-shutdown-at", System.currentTimeMillis());
            } else {
                state.set("last-startup-at", System.currentTimeMillis());
                state.set("startup-id", UUID.randomUUID().toString());
            }
            state.set("configured-backend", configuredBackend);
            state.set("active-backend", activeBackend == null ? "UNINITIALIZED" : activeBackend);
            writeAtomic(stateFile, state.saveToString());
        } catch (Exception failure) {
            plugin.getLogger().warning("Unable to write storage-state.yml: " + safeMessage(failure));
        }
    }

    private void initializeBackendAndLoad() throws Exception {
        if ("YAML".equals(configuredBackend)) {
            backend = yamlBackend;
            activeBackend = "YAML";
            loadDocuments(yamlBackend.load(DOCUMENT_KEYS));
            lastHealthCheckAt = System.currentTimeMillis();
            lastHealthHealthy = true;
            return;
        }

        StorageBackend requested = createBackend(configuredBackend);
        try {
            requested.initialize();
            Map<String, String> sqlDocuments = new LinkedHashMap<>(requested.load(DOCUMENT_KEYS));
            Map<String, String> yamlDocuments = yamlBackend.load(DOCUMENT_KEYS);
            boolean changed = false;

            if (sqlDocuments.isEmpty()
                    && settings.getBoolean("storage.migration.import-yaml-when-sql-empty", true)
                    && hasMeaningfulData(yamlDocuments)) {
                Path preImport = createBackup("pre-import", yamlDocuments, "YAML");
                sqlDocuments.putAll(yamlDocuments);
                requested.save(sqlDocuments);
                Map<String, String> verified = requested.load(DOCUMENT_KEYS);
                if (!StorageIntegrity.equivalent(sqlDocuments, verified, DOCUMENT_KEYS)) {
                    throw new IllegalStateException("SQL import checksum verification failed");
                }
                changed = true;
                plugin.getLogger().info("Storage migration: imported existing YAML documents into "
                        + configuredBackend + " after snapshot " + preImport.getFileName() + ".");
            } else if (settings.getBoolean("storage.migration.import-missing-yaml-documents", true)) {
                for (String key : DOCUMENT_KEYS) {
                    if (!sqlDocuments.containsKey(key) && yamlDocuments.containsKey(key)) {
                        sqlDocuments.put(key, yamlDocuments.get(key));
                        changed = true;
                    }
                }
                if (changed) {
                    requested.save(sqlDocuments);
                    Map<String, String> verified = requested.load(DOCUMENT_KEYS);
                    if (!StorageIntegrity.equivalent(sqlDocuments, verified, DOCUMENT_KEYS)) {
                        throw new IllegalStateException("SQL missing-document import verification failed");
                    }
                }
            }

            backend = requested;
            activeBackend = configuredBackend;
            loadDocuments(sqlDocuments);
            lastSuccessfulWriteAt = System.currentTimeMillis();
            lastHealthCheckAt = System.currentTimeMillis();
            lastHealthHealthy = true;

            if (settings.getBoolean("storage.mirror-yaml-backup", true)) {
                try {
                    yamlBackend.save(snapshotDocuments());
                } catch (Exception mirrorFailure) {
                    plugin.getLogger().warning("Initial YAML mirror failed: " + safeMessage(mirrorFailure));
                }
            }
        } catch (Exception sqlFailure) {
            try {
                requested.close();
            } catch (Exception ignored) {
            }
            if (!settings.getBoolean("storage.fallback-to-yaml-on-error", true)) throw sqlFailure;

            degraded = true;
            lastError = sqlFailure.getClass().getSimpleName() + ": " + safeMessage(sqlFailure);
            backend = yamlBackend;
            activeBackend = "YAML_FALLBACK";
            loadDocuments(yamlBackend.load(DOCUMENT_KEYS));
            lastHealthHealthy = false;
            lastHealthCheckAt = System.currentTimeMillis();
            plugin.getLogger().severe(configuredBackend
                    + " unavailable; using YAML fallback. Auto-recovery will retry if enabled. Cause: "
                    + lastError);
        }
    }

    private StorageBackend createBackend(String type) {
        String normalized = normalizeBackend(type);
        if ("YAML".equals(normalized)) return yamlBackend;

        String table = settings.getString("storage.sql.table", "menkiestesparty_storage");
        int attempts = Math.max(1, settings.getInt("storage.sql.retry.max-attempts", 2));
        long delay = Math.max(0L, settings.getLong("storage.sql.retry.delay-millis", 250L));

        if ("SQLITE".equals(normalized)) {
            String configuredFile = settings.getString("storage.sqlite.file", "storage.db");
            Path file = new File(configuredFile == null ? "storage.db" : configuredFile).isAbsolute()
                    ? Path.of(configuredFile)
                    : dataDirectory.resolve(configuredFile == null ? "storage.db" : configuredFile);
            String url = "jdbc:sqlite:" + file.toAbsolutePath();
            return new JdbcStorageBackend(
                    "SQLITE", "org.sqlite.JDBC", url, new Properties(), table, attempts, delay);
        }

        String host = settings.getString("storage.mysql.host", "127.0.0.1");
        int port = Math.max(1, Math.min(65535, settings.getInt("storage.mysql.port", 3306)));
        String database = settings.getString("storage.mysql.database", "menkiestesparty");
        String parameters = settings.getString("storage.mysql.parameters",
                "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000&socketTimeout=10000");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        if (parameters != null && !parameters.isBlank()) url += "?" + parameters;
        Properties properties = new Properties();
        properties.setProperty("user", settings.getString("storage.mysql.username", "root"));
        properties.setProperty("password", settings.getString("storage.mysql.password", ""));
        return new JdbcStorageBackend(
                "MYSQL", "com.mysql.cj.jdbc.Driver", url, properties, table, attempts, delay);
    }

    private void loadDocuments(Map<String, String> documents) throws InvalidConfigurationException {
        loadDocument(parties, documents.get("parties"));
        loadDocument(wars, documents.get("wars"));
        loadDocument(season, documents.get("season"));
        loadDocument(hall, documents.get("hall"));
        loadDocument(interactions, documents.get("interactions"));
    }

    private static void loadDocument(YamlConfiguration configuration, String payload)
            throws InvalidConfigurationException {
        if (payload != null && !payload.isBlank()) configuration.loadFromString(payload);
    }

    public void saveAllAsync() {
        Snapshot snapshot = snapshot();
        if (!settings.getBoolean("storage.async-writes", true)) {
            persist(snapshot);
            return;
        }

        asyncSubmissions.incrementAndGet();
        Snapshot replaced = pending.getAndSet(snapshot);
        if (replaced != null) coalescedSnapshots.incrementAndGet();
        if (oldestPendingAt <= 0L) oldestPendingAt = snapshot.createdAt();
        scheduleDrain();
    }

    /** Durable/synchronous save. Used by reward receipts, migrations and shutdown. */
    public boolean saveAllBlocking() {
        Snapshot snapshot = snapshot();
        pending.set(null);
        oldestPendingAt = 0L;
        return persist(snapshot);
    }

    /** Backward-compatible durable save method. */
    public void saveAll() {
        saveAllBlocking();
    }

    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) return;
        writer.execute(() -> {
            try {
                while (true) {
                    Snapshot next = pending.getAndSet(null);
                    if (next == null) break;
                    lastQueueDelayMillis = Math.max(0L, System.currentTimeMillis() - next.createdAt());
                    persist(next);
                    if (pending.get() == null) oldestPendingAt = 0L;
                }
            } finally {
                drainScheduled.set(false);
                if (pending.get() != null) scheduleDrain();
            }
        });
    }

    private boolean persist(Snapshot snapshot) {
        synchronized (ioLock) {
            if (snapshot.sequence() <= lastPersistedSequence) return true;
            try {
                backend.save(snapshot.documents());
                successfulWrites.incrementAndGet();
                lastSuccessfulWriteAt = System.currentTimeMillis();
                lastPersistedSequence = snapshot.sequence();
                lastError = "";

                if (!activeBackend.startsWith("YAML")
                        && settings.getBoolean("storage.mirror-yaml-backup", true)) {
                    try {
                        yamlBackend.save(snapshot.documents());
                    } catch (Exception mirrorFailure) {
                        plugin.getLogger().warning("YAML mirror write failed: " + safeMessage(mirrorFailure));
                    }
                }
                return true;
            } catch (Exception failure) {
                failedWrites.incrementAndGet();
                lastError = failure.getClass().getSimpleName() + ": " + safeMessage(failure);
                plugin.getLogger().severe("Storage write failed on " + activeBackend + ": " + lastError);
                return fallbackAfterWriteFailure(snapshot, failure);
            }
        }
    }

    private boolean fallbackAfterWriteFailure(Snapshot snapshot, Exception original) {
        if (activeBackend.startsWith("YAML")
                || !settings.getBoolean("storage.fallback-to-yaml-on-error", true)) return false;
        try {
            StorageBackend failedBackend = backend;
            yamlBackend.save(snapshot.documents());
            backend = yamlBackend;
            activeBackend = "YAML_FALLBACK";
            degraded = true;
            lastHealthHealthy = false;
            lastSuccessfulWriteAt = System.currentTimeMillis();
            lastPersistedSequence = snapshot.sequence();
            try {
                failedBackend.close();
            } catch (Exception ignored) {
            }
            markRuntimeState(false);
            plugin.getLogger().severe("Storage switched to YAML fallback after runtime SQL failure. "
                    + "v1.5.1 auto-recovery will resync current memory/YAML state when SQL becomes healthy.");
            return true;
        } catch (Exception fallbackFailure) {
            lastError = "primary=" + safeMessage(original) + "; fallback=" + safeMessage(fallbackFailure);
            plugin.getLogger().severe("YAML fallback also failed: " + safeMessage(fallbackFailure));
            return false;
        }
    }

    private Snapshot snapshot() {
        long now = System.currentTimeMillis();
        return new Snapshot(sequence.incrementAndGet(), now, snapshotDocuments());
    }

    private Map<String, String> snapshotDocuments() {
        Map<String, String> documents = new LinkedHashMap<>();
        documents.put("parties", parties.saveToString());
        documents.put("wars", wars.saveToString());
        documents.put("season", season.saveToString());
        documents.put("hall", hall.saveToString());
        documents.put("interactions", interactions.saveToString());
        return Map.copyOf(documents);
    }

    public CompletableFuture<Boolean> verifyAsync() {
        return CompletableFuture.supplyAsync(this::verifyBlocking, writer);
    }

    public boolean verifyBlocking() {
        synchronized (ioLock) {
            lastHealthCheckAt = System.currentTimeMillis();
            lastHealthHealthy = backend.ping();
            if (lastHealthHealthy) consecutiveHealthFailures = 0;
            else consecutiveHealthFailures++;
            return lastHealthHealthy;
        }
    }

    /**
     * Called periodically from the global scheduler. Serialization is captured on
     * the caller thread; all I/O/reconnect work is delegated to the storage worker.
     */
    public void maintenanceTick() {
        long now = System.currentTimeMillis();
        long interval = Math.max(5L, settings.getLong("storage.health-check.interval-seconds", 30L)) * 1000L;
        if (now - lastHealthCheckAt < interval) {
            warnBacklogIfNeeded(now);
            return;
        }
        if (!maintenanceScheduled.compareAndSet(false, true)) return;

        Map<String, String> captured = needsRecoverySnapshot() ? snapshotDocuments() : Map.of();
        writer.execute(() -> {
            try {
                runMaintenance(captured);
            } finally {
                maintenanceScheduled.set(false);
            }
        });
        warnBacklogIfNeeded(now);
    }

    private boolean needsRecoverySnapshot() {
        return !activeBackend.equals("YAML")
                && settings.getBoolean("storage.fallback-to-yaml-on-error", true);
    }

    private void runMaintenance(Map<String, String> captured) {
        synchronized (ioLock) {
            lastHealthCheckAt = System.currentTimeMillis();

            if ("YAML_FALLBACK".equals(activeBackend)
                    && !"YAML".equals(configuredBackend)
                    && settings.getBoolean("storage.health-check.auto-recover-sql", true)) {
                attemptSqlRecovery(captured);
                return;
            }

            boolean healthy = backend.ping();
            lastHealthHealthy = healthy;
            if (healthy) {
                consecutiveHealthFailures = 0;
                return;
            }

            consecutiveHealthFailures++;
            int threshold = Math.max(1,
                    settings.getInt("storage.health-check.failures-before-fallback", 2));
            if (!activeBackend.startsWith("YAML")
                    && consecutiveHealthFailures >= threshold
                    && settings.getBoolean("storage.fallback-to-yaml-on-error", true)) {
                Snapshot snapshot = new Snapshot(
                        sequence.incrementAndGet(),
                        System.currentTimeMillis(),
                        captured.isEmpty() ? snapshotDocuments() : captured);
                fallbackAfterWriteFailure(snapshot,
                        new IllegalStateException("health check failed " + consecutiveHealthFailures + " times"));
            }
        }
    }

    private void attemptSqlRecovery(Map<String, String> captured) {
        recoveryAttempts.incrementAndGet();
        StorageBackend candidate = null;
        try {
            candidate = createBackend(configuredBackend);
            candidate.initialize();
            if (!candidate.ping()) throw new IllegalStateException("SQL recovery ping failed");

            Map<String, String> source = captured.isEmpty() ? snapshotDocuments() : captured;
            candidate.save(source);
            Map<String, String> verified = candidate.load(DOCUMENT_KEYS);
            if (!StorageIntegrity.equivalent(source, verified, DOCUMENT_KEYS)) {
                throw new IllegalStateException("SQL recovery checksum verification failed");
            }

            backend = candidate;
            candidate = null;
            activeBackend = configuredBackend;
            degraded = false;
            lastError = "";
            lastHealthHealthy = true;
            consecutiveHealthFailures = 0;
            lastRecoveryAt = System.currentTimeMillis();
            markRuntimeState(false);
            plugin.getLogger().info("Storage auto-recovery succeeded. Active backend restored to "
                    + activeBackend + " after verified resync.");
        } catch (Exception failure) {
            lastHealthHealthy = false;
            lastError = "recovery: " + safeMessage(failure);
            plugin.getLogger().warning("Storage SQL auto-recovery attempt failed: " + safeMessage(failure));
        } finally {
            if (candidate != null) {
                try {
                    candidate.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void warnBacklogIfNeeded(long now) {
        long threshold = Math.max(1000L,
                settings.getLong("storage.queue.warn-after-millis", 5000L));
        long age = pendingAgeMillis();
        if (age < threshold || now - lastBacklogWarningAt < threshold) return;
        lastBacklogWarningAt = now;
        plugin.getLogger().warning("Storage writer backlog detected: pending-age=" + age
                + "ms, pending=" + pendingWrites()
                + ", coalesced=" + coalescedSnapshots.get() + ".");
    }

    public CompletableFuture<MigrationResult> migrateAsync(String rawTarget, boolean force) {
        String target;
        try {
            target = normalizeBackend(rawTarget);
        } catch (Exception invalid) {
            return CompletableFuture.completedFuture(MigrationResult.failure(
                    activeBackend, rawTarget, safeMessage(invalid), null));
        }

        if (!migrationInProgress.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(MigrationResult.failure(
                    activeBackend, target, "another storage migration is already running", null));
        }

        Snapshot captured = snapshot();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return migrateCaptured(target, force, captured, "MIGRATE");
            } finally {
                migrationInProgress.set(false);
            }
        }, writer);
    }

    public CompletableFuture<MigrationResult> rollbackAsync() {
        if (!migrationInProgress.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(MigrationResult.failure(
                    activeBackend, "UNKNOWN", "another storage migration is already running", null));
        }

        String rollbackTarget = rollbackTarget();
        if (rollbackTarget == null || rollbackTarget.isBlank()) {
            migrationInProgress.set(false);
            return CompletableFuture.completedFuture(MigrationResult.failure(
                    activeBackend, "UNKNOWN", "no successful migration is available to roll back", null));
        }

        Snapshot captured = snapshot();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return migrateCaptured(rollbackTarget, true, captured, "ROLLBACK");
            } finally {
                migrationInProgress.set(false);
            }
        }, writer);
    }

    private MigrationResult migrateCaptured(String target, boolean force, Snapshot captured, String operation) {
        String from = primaryActiveBackend();
        if (from.equals(target) && !activeBackend.equals("YAML_FALLBACK")) {
            return MigrationResult.failure(from, target, "target backend is already active", null);
        }

        StorageBackend targetBackend = null;
        Path backup = null;
        try {
            if (!persist(captured)) {
                throw new IllegalStateException("durable pre-migration flush failed");
            }

            backup = createBackup("pre-migration", captured.documents(), from);
            writeMigrationJournal("IN_PROGRESS", operation, from, target, backup,
                    StorageIntegrity.checksums(captured.documents()), "");

            targetBackend = createBackend(target);
            targetBackend.initialize();

            Map<String, String> existing = targetBackend.load(DOCUMENT_KEYS);
            boolean guardTarget = settings.getBoolean(
                    "storage.migration.require-empty-or-matching-target", true);
            if (guardTarget && !force && hasMeaningfulData(existing)
                    && !StorageIntegrity.equivalent(captured.documents(), existing, DOCUMENT_KEYS)) {
                throw new IllegalStateException(
                        "target backend already contains different data; retry with force only after reviewing backup");
            }

            targetBackend.save(captured.documents());
            Map<String, String> verified = targetBackend.load(DOCUMENT_KEYS);
            if (!StorageIntegrity.equivalent(captured.documents(), verified, DOCUMENT_KEYS)) {
                throw new IllegalStateException("target backend checksum verification failed");
            }

            setConfiguredBackend(target);

            synchronized (ioLock) {
                StorageBackend previous = backend;
                backend = targetBackend;
                targetBackend = null;
                activeBackend = target;
                configuredBackend = target;
                degraded = false;
                lastError = "";
                lastHealthHealthy = true;
                consecutiveHealthFailures = 0;
                lastHealthCheckAt = System.currentTimeMillis();
                lastSuccessfulWriteAt = System.currentTimeMillis();
                if (previous != yamlBackend && previous != backend) {
                    try {
                        previous.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            if (!target.equals("YAML") && settings.getBoolean("storage.mirror-yaml-backup", true)) {
                try {
                    yamlBackend.save(captured.documents());
                } catch (Exception mirrorFailure) {
                    plugin.getLogger().warning("Post-migration YAML mirror failed: " + safeMessage(mirrorFailure));
                }
            }

            try {
                writeMigrationJournal("COMPLETED", operation, from, target, backup,
                        StorageIntegrity.checksums(captured.documents()), "");
            } catch (Exception journalFailure) {
                plugin.getLogger().warning("Migration committed but completion journal update failed: "
                        + safeMessage(journalFailure));
            }
            markRuntimeState(false);
            return MigrationResult.success(from, target, backup);
        } catch (Exception failure) {
            lastError = "migration: " + safeMessage(failure);
            try {
                writeMigrationJournal("FAILED", operation, from, target, backup,
                        StorageIntegrity.checksums(captured.documents()), safeMessage(failure));
            } catch (Exception journalFailure) {
                plugin.getLogger().warning("Unable to update failed migration journal: "
                        + safeMessage(journalFailure));
            }
            return MigrationResult.failure(from, target, safeMessage(failure), backup);
        } finally {
            if (targetBackend != null && targetBackend != yamlBackend) {
                try {
                    targetBackend.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void setConfiguredBackend(String target) throws Exception {
        settings.set("storage.backend", target);
        writeAtomic(settingsFile, settings.saveToString());
        configuredBackend = target;
    }

    private void writeMigrationJournal(String state, String operation, String from, String to,
                                       Path backup, Map<String, String> checksums, String error) throws Exception {
        YamlConfiguration journal = Files.isRegularFile(journalFile)
                ? YamlConfiguration.loadConfiguration(journalFile.toFile())
                : new YamlConfiguration();

        long now = System.currentTimeMillis();
        journal.set("schema-version", STORAGE_SCHEMA_VERSION);
        journal.set("current.state", state);
        journal.set("current.operation", operation);
        journal.set("current.from", from);
        journal.set("current.to", to);
        journal.set("current.updated-at", now);
        if ("IN_PROGRESS".equals(state)) journal.set("current.started-at", now);
        if (backup != null) journal.set("current.backup", backup.toAbsolutePath().toString());
        journal.set("current.error", error == null ? "" : error);
        journal.set("current.checksums", null);
        for (Map.Entry<String, String> entry : checksums.entrySet()) {
            journal.set("current.checksums." + entry.getKey(), entry.getValue());
        }

        if ("COMPLETED".equals(state)) {
            journal.set("last-success.operation", operation);
            journal.set("last-success.from", from);
            journal.set("last-success.to", to);
            journal.set("last-success.completed-at", now);
            if (backup != null) journal.set("last-success.backup", backup.toAbsolutePath().toString());
        }

        writeAtomic(journalFile, journal.saveToString());
    }

    private void recoverIncompleteMigrationJournal() {
        if (!Files.isRegularFile(journalFile)) return;
        try {
            YamlConfiguration journal = YamlConfiguration.loadConfiguration(journalFile.toFile());
            if (!"IN_PROGRESS".equalsIgnoreCase(journal.getString("current.state", ""))) return;

            String from = journal.getString("current.from", "UNKNOWN");
            String to = journal.getString("current.to", "UNKNOWN");
            String recoveredState;
            if (primaryActiveBackend().equalsIgnoreCase(to)) {
                recoveredState = "RECOVERED_TARGET";
            } else {
                recoveredState = "ABORTED_AFTER_CRASH";
            }
            journal.set("current.state", recoveredState);
            journal.set("current.recovered-at", System.currentTimeMillis());
            journal.set("current.recovery-active-backend", activeBackend);
            writeAtomic(journalFile, journal.saveToString());

            plugin.getLogger().warning("Recovered incomplete storage migration journal "
                    + from + " -> " + to + "; state=" + recoveredState
                    + ", active=" + activeBackend + ".");
        } catch (Exception failure) {
            plugin.getLogger().warning("Unable to inspect migration journal: " + safeMessage(failure));
        }
    }

    private String rollbackTarget() {
        if (!Files.isRegularFile(journalFile)) return null;
        YamlConfiguration journal = YamlConfiguration.loadConfiguration(journalFile.toFile());
        String target = journal.getString("last-success.from");
        if (target == null) return null;
        if (target.endsWith("_FALLBACK")) target = "YAML";
        try {
            return normalizeBackend(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    public String migrationState() {
        if (!Files.isRegularFile(journalFile)) return "NONE";
        YamlConfiguration journal = YamlConfiguration.loadConfiguration(journalFile.toFile());
        return journal.getString("current.state", "NONE");
    }

    public String rollbackBackend() {
        String target = rollbackTarget();
        return target == null ? "NONE" : target;
    }

    public Path backupNow() throws Exception {
        return createBackup("manual", snapshotDocuments(), activeBackend);
    }

    private Path createBackup(String reason, Map<String, String> documents, String sourceBackend)
            throws Exception {
        Path backupRoot = dataDirectory.resolve("backups");
        Path backupDirectory = backupRoot.resolve(
                reason + "-" + BACKUP_STAMP.format(LocalDateTime.now()));
        YamlStorageBackend backup = new YamlStorageBackend(backupDirectory);
        backup.initialize();
        backup.save(documents);

        YamlConfiguration manifest = new YamlConfiguration();
        manifest.set("schema-version", STORAGE_SCHEMA_VERSION);
        manifest.set("plugin-version", plugin.getDescription().getVersion());
        manifest.set("created-at", System.currentTimeMillis());
        manifest.set("reason", reason);
        manifest.set("source-backend", sourceBackend);
        Map<String, String> checksums = StorageIntegrity.checksums(documents);
        for (Map.Entry<String, String> entry : checksums.entrySet()) {
            manifest.set("checksums." + entry.getKey(), entry.getValue());
        }
        writeAtomic(backupDirectory.resolve("manifest.yml"), manifest.saveToString());
        pruneBackups();
        return backupDirectory;
    }

    private void pruneBackups() {
        int max = Math.max(1, settings.getInt("storage.backups.max-count", 12));
        Path root = dataDirectory.resolve("backups");
        if (!Files.isDirectory(root)) return;
        try {
            List<Path> directories;
            try (var stream = Files.list(root)) {
                directories = stream.filter(Files::isDirectory)
                        .sorted(Comparator.comparingLong(StorageBundle::modifiedTime).reversed())
                        .toList();
            }
            for (int i = max; i < directories.size(); i++) deleteRecursively(directories.get(i));
        } catch (Exception failure) {
            plugin.getLogger().warning("Backup rotation warning: " + safeMessage(failure));
        }
    }

    private static long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) Files.deleteIfExists(path);
        }
    }

    public String configuredBackend() {
        return configuredBackend;
    }

    public String activeBackend() {
        return activeBackend;
    }

    public String primaryActiveBackend() {
        return "YAML_FALLBACK".equals(activeBackend) ? "YAML" : activeBackend;
    }

    public boolean asyncWrites() {
        return settings.getBoolean("storage.async-writes", true);
    }

    public boolean degraded() {
        return degraded;
    }

    public String lastError() {
        return lastError;
    }

    public long lastSuccessfulWriteAt() {
        return lastSuccessfulWriteAt;
    }

    public int pendingWrites() {
        return (pending.get() == null ? 0 : 1) + (drainScheduled.get() ? 1 : 0);
    }

    public long pendingAgeMillis() {
        long started = oldestPendingAt;
        return started <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - started);
    }

    public long asyncSubmissions() {
        return asyncSubmissions.get();
    }

    public long coalescedSnapshots() {
        return coalescedSnapshots.get();
    }

    public long successfulWrites() {
        return successfulWrites.get();
    }

    public long failedWrites() {
        return failedWrites.get();
    }

    public long lastQueueDelayMillis() {
        return lastQueueDelayMillis;
    }

    public long lastHealthCheckAt() {
        return lastHealthCheckAt;
    }

    public boolean lastHealthHealthy() {
        return lastHealthHealthy;
    }

    public int consecutiveHealthFailures() {
        return consecutiveHealthFailures;
    }

    public long recoveryAttempts() {
        return recoveryAttempts.get();
    }

    public long lastRecoveryAt() {
        return lastRecoveryAt;
    }

    public boolean migrationRunning() {
        return migrationInProgress.get();
    }

    public boolean uncleanShutdownDetected() {
        return uncleanShutdownDetected;
    }

    public int schemaVersion() {
        return STORAGE_SCHEMA_VERSION;
    }

    public String backendDiagnostics() {
        return backend == null ? "uninitialized" : backend.diagnostics();
    }

    public String queueDiagnostics() {
        return "submitted=" + asyncSubmissions()
                + ", coalesced=" + coalescedSnapshots()
                + ", success=" + successfulWrites()
                + ", failed=" + failedWrites()
                + ", pending-age-ms=" + pendingAgeMillis()
                + ", last-queue-delay-ms=" + lastQueueDelayMillis;
    }

    @Override
    public void close() {
        boolean saved = saveAllBlocking();
        writer.shutdown();
        int timeout = Math.max(1, settings.getInt("storage.shutdown-flush-timeout-seconds", 10));
        try {
            if (!writer.awaitTermination(timeout, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
        try {
            if (backend != null && backend != yamlBackend) backend.close();
        } catch (Exception failure) {
            plugin.getLogger().warning("Storage close warning: " + safeMessage(failure));
        }
        if (saved) markRuntimeState(true);
    }

    private static String normalizeBackend(String raw) {
        String normalized = raw == null ? "YAML" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "YAML", "SQLITE", "MYSQL" -> normalized;
            default -> throw new IllegalArgumentException(
                    "storage backend must be YAML, SQLITE or MYSQL (got " + raw + ")");
        };
    }

    private static boolean hasMeaningfulData(Map<String, String> documents) {
        return documents.values().stream().anyMatch(value -> value != null && !value.isBlank());
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static void writeAtomic(Path target, String content) throws Exception {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content == null ? "" : content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Snapshot(long sequence, long createdAt, Map<String, String> documents) {
    }

    public record MigrationResult(
            boolean success,
            String from,
            String to,
            String message,
            Path backup
    ) {
        public static MigrationResult success(String from, String to, Path backup) {
            return new MigrationResult(true, from, to, "verified migration completed", backup);
        }

        public static MigrationResult failure(String from, String to, String message, Path backup) {
            return new MigrationResult(false, from, to, message, backup);
        }
    }
}
