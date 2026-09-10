package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SocialIdentityReleaseContractTest {

    @Test
    void releaseMetadataAndSocialDefaultsArePresent() throws Exception {
        String plugin = resource("plugin.yml");
        String social = resource("social.yml");

        assertTrue(plugin.contains("name: MENKIESTESParty"));
        assertTrue(plugin.contains("partysocial:"));
        assertTrue(plugin.contains("partytop:"));
        assertTrue(plugin.contains("menkiestesparty.social.view:"));
        assertTrue(plugin.contains("menkiestesparty.social.edit:"));
        assertTrue(plugin.contains("menkiestesparty.social.leaderboard:"));
        assertTrue(plugin.contains("menkiestesparty.social.private.bypass:"));

        assertTrue(social.contains("social:"));
        assertTrue(social.contains("default-visibility: PUBLIC"));
        assertTrue(social.contains("tag-unique: true"));
        assertTrue(social.contains("leaderboard-include-private: false"));
        assertTrue(social.contains("achievements:"));
        assertTrue(social.contains("active_force:"));

        assertTrue(Files.isRegularFile(Path.of("RELEASE_NOTES_v1.7.0.md")));
        assertTrue(Files.isRegularFile(Path.of("SOCIAL_IDENTITY.md")));
        assertTrue(Files.isRegularFile(Path.of("CHANGELOG.md")));
    }

    @Test
    void publicApiContractRemainsOnePointZero() {
        assertEquals("1.0", id.cadera.menkiestesparty.api.MenkiPartyAPI.API_VERSION);
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = SocialIdentityReleaseContractTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name + " missing from resources");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
