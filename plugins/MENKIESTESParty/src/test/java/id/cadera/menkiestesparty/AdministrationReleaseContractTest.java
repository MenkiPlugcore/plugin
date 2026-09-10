package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AdministrationReleaseContractTest {

    @Test
    void releaseMetadataAndSafetyDefaultsArePresent() throws Exception {
        String plugin = resource("plugin.yml");
        String administration = resource("administration.yml");

        assertTrue(plugin.contains("version: 1.6.1"));
        assertTrue(plugin.contains("partyadmin:"));
        assertTrue(plugin.contains("menkiestesparty.admin.dangerous:"));
        assertTrue(administration.contains("require-token: true"));
        assertTrue(administration.contains("max-attempts: 3"));
        assertTrue(administration.contains("checksum: SHA-256"));

        assertTrue(Files.isRegularFile(Path.of("RELEASE_NOTES_v1.6.1.md")),
                "Every releasable version must carry GitHub release notes");
        assertTrue(Files.isRegularFile(Path.of("ADMINISTRATION.md")));
        assertTrue(Files.isRegularFile(Path.of("CHANGELOG.md")));
    }

    @Test
    void publicApiVersionRemainsOnePointZero() {
        assertEquals("1.0", id.cadera.menkiestesparty.api.MenkiPartyAPI.API_VERSION);
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = AdministrationReleaseContractTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name + " missing from resources");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
