package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AdminSnapshotVerifierTest {

    @TempDir
    Path temp;

    @Test
    void approvedBucketsResolveOnlyBasenameYamlFiles() throws Exception {
        Path archive = AdminSnapshotVerifier.resolveSnapshot(temp, "archive", "party-test.yml");
        Path export = AdminSnapshotVerifier.resolveSnapshot(temp, "export", "party-export.yml");

        assertEquals(temp.resolve("archives").toAbsolutePath().normalize(), archive.getParent());
        assertEquals(temp.resolve("admin-exports").toAbsolutePath().normalize(), export.getParent());
        assertThrows(java.io.IOException.class,
                () -> AdminSnapshotVerifier.resolveSnapshot(temp, "archive", "../escape.yml"));
        assertThrows(java.io.IOException.class,
                () -> AdminSnapshotVerifier.resolveSnapshot(temp, "archive", "nested/file.yml"));
        assertThrows(java.io.IOException.class,
                () -> AdminSnapshotVerifier.resolveSnapshot(temp, "other", "file.yml"));
        assertThrows(java.io.IOException.class,
                () -> AdminSnapshotVerifier.resolveSnapshot(temp, "archive", "file.txt"));
    }

    @Test
    void missingSidecarIsLegacyButChecksumMismatchIsCorrupt() throws Exception {
        Path file = AdminSnapshotVerifier.resolveSnapshot(temp, "archive", "party.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "meta:\n  party-key: party\n");

        AdminSnapshotVerifier.Result legacy = AdminSnapshotVerifier.verifyChecksum(file);
        assertEquals(AdminSnapshotVerifier.Status.LEGACY_UNVERIFIED, legacy.status());
        assertEquals(64, legacy.sha256().length());

        String digest = AdminArchiveIntegrity.sha256(file);
        Path sidecar = AdminArchiveIntegrity.writeChecksumSidecar(file, digest);
        assertEquals(AdminSnapshotVerifier.Status.VERIFIED,
                AdminSnapshotVerifier.verifyChecksum(file).status());

        Files.writeString(file, "meta:\n  party-key: tampered\n");
        AdminSnapshotVerifier.Result corrupt = AdminSnapshotVerifier.verifyChecksum(file);
        assertEquals(AdminSnapshotVerifier.Status.CORRUPT, corrupt.status());
        assertTrue(Files.isRegularFile(sidecar));
    }

    @Test
    void sidecarCannotSilentlyReferenceAnotherFilename() throws Exception {
        Path file = AdminSnapshotVerifier.resolveSnapshot(temp, "export", "party.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "party: true\n");
        String digest = AdminArchiveIntegrity.sha256(file);
        Path sidecar = file.resolveSibling(file.getFileName() + ".sha256");
        Files.writeString(sidecar, digest + "  different.yml\n");

        AdminSnapshotVerifier.Result result = AdminSnapshotVerifier.verifyChecksum(file);
        assertEquals(AdminSnapshotVerifier.Status.CORRUPT, result.status());
        assertTrue(result.detail().contains("different filename"));
    }
}
