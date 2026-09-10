package id.cadera.menkiestesparty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Bukkit-free path/checksum verifier for administration snapshots. */
final class AdminSnapshotVerifier {
    private AdminSnapshotVerifier() {}

    enum Status {
        VERIFIED,
        LEGACY_UNVERIFIED,
        CORRUPT,
        MISSING
    }

    record Result(Status status, Path file, Path sidecar, String sha256, String detail) {}

    static Path resolveSnapshot(Path dataFolder, String bucket, String fileName) throws IOException {
        if (dataFolder == null) throw new IOException("plugin data folder is unavailable");
        String directory = switch (normalize(bucket)) {
            case "archive", "archives" -> "archives";
            case "export", "exports", "admin-exports" -> "admin-exports";
            default -> throw new IOException("snapshot bucket must be archive or export");
        };

        String name = fileName == null ? "" : fileName.trim();
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new IOException("snapshot filename must be a basename only");
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            throw new IOException("snapshot filename must end with .yml");
        }

        Path root = dataFolder.resolve(directory).toAbsolutePath().normalize();
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root) || target.getParent() == null || !target.getParent().equals(root)) {
            throw new IOException("snapshot path escapes the approved directory");
        }
        return target;
    }

    static Result verifyChecksum(Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        Path sidecar = normalized.resolveSibling(normalized.getFileName() + ".sha256");
        if (!Files.isRegularFile(normalized)) {
            return new Result(Status.MISSING, normalized, sidecar, "", "snapshot file missing");
        }

        String actual = AdminArchiveIntegrity.sha256(normalized);
        if (!Files.isRegularFile(sidecar)) {
            return new Result(Status.LEGACY_UNVERIFIED, normalized, sidecar, actual,
                    "checksum sidecar missing (legacy/unverified snapshot)");
        }

        String text = Files.readString(sidecar, StandardCharsets.UTF_8).trim();
        if (text.isBlank()) {
            return new Result(Status.CORRUPT, normalized, sidecar, actual, "checksum sidecar is empty");
        }
        String[] parts = text.split("\\s+", 2);
        String expected = parts[0].trim();
        if (!expected.matches("(?i)[0-9a-f]{64}")) {
            return new Result(Status.CORRUPT, normalized, sidecar, actual, "invalid SHA-256 sidecar format");
        }
        if (parts.length > 1) {
            String referenced = parts[1].trim();
            if (referenced.startsWith("*")) referenced = referenced.substring(1);
            if (!referenced.isBlank() && !referenced.equals(normalized.getFileName().toString())) {
                return new Result(Status.CORRUPT, normalized, sidecar, actual,
                        "checksum sidecar references a different filename");
            }
        }
        if (!expected.equalsIgnoreCase(actual)) {
            return new Result(Status.CORRUPT, normalized, sidecar, actual, "SHA-256 mismatch");
        }
        return new Result(Status.VERIFIED, normalized, sidecar, actual, "checksum verified");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
