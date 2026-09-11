package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import id.cadera.menkiestesparty.api.v2.MenkiPartyAPIv2;
import id.cadera.menkiestesparty.architecture.StorageDocumentSchema;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureReleaseContractTest {

    @Test
    void v2ReleaseMetadataAndArchitectureDefaultsArePresent() throws Exception {
        String plugin = resource("plugin.yml");
        String architecture = resource("architecture.yml");
        String storageSource = Files.readString(Path.of(
                "src/main/java/id/cadera/menkiestesparty/StorageBundle.java"), StandardCharsets.UTF_8);

        assertTrue(plugin.contains("version: 2."));
        assertTrue(plugin.contains("partyarchitecture:"));
        assertTrue(plugin.contains("menkiestesparty.architecture.inspect:"));
        assertTrue(architecture.contains("api-v2:"));
        assertTrue(architecture.contains("auto-migrate: true"));
        assertTrue(architecture.contains("block-legacy-startup: true"));
        assertTrue(architecture.contains("mode: LOCAL"));
        assertTrue(architecture.contains("publish-event-envelopes: true"));
        assertTrue(architecture.contains("persist-revisions: true"));

        // The logical document schema moves to v2 while the proven raw-document
        // backend/table protocol intentionally remains v1 for YAML/SQLite/MySQL.
        assertTrue(storageSource.contains("public static final int STORAGE_SCHEMA_VERSION = 1;"));
        assertEquals(2, StorageDocumentSchema.CURRENT_VERSION);
        assertEquals("1.0", MenkiPartyAPI.API_VERSION);
        assertEquals("2.0", MenkiPartyAPIv2.API_VERSION);

        assertTrue(Files.isRegularFile(Path.of("RELEASE_NOTES_v2.0.0.md")));
        assertTrue(Files.isRegularFile(Path.of("ARCHITECTURE.md")));
        assertTrue(Files.isRegularFile(Path.of("API_V2.md")));
        assertTrue(Files.isRegularFile(Path.of("CHANGELOG.md")));
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = ArchitectureReleaseContractTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name + " missing from resources");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
