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
    private volatile String lastDiagnostic = "not initialized";

    public JdbcStorageBackend(String type, String driverClass, String jdbcUrl,
                              Properties properties, String table) {
        this.type = type;
        this.driverClass = driverClass;
        this.jdbcUrl = jdbcUrl;
        this.properties = properties == null ? new Properties() : copy(properties);
        this.table = sanitizeTable(table);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public void initialize() throws Exception {
        Class.forName(driverClass);
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "storage_key VARCHAR(64) PRIMARY KEY, "
                    + "payload LONGTEXT NOT NULL, "
                    + "updated_at BIGINT NOT NULL"
                    + ")");
        }
        lastDiagnostic = "connected url=" + redactedUrl();
    }

    @Override
    public Map<String, String> load(Set<String> documentKeys) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        String sql = "SELECT storage_key, payload FROM " + table;
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String key = rows.getString(1);
                if (documentKeys.contains(key)) result.put(key, rows.getString(2));
            }
        }
        return result;
    }

    @Override
    public void save(Map<String, String> documents) throws SQLException {
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
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        }
    }

    @Override
    public boolean ping() {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                return result.next() && result.getInt(1) == 1;
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    @Override
    public String diagnostics() {
        return lastDiagnostic + ", table=" + table;
    }

    private Connection open() throws SQLException {
        return properties.isEmpty()
                ? DriverManager.getConnection(jdbcUrl)
                : DriverManager.getConnection(jdbcUrl, properties);
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
}
