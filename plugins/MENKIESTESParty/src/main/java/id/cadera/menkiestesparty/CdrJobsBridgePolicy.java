package id.cadera.menkiestesparty;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bukkit-free policy helpers for the optional CdrJobs bridge.
 */
public final class CdrJobsBridgePolicy {
    public static final List<String> PATHS = List.of("MINER", "FARMER", "HUNTER", "LUMBERJACK", "FISHER");

    private CdrJobsBridgePolicy() {}

    public static String normalizeProfession(String profession) {
        return profession == null ? "" : profession.trim().toUpperCase(Locale.ROOT);
    }

    public static String questType(String profession) {
        return switch (normalizeProfession(profession)) {
            case "MINER" -> "mining";
            case "FARMER" -> "farmer";
            case "HUNTER" -> "hunter";
            case "LUMBERJACK" -> "lumberjack";
            case "FISHER" -> "fisher";
            default -> "";
        };
    }

    public static String projectType(String profession) {
        String normalized = normalizeProfession(profession);
        return PATHS.contains(normalized) ? "cdr_" + normalized.toLowerCase(Locale.ROOT) : "";
    }

    public static boolean resourceProfession(String profession) {
        return switch (normalizeProfession(profession)) {
            case "MINER", "FARMER", "LUMBERJACK", "FISHER" -> true;
            default -> false;
        };
    }

    public static int safeEventAmount(long amount) {
        if (amount <= 0L) return 0;
        return (int) Math.min(Integer.MAX_VALUE, amount);
    }

    /**
     * Returns true when every Five Path can be assigned to a different member.
     */
    public static boolean hasDistinctFivePathCoverage(Map<UUID, ? extends Collection<String>> eligiblePaths) {
        if (eligiblePaths == null || eligiblePaths.size() < PATHS.size()) return false;
        return matchPath(0, eligiblePaths, new java.util.HashSet<>());
    }

    private static boolean matchPath(int index, Map<UUID, ? extends Collection<String>> eligiblePaths, Set<UUID> used) {
        if (index >= PATHS.size()) return true;
        String path = PATHS.get(index);
        for (Map.Entry<UUID, ? extends Collection<String>> entry : eligiblePaths.entrySet()) {
            if (used.contains(entry.getKey())) continue;
            boolean eligible = entry.getValue() != null && entry.getValue().stream()
                    .map(CdrJobsBridgePolicy::normalizeProfession)
                    .anyMatch(path::equals);
            if (!eligible) continue;
            used.add(entry.getKey());
            if (matchPath(index + 1, eligiblePaths, used)) return true;
            used.remove(entry.getKey());
        }
        return false;
    }
}
