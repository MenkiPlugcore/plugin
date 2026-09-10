package id.cadera.menkiestesparty.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StorageBackendContractTest {
    @TempDir
    Path temp;

    @Test
    void yamlBackendRoundTripsAndOverwritesDocuments() throws Exception {
        YamlStorageBackend backend = new YamlStorageBackend(temp.resolve("yaml"));
        backend.initialize();
        assertTrue(backend.ping());

        backend.save(Map.of("parties", "parties:\n  alpha: {}\n", "wars", "history-seq: 1\n"));
        Map<String, String> first = backend.load(Set.of("parties", "wars", "season"));
        assertTrue(first.get("parties").contains("alpha"));
        assertEquals("history-seq: 1\n", first.get("wars"));
        assertFalse(first.containsKey("season"));

        backend.save(Map.of("wars", "history-seq: 2\n"));
        assertEquals("history-seq: 2\n", backend.load(Set.of("wars")).get("wars"));
    }

    @Test
    void sqliteBackendRoundTripsAndUpsertsDocuments() throws Exception {
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + temp.resolve("party-test.db").toAbsolutePath();
        JdbcStorageBackend backend = new JdbcStorageBackend(
                "SQLITE", "org.sqlite.JDBC", url, new Properties(),
                "menkiestesparty_storage_test", 2, 5L);
        backend.initialize();
        assertTrue(backend.ping());

        backend.save(Map.of("parties", "a: 1\n", "interactions", "b: 2\n"));
        Map<String, String> first = backend.load(Set.of("parties", "interactions"));
        assertEquals("a: 1\n", first.get("parties"));
        assertEquals("b: 2\n", first.get("interactions"));

        backend.save(Map.of("parties", "a: 3\n"));
        Map<String, String> second = backend.load(Set.of("parties", "interactions"));
        assertEquals("a: 3\n", second.get("parties"));
        assertEquals("b: 2\n", second.get("interactions"));
    }

    @Test
    void mysqlBackendRoundTripsAndUpsertsDocuments() throws Exception {
        String url = System.getenv("MENKI_TEST_MYSQL_URL");
        assumeTrue(url != null && !url.isBlank(), "MySQL service-container test only");

        Properties props = new Properties();
        props.setProperty("user", env("MENKI_TEST_MYSQL_USER", "menkiparty"));
        props.setProperty("password", env("MENKI_TEST_MYSQL_PASSWORD", "testpass"));

        JdbcStorageBackend backend = new JdbcStorageBackend(
                "MYSQL", "com.mysql.cj.jdbc.Driver", url, props,
                "menkiestesparty_storage_ci", 3, 250L);
        backend.initialize();
        assertTrue(backend.ping());

        backend.save(Map.of(
                "parties", "parties:\n  mysql_ci: {}\n",
                "wars", "history-seq: 7\n"));
        Map<String, String> first = backend.load(Set.of("parties", "wars"));
        assertTrue(first.get("parties").contains("mysql_ci"));
        assertEquals("history-seq: 7\n", first.get("wars"));

        backend.save(Map.of("wars", "history-seq: 8\n"));
        assertEquals("history-seq: 8\n", backend.load(Set.of("wars")).get("wars"));
    }

    @Test
    void storageIntegrityDetectsMatchingAndDifferentSnapshots() {
        Map<String, String> first = Map.of(
                "parties", "alpha: 1\n",
                "wars", "seq: 1\n");
        Map<String, String> same = Map.of(
                "parties", "alpha: 1\n",
                "wars", "seq: 1\n");
        Map<String, String> changed = Map.of(
                "parties", "alpha: 2\n",
                "wars", "seq: 1\n");

        assertTrue(StorageIntegrity.equivalent(first, same, Set.of("parties", "wars")));
        assertFalse(StorageIntegrity.equivalent(first, changed, Set.of("parties", "wars")));
        assertEquals(64, StorageIntegrity.sha256("hello").length());
        assertEquals(StorageIntegrity.checksums(first), StorageIntegrity.checksums(same));
    }

    @Test
    void packagedJdbcDriversAreResolvable() throws Exception {
        assertNotNull(Class.forName("org.sqlite.JDBC"));
        assertNotNull(Class.forName("com.mysql.cj.jdbc.Driver"));
    }

    @Test
    void rejectsUnsafeSqlTableNames() {
        assertThrows(IllegalArgumentException.class, () -> new JdbcStorageBackend(
                "SQLITE", "org.sqlite.JDBC", "jdbc:sqlite::memory:",
                new Properties(), "party; DROP TABLE x"));
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
