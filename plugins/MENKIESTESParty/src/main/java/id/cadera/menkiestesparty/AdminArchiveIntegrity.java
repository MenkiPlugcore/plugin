package id.cadera.menkiestesparty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Bukkit-free archive/checksum helpers used by the v1.6.1 admin safety layer. */
final class AdminArchiveIntegrity {
    private AdminArchiveIntegrity() {}

    static Path uniquePath(Path directory, String stem, String extension) throws IOException {
        Files.createDirectories(directory);
        String safeStem = sanitizeStem(stem);
        String ext = extension == null || extension.isBlank() ? ".yml"
                : (extension.startsWith(".") ? extension : "." + extension);
        Path candidate = directory.resolve(safeStem + ext);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(safeStem + "-" + suffix++ + ext);
        }
        return candidate;
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static Path writeChecksumSidecar(Path file, String digest) throws IOException {
        Path sidecar = file.resolveSibling(file.getFileName() + ".sha256");
        String line = digest + "  " + file.getFileName() + System.lineSeparator();
        Files.writeString(sidecar, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return sidecar;
    }

    static boolean checksumMatches(Path file, Path sidecar) throws IOException {
        if (!Files.isRegularFile(file) || !Files.isRegularFile(sidecar)) return false;
        String expected = Files.readString(sidecar, StandardCharsets.UTF_8).trim();
        int space = expected.indexOf(' ');
        if (space >= 0) expected = expected.substring(0, space);
        return expected.equalsIgnoreCase(sha256(file));
    }

    static String sanitizeStem(String value) {
        String input = value == null ? "archive" : value.trim();
        String safe = input.replaceAll("[^A-Za-z0-9._-]", "_");
        while (safe.contains("..")) safe = safe.replace("..", ".");
        safe = safe.replaceAll("^[.]+", "").replaceAll("[.]+$", "");
        return safe.isBlank() ? "archive" : safe;
    }
}
