package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import id.cadera.menkiestesparty.api.v2.MenkiPartyAPIv2;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CdrJobsIntegrationReleaseContractTest {

    @Test
    void releaseMetadataAndIntegrationResourceArePresent() throws Exception {
        String plugin = resource("plugin.yml");
        String integration = resource("cdrjobs-integration.yml");

        assertTrue(plugin.contains("version: 2.1.0"));
        assertTrue(plugin.contains("CdrJobs Five Paths integration"));
        assertTrue(plugin.contains("softdepend: [PlaceholderAPI, GriefPrevention, Vault, CdrJobs]"));
        assertTrue(integration.contains("minimum-api-version: 2"));
        assertTrue(integration.contains("authoritative-weekly-quests: true"));
        assertTrue(integration.contains("authoritative-daily-missions: true"));
        assertTrue(integration.contains("require-distinct-members: true"));
        assertTrue(integration.contains("type: cdr_lumberjack"));
        assertTrue(integration.contains("type: cdr_fisher"));

        assertTrue(Files.isRegularFile(Path.of("CDRJOBS_INTEGRATION.md")));
        assertTrue(Files.isRegularFile(Path.of("RELEASE_NOTES_v2.1.0.md")));
    }

    @Test
    void integrationDoesNotChangePartyPublicApiVersions() {
        assertEquals("1.0", MenkiPartyAPI.API_VERSION);
        assertEquals("2.0", MenkiPartyAPIv2.API_VERSION);
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = CdrJobsIntegrationReleaseContractTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name + " missing from resources");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
