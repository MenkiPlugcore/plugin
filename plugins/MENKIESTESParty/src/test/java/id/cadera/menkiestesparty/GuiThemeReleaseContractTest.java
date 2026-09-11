package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import id.cadera.menkiestesparty.api.v2.MenkiPartyAPIv2;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GuiThemeReleaseContractTest {

    @Test
    void guiThemeResourceAndReleaseMetadataArePresent() throws Exception {
        String plugin = resource("plugin.yml");
        String theme = resource("gui-theme.yml");

        assertTrue(plugin.contains("version: 2.0.1"));
        assertTrue(plugin.contains("themed inventory UI"));
        assertTrue(theme.contains("fill-empty-slots: true"));
        assertTrue(theme.contains("primary: BLACK_STAINED_GLASS_PANE"));
        assertTrue(theme.contains("accent: CYAN_STAINED_GLASS_PANE"));
        assertTrue(theme.contains("secondary: PURPLE_STAINED_GLASS_PANE"));
        assertTrue(theme.contains("MENKIESTES UI"));

        assertTrue(Files.isRegularFile(Path.of("RELEASE_NOTES_v2.0.1.md")));
        assertTrue(Files.isRegularFile(Path.of("GUI_THEME.md")));
        assertTrue(Files.isRegularFile(Path.of("CHANGELOG.md")));
    }

    @Test
    void guiPatchDoesNotChangePublicApiContracts() {
        assertEquals("1.0", MenkiPartyAPI.API_VERSION);
        assertEquals("2.0", MenkiPartyAPIv2.API_VERSION);
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = GuiThemeReleaseContractTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name + " missing from resources");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
