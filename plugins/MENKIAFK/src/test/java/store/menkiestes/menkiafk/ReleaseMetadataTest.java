package store.menkiestes.menkiafk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseMetadataTest {

    @Test
    void pluginMetadataMatchesProductionStable() throws IOException {
        String pluginYml = resource("plugin.yml");

        assertTrue(pluginYml.contains("name: MENKIAFK"));
        assertTrue(pluginYml.contains("version: '1.6.0'"));
        assertTrue(pluginYml.contains("api-version: '1.21.11'"));
        assertTrue(pluginYml.contains("main: store.menkiestes.menkiafk.MenkiAfkPlugin"));
        assertTrue(pluginYml.contains("softdepend: [PlaceholderAPI, Essentials]"));
        assertFalse(pluginYml.toLowerCase().contains("production-candidate"));
    }

    @Test
    void defaultConfigIsStillUniversalAndStableVersioned() throws IOException {
        String config = resource("config.yml");

        assertTrue(config.contains("MENKIAFK v1.6.0 Universal"));
        assertTrue(config.contains("timezone: \"system\""));
        assertTrue(config.contains("minimum-session-seconds: 10"));
        assertFalse(config.contains("mysql:"));
        assertFalse(config.contains("redis:"));
        assertFalse(config.contains("vault:"));
    }

    private static String resource(String name) throws IOException {
        try (InputStream input = ReleaseMetadataTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, "Missing test resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
