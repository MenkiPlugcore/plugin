package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Build-time guard for the v1.6.0 freeze protections that v1.6.1 must preserve. */
class AdministrationFreezeRegressionTest {

    @Test
    void partyCoreStillGuardsRosterMutations() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/id/cadera/menkiestesparty/PartyService.java"));
        assertTrue(source.contains("private boolean rosterFrozen("));
        assertTrue(count(source, "rosterFrozen(") >= 7,
                "create helper + invite/accept/leave/disband/kick/role paths must retain freeze checks");
        assertTrue(source.contains("admin.blocksRoster(party)"));
    }

    @Test
    void administrationStillGuardsInteractionMutations() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/id/cadera/menkiestesparty/AdministrationManager.java"));
        assertTrue(source.contains("blocksInteractions(String party)"));
        assertTrue(source.contains("onFrozenInteractionClick"));
        assertTrue(source.contains("CONTRACT_MUTATIONS"));
        assertTrue(source.contains("DIPLO_MUTATIONS"));
    }

    private static int count(String text, String needle) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
    }
}
