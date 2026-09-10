package id.cadera.menkiestesparty.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

public final class JdbcStorageBackend implements StorageBackend {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private final String type;
    private final String driverClass;
    private final String jdbcUrl;
    private final Properties properties;
    private final String table;
    private final int maxAttempts;
    private final long retryDelayMillis;

    private volatile String lastDiagnostic = "not initialized";
    private volatile long lastFailureAt;
    private volatile int consecutiveFailures;

    public JdbcStorageBackend(String type, String driverClass, String jdbcUrl,
                              Properties properties, String table) {
        this(type, driverClass, jdbcUrl, properties, table, 1, 0L);
    }

    public JdbcStorageBackend(String type, String driverClass, String jdbcUrl,
                              Properties properties, String table,
                              int maxAttempts, long retryDelayMillis) {
        this.type = type;
        this.driverClass = driverClass;
        this.jdbcUrl = jdbcUrl;
        this.properties = properties == null ? new Properties() : copy(properties);
        this.table = sanitizeTable(table);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMillis = Math.max(0L, retryDelayMillis);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public void initialize() throws Exception {
        Class.forName(driverClass);
        executeWithRetry(() -> {
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "storage_key VARCHAR(64) PRIMARY KEY, "
                        + "payload LONGTEXT NOT NULL, "
                        + "updated_at BIGINT NOT NULL"
                        + ")");
            }
            return null;
        });
        markSuccess("connected url=" + redactedUrl());
    }

    @Override
    public Map<String, String> load(Set<String> documentKeys) throws Exception {
        Map<String, String> result = executeWithRetry(() -> {
            Map<String, String> loaded = new LinkedHashMap<>();
            String sql = "SELECT storage_key, payload FROM " + table;
            try (Connection connection = open();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String key = rows.getString(1);
                    if (documentKeys.contains(key)) loaded.put(key, rows.getString(2));
                }
            }
            return loaded;
        });
        markSuccess("load ok url=" + redactedUrl());
        return result;
    }

    @Override
    public void save(Map<String, String> documents) throws Exception {
        executeWithRetry(() -> {
            String updateSql = "UPDATE " + table + " SET payload=?, updated_at=? WHERE storage_key=?";
            String insertSql = "INSERT INTO " + table + " (storage_key, payload, updated_at) VALUES (?, ?, ?)";
            long now = System.currentTimeMillis();

            try (Connection connection = open()) {
                boolean oldAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement update = connection.prepareStatement(updateSql);
                     PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    for (Map.Entry<String, String> entry : documents.entrySet()) {
                        update.setString(1, entry.getValue() == null ? "" : entry.getValue());
                        update.setLong(2, now);
                        update.setString(3, entry.getKey());
                        int changed = update.executeUpdate();
                        if (changed == 0) {
                            insert.setString(1, entry.getKey());
                            insert.setString(2, entry.getValue() == null ? "" : entry.getValue());
                            insert.setLong(3, now);
                            insert.executeUpdate();
                        }
                    }
                    connection.commit();
                } catch (SQLException failure) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                    throw failure;
                } finally {
                    try {
                        connection.setAutoCommit(oldAutoCommit);
                    } catch (SQLException ignored) {
                    }
                }
            }
            return null;
        });
        markSuccess("save ok url=" + redactedUrl());
    }

    @Override
    public boolean ping() {
        try {
            Boolean ok = executeWithRetry(() -> {
                try (Connection connection = open(); Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery("SELECT 1")) {
                    return result.next() && result.getInt(1) == 1;
                }
            });
            if (Boolean.TRUE.equals(ok)) {
                markSuccess("healthy url=" + redactedUrl());
                return true;
            }
            return false;
        } catch (Exception failure) {
            return false;
        }
    }

    @Override
    public String diagnostics() {
        String failure = lastFailureAt <= 0L ? "none" : String.valueOf(lastFailureAt);
        return lastDiagnostic
                + ", table=" + table
                + ", retry-attempts=" + maxAttempts
                + ", consecutive-failures=" + consecutiveFailures
                + ", last-failure-at=" + failure;
    }

    private <T> T executeWithRetry(SqlOperation<T> operation) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = operation.run();
                consecutiveFailures = 0;
                return result;
            } catch (Exception failure) {
                last = failure;
                markFailure(failure);
                if (attempt >= maxAttempts) break;
                if (retryDelayMillis > 0L) {
                    try {
                        Thread.sleep(retryDelayMillis);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted while retrying " + type + " storage", interrupted);
                    }
                }
            }
        }
        throw last == null ? new SQLException(type + " storage operation failed") : last;
    }

    private Connection open() throws SQLException {
        return properties.isEmpty()
                ? DriverManager.getConnection(jdbcUrl)
                : DriverManager.getConnection(jdbcUrl, properties);
    }

    private void markSuccess(String diagnostic) {
        consecutiveFailures = 0;
        lastDiagnostic = diagnostic;
    }

    private void markFailure(Throwable failure) {
        consecutiveFailures++;
        lastFailureAt = System.currentTimeMillis();
        String message = failure.getMessage();
        lastDiagnostic = "last-error=" + failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private String redactedUrl() {
        int query = jdbcUrl.indexOf('?');
        return query < 0 ? jdbcUrl : jdbcUrl.substring(0, query) + "?...";
    }

    private static String sanitizeTable(String requested) {
        String candidate = requested == null || requested.isBlank() ? "menkiestesparty_storage" : requested;
        if (!SAFE_IDENTIFIER.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Unsafe SQL table name: " + candidate);
        }
        return candidate;
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return copy;
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws Exception;
    }
}
