package id.cadera.menkiestesparty.architecture;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java document schema helper for MENKIESTESParty v2.
 *
 * Schema v2 intentionally keeps the existing YAML document bodies intact and
 * adds reserved top-level metadata fields. This means the storage backend
 * protocol and SQL table format do not need to change just to identify the
 * logical document schema.
 */
public final class StorageDocumentSchema {
    public static final int CURRENT_VERSION = 2;

    public static final String VERSION_KEY = "_menkiestesparty_document_schema";
    public static final String DOCUMENT_KEY = "_menkiestesparty_document_key";
    public static final String MIGRATED_AT_KEY = "_menkiestesparty_schema_migrated_at";
    public static final String PLUGIN_VERSION_KEY = "_menkiestesparty_schema_plugin_version";

    private static final Pattern VERSION = Pattern.compile(
            "(?m)^" + Pattern.quote(VERSION_KEY) + ":\\s*['\\\"]?(\\d+)['\\\"]?\\s*$");
    private static final Pattern DOCUMENT = Pattern.compile(
            "(?m)^" + Pattern.quote(DOCUMENT_KEY) + ":\\s*['\\\"]?([^'\\\"\\r\\n]+)['\\\"]?\\s*$");

    private StorageDocumentSchema() {}

    public static int detectVersion(String payload) {
        if (payload == null || payload.isBlank()) return 1;
        Matcher matcher = VERSION.matcher(payload);
        if (!matcher.find()) return 1;
        try {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    public static String detectDocumentKey(String payload) {
        if (payload == null || payload.isBlank()) return "";
        Matcher matcher = DOCUMENT.matcher(payload);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    public static int minimumVersion(Map<String, String> documents, Collection<String> keys) {
        int minimum = CURRENT_VERSION;
        for (String key : keys) {
            minimum = Math.min(minimum, detectVersion(documents.get(key)));
        }
        return minimum;
    }

    public static boolean requiresMigration(Map<String, String> documents, Collection<String> keys) {
        for (String key : keys) {
            String payload = documents.get(key);
            if (detectVersion(payload) < CURRENT_VERSION) return true;
            if (!key.equalsIgnoreCase(detectDocumentKey(payload))) return true;
        }
        return false;
    }

    public static Map<String, String> migrate(Map<String, String> documents,
                                               Collection<String> keys,
                                               long migratedAt,
                                               String pluginVersion) {
        Map<String, String> migrated = new LinkedHashMap<>();
        for (String key : keys) {
            migrated.put(key, migrateDocument(key, documents.get(key), migratedAt, pluginVersion));
        }
        return Map.copyOf(migrated);
    }

    public static String migrateDocument(String documentKey,
                                         String payload,
                                         long migratedAt,
                                         String pluginVersion) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey cannot be blank");
        }
        String normalizedKey = documentKey.trim().toLowerCase();
        String current = payload == null ? "" : payload;
        if (detectVersion(current) >= CURRENT_VERSION
                && normalizedKey.equalsIgnoreCase(detectDocumentKey(current))) {
            return current;
        }

        String body = stripReservedMetadata(current);
        String safeVersion = pluginVersion == null ? "unknown" : pluginVersion.replace("'", "''");

        StringBuilder out = new StringBuilder();
        out.append(VERSION_KEY).append(": ").append(CURRENT_VERSION).append('\n');
        out.append(DOCUMENT_KEY).append(": ").append(normalizedKey).append('\n');
        out.append(MIGRATED_AT_KEY).append(": ").append(Math.max(0L, migratedAt)).append('\n');
        out.append(PLUGIN_VERSION_KEY).append(": '").append(safeVersion).append("'\n");
        if (!body.isBlank()) {
            out.append(body);
            if (!body.endsWith("\n")) out.append('\n');
        }
        return out.toString();
    }

    public static boolean verify(Map<String, String> documents, Collection<String> keys) {
        for (String key : keys) {
            String payload = documents.get(key);
            if (detectVersion(payload) != CURRENT_VERSION) return false;
            if (!key.equalsIgnoreCase(detectDocumentKey(payload))) return false;
        }
        return true;
    }

    public static boolean hasMeaningfulLegacyData(Map<String, String> documents, Collection<String> keys) {
        for (String key : keys) {
            String body = stripReservedMetadata(documents.get(key));
            if (body != null && !body.isBlank()) return true;
        }
        return false;
    }

    private static String stripReservedMetadata(String payload) {
        if (payload == null || payload.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        String[] lines = payload.split("\\R", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (isReservedRootLine(trimmed)) continue;
            out.append(line).append('\n');
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    private static boolean isReservedRootLine(String trimmed) {
        return trimmed.startsWith(VERSION_KEY + ":")
                || trimmed.startsWith(DOCUMENT_KEY + ":")
                || trimmed.startsWith(MIGRATED_AT_KEY + ":")
                || trimmed.startsWith(PLUGIN_VERSION_KEY + ":");
    }
}
