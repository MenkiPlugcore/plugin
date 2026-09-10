package id.cadera.menkiestesparty.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class StorageIntegrity {
    private StorageIntegrity() {}

    public static Map<String, String> checksums(Map<String, String> documents) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : documents.entrySet()) {
            result.put(entry.getKey(), sha256(entry.getValue() == null ? "" : entry.getValue()));
        }
        return Map.copyOf(result);
    }

    public static boolean equivalent(Map<String, String> expected, Map<String, String> actual, Set<String> keys) {
        for (String key : keys) {
            String left = expected.getOrDefault(key, "");
            String right = actual.getOrDefault(key, "");
            if (!sha256(left).equals(sha256(right))) return false;
        }
        return true;
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
