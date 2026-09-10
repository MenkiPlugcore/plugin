package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AdminArchiveIntegrityTest {

    @TempDir
    Path temp;

    @Test
    void exportPathNeverOverwritesExistingSnapshot() throws Exception {
        Path first = AdminArchiveIntegrity.uniquePath(temp, "party-20260910", ".yml");
        Files.writeString(first, "one");
        Path second = AdminArchiveIntegrity.uniquePath(temp, "party-20260910", ".yml");
        assertNotEquals(first, second);
        assertEquals("party-20260910-2.yml", second.getFileName().toString());
    }

    @Test
    void checksumSidecarDetectsTampering() throws Exception {
        Path file = temp.resolve("snapshot.yml");
        Files.writeString(file, "party:\n  owner: test\n");
        String digest = AdminArchiveIntegrity.sha256(file);
        Path sidecar = AdminArchiveIntegrity.writeChecksumSidecar(file, digest);
        assertTrue(AdminArchiveIntegrity.checksumMatches(file, sidecar));

        Files.writeString(file, "party:\n  owner: changed\n");
        assertFalse(AdminArchiveIntegrity.checksumMatches(file, sidecar));
    }

    @Test
    void unsafeFileStemIsSanitized() {
        String safe = AdminArchiveIntegrity.sanitizeStem("../../PARADOX / test");
        assertFalse(safe.contains(".."));
        assertFalse(safe.contains("/"));
        assertTrue(safe.contains("PARADOX"));
    }
}
