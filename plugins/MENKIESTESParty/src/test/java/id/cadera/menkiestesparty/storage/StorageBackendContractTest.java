package id.cadera.menkiestesparty.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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
                "SQLITE", "org.sqlite.JDBC", url, new Properties(), "menkiestesparty_storage_test");
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
    void packagedJdbcDriversAreResolvable() throws Exception {
        assertNotNull(Class.forName("org.sqlite.JDBC"));
        assertNotNull(Class.forName("com.mysql.cj.jdbc.Driver"));
    }

    @Test
    void rejectsUnsafeSqlTableNames() {
        assertThrows(IllegalArgumentException.class, () -> new JdbcStorageBackend(
                "SQLITE", "org.sqlite.JDBC", "jdbc:sqlite::memory:", new Properties(), "party; DROP TABLE x"));
    }
}
