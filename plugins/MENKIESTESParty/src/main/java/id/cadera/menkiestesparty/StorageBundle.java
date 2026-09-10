package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.storage.JdbcStorageBackend;
import id.cadera.menkiestesparty.storage.StorageBackend;
import id.cadera.menkiestesparty.storage.YamlStorageBackend;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * v1.5.0 storage facade.
 *
 * Gameplay code keeps using in-memory YamlConfiguration documents. Persistence
 * is delegated to YAML, SQLite or MySQL without exposing backend-specific types
 * to managers or the public API.
 */
public final class StorageBundle implements AutoCloseable {
    private static final Set<String> DOCUMENT_KEYS = Set.of(
            "parties", "wars", "season", "hall", "interactions"
    );
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JavaPlugin plugin;
    private final YamlConfiguration settings;
    private final Path dataDirectory;
    private final YamlStorageBackend yamlBackend;
    private final ExecutorService writer;
    private final AtomicReference<Snapshot> pending = new AtomicReference<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong();
    private final Object ioLock = new Object();

    private volatile StorageBackend backend;
    private final String configuredBackend;
    private volatile String activeBackend;
    private volatile boolean degraded;
    private volatile String lastError = "";
    private volatile long lastSuccessfulWriteAt;
    private volatile long lastPersistedSequence;

    public final YamlConfiguration parties = new YamlConfiguration();
    public final YamlConfiguration wars = new YamlConfiguration();
    public final YamlConfiguration season = new YamlConfiguration();
    public final YamlConfiguration hall = new YamlConfiguration();
    public final YamlConfiguration interactions = new YamlConfiguration();

    public StorageBundle(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataFolder().toPath();
        File settingsFile = new File(plugin.getDataFolder(), "storage.yml");
        if (!settingsFile.isFile()) plugin.saveResource("storage.yml", false);
        this.settings = YamlConfiguration.loadConfiguration(settingsFile);
        this.yamlBackend = new YamlStorageBackend(dataDirectory);
        this.configuredBackend = normalizeBackend(settings.getString("storage.backend", "YAML"));
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MENKIESTESParty-Storage");
            thread.setDaemon(true);
            return thread;
        });

        try {
            Files.createDirectories(dataDirectory);
            yamlBackend.initialize();
            initializeBackendAndLoad();
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to initialize MENKIESTESParty storage: " + failure.getMessage(), failure);
        }
    }

    private void initializeBackendAndLoad() throws Exception {
        if ("YAML".equals(configuredBackend)) {
            backend = yamlBackend;
            activeBackend = "YAML";
            loadDocuments(yamlBackend.load(DOCUMENT_KEYS));
            return;
        }

        StorageBackend requested = createSqlBackend(configuredBackend);
        try {
            requested.initialize();
            Map<String, String> sqlDocuments = new LinkedHashMap<>(requested.load(DOCUMENT_KEYS));
            Map<String, String> yamlDocuments = yamlBackend.load(DOCUMENT_KEYS);
            boolean changed = false;

            if (sqlDocuments.isEmpty()
                    && settings.getBoolean("storage.migration.import-yaml-when-sql-empty", true)
                    && hasMeaningfulData(yamlDocuments)) {
                sqlDocuments.putAll(yamlDocuments);
                requested.save(sqlDocuments);
                changed = true;
                plugin.getLogger().info("Storage migration: imported existing YAML documents into " + configuredBackend + ".");
            } else if (settings.getBoolean("storage.migration.import-missing-yaml-documents", true)) {
                for (String key : DOCUMENT_KEYS) {
                    if (!sqlDocuments.containsKey(key) && yamlDocuments.containsKey(key)) {
                        sqlDocuments.put(key, yamlDocuments.get(key));
                        changed = true;
                    }
                }
                if (changed) requested.save(sqlDocuments);
            }

            backend = requested;
            activeBackend = configuredBackend;
            loadDocuments(sqlDocuments);
            lastSuccessfulWriteAt = System.currentTimeMillis();

            if (settings.getBoolean("storage.mirror-yaml-backup", true)) {
                try {
                    yamlBackend.save(snapshotDocuments());
                } catch (Exception mirrorFailure) {
                    plugin.getLogger().warning("Initial YAML mirror failed: " + mirrorFailure.getMessage());
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
            plugin.getLogger().severe(configuredBackend + " unavailable; using YAML fallback. Cause: " + lastError);
        }
    }

    private StorageBackend createSqlBackend(String type) {
        String table = settings.getString("storage.sql.table", "menkiestesparty_storage");
        if ("SQLITE".equals(type)) {
            String configuredFile = settings.getString("storage.sqlite.file", "storage.db");
            Path file = new File(configuredFile == null ? "storage.db" : configuredFile).isAbsolute()
                    ? Path.of(configuredFile)
                    : dataDirectory.resolve(configuredFile == null ? "storage.db" : configuredFile);
            String url = "jdbc:sqlite:" + file.toAbsolutePath();
            return new JdbcStorageBackend("SQLITE", "org.sqlite.JDBC", url, new Properties(), table);
        }

        if ("MYSQL".equals(type)) {
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
            return new JdbcStorageBackend("MYSQL", "com.mysql.cj.jdbc.Driver", url, properties, table);
        }

        throw new IllegalArgumentException("Unsupported storage backend: " + type);
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
        pending.set(snapshot); // coalesce: only the newest not-yet-started snapshot matters
        scheduleDrain();
    }

    /** Durable/synchronous save. Used by reward receipts and shutdown. */
    public boolean saveAllBlocking() {
        Snapshot snapshot = snapshot();
        pending.set(null);
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
                    persist(next);
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
                lastSuccessfulWriteAt = System.currentTimeMillis();
                lastPersistedSequence = snapshot.sequence();
                lastError = "";

                if (!activeBackend.startsWith("YAML")
                        && settings.getBoolean("storage.mirror-yaml-backup", true)) {
                    try {
                        yamlBackend.save(snapshot.documents());
                    } catch (Exception mirrorFailure) {
                        plugin.getLogger().warning("YAML mirror write failed: " + mirrorFailure.getMessage());
                    }
                }
                return true;
            } catch (Exception failure) {
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
            try {
                backend.close();
            } catch (Exception ignored) {
            }
            backend = yamlBackend;
            activeBackend = "YAML_FALLBACK";
            degraded = true;
            yamlBackend.save(snapshot.documents());
            lastSuccessfulWriteAt = System.currentTimeMillis();
            lastPersistedSequence = snapshot.sequence();
            plugin.getLogger().severe("Storage switched to YAML fallback after runtime SQL failure. Restart after fixing SQL to retry "
                    + configuredBackend + ".");
            return true;
        } catch (Exception fallbackFailure) {
            lastError = "primary=" + safeMessage(original) + "; fallback=" + safeMessage(fallbackFailure);
            plugin.getLogger().severe("YAML fallback also failed: " + fallbackFailure.getMessage());
            return false;
        }
    }

    private Snapshot snapshot() {
        return new Snapshot(sequence.incrementAndGet(), snapshotDocuments());
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
        return CompletableFuture.supplyAsync(() -> {
            synchronized (ioLock) {
                return backend.ping();
            }
        }, writer);
    }

    public boolean verifyBlocking() {
        synchronized (ioLock) {
            return backend.ping();
        }
    }

    public Path backupNow() throws Exception {
        Path backupDirectory = dataDirectory.resolve("backups")
                .resolve("manual-" + BACKUP_STAMP.format(LocalDateTime.now()));
        YamlStorageBackend backup = new YamlStorageBackend(backupDirectory);
        backup.initialize();
        backup.save(snapshotDocuments());
        return backupDirectory;
    }

    public String configuredBackend() {
        return configuredBackend;
    }

    public String activeBackend() {
        return activeBackend;
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

    public String backendDiagnostics() {
        return backend == null ? "uninitialized" : backend.diagnostics();
    }

    @Override
    public void close() {
        saveAllBlocking();
        writer.shutdown();
        int timeout = Math.max(1, settings.getInt("storage.shutdown-flush-timeout-seconds", 10));
        try {
            if (!writer.awaitTermination(timeout, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
        try {
            if (backend != null) backend.close();
        } catch (Exception failure) {
            plugin.getLogger().warning("Storage close warning: " + failure.getMessage());
        }
    }

    private static String normalizeBackend(String raw) {
        String normalized = raw == null ? "YAML" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "YAML", "SQLITE", "MYSQL" -> normalized;
            default -> throw new IllegalArgumentException("storage.backend must be YAML, SQLITE or MYSQL (got " + raw + ")");
        };
    }

    private static boolean hasMeaningfulData(Map<String, String> documents) {
        return documents.values().stream().anyMatch(value -> value != null && !value.isBlank());
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record Snapshot(long sequence, Map<String, String> documents) {
    }
}
