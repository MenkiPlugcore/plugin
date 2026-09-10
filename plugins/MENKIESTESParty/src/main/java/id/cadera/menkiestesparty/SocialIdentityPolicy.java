package id.cadera.menkiestesparty;

import java.util.Locale;

/**
 * Bukkit-free validation/scoring rules for the v1.7.0 social identity layer.
 * Keeping these rules isolated makes them cheap to regression-test and avoids
 * coupling public profile behavior to the server runtime.
 */
final class SocialIdentityPolicy {
    record Metrics(int level, int reputation, int members, int projects, long ageDays, long activity) {}

    private SocialIdentityPolicy() {}

    static String sanitizeDescription(String input, int maxLength) {
        if (input == null) return "";
        String value = input
                .replaceAll("(?i)[&§]x(?:[&§][0-9a-f]){6}", "")
                .replaceAll("(?i)[&§][0-9a-fk-or]", "")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        int cap = Math.max(0, maxLength);
        if (value.length() <= cap) return value;
        return value.substring(0, cap).trim();
    }

    static String normalizeTag(String input, int minLength, int maxLength) {
        if (input == null) return null;
        String tag = input.trim().toUpperCase(Locale.ROOT);
        int min = Math.max(1, minLength);
        int max = Math.max(min, maxLength);
        if (tag.length() < min || tag.length() > max) return null;
        return tag.matches("[A-Z0-9]+") ? tag : null;
    }

    static String activityStatus(boolean online, long lastPlayed, long now, int activeDays, int awayDays) {
        if (online) return "ONLINE";
        if (lastPlayed <= 0L || now <= 0L) return "INACTIVE";
        long age = Math.max(0L, now - lastPlayed);
        long activeWindow = Math.max(1, activeDays) * 86_400_000L;
        long awayWindow = Math.max(Math.max(1, activeDays), awayDays) * 86_400_000L;
        if (age <= activeWindow) return "ACTIVE";
        if (age <= awayWindow) return "AWAY";
        return "INACTIVE";
    }

    static boolean achievementUnlocked(String type, long target, Metrics metrics) {
        if (metrics == null) return false;
        long required = Math.max(0L, target);
        return switch (normalize(type)) {
            case "level" -> metrics.level() >= required;
            case "reputation", "rep" -> metrics.reputation() >= required;
            case "members", "member" -> metrics.members() >= required;
            case "projects", "project" -> metrics.projects() >= required;
            case "age_days", "age" -> metrics.ageDays() >= required;
            case "activity" -> metrics.activity() >= required;
            default -> false;
        };
    }

    static long achievementProgress(String type, Metrics metrics) {
        if (metrics == null) return 0L;
        return switch (normalize(type)) {
            case "level" -> metrics.level();
            case "reputation", "rep" -> metrics.reputation();
            case "members", "member" -> metrics.members();
            case "projects", "project" -> metrics.projects();
            case "age_days", "age" -> metrics.ageDays();
            case "activity" -> metrics.activity();
            default -> 0L;
        };
    }

    static long leaderboardScore(String metric, Metrics metrics) {
        if (metrics == null) return 0L;
        return switch (normalize(metric)) {
            case "level" -> metrics.level();
            case "members", "member" -> metrics.members();
            case "projects", "project" -> metrics.projects();
            case "activity" -> metrics.activity();
            case "age", "age_days" -> metrics.ageDays();
            case "reputation", "rep" -> metrics.reputation();
            default -> metrics.reputation();
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
