package id.cadera.menkiestesparty;

import java.util.Locale;

/** Bukkit-free matching rules for v1.6.2 administration audit queries. */
final class AdminAuditQuery {
    private AdminAuditQuery() {}

    record Entry(String id, long at, String actor, String action, String party, String result, String detail) {
        Entry {
            id = safe(id);
            actor = safe(actor);
            action = safe(action);
            party = safe(party);
            result = safe(result);
            detail = safe(detail);
        }
    }

    static boolean matchesSearch(Entry entry, String query) {
        if (entry == null) return false;
        String needle = normalize(query);
        if (needle.isBlank()) return true;
        return normalize(entry.actor()).contains(needle)
                || normalize(entry.action()).contains(needle)
                || normalize(entry.party()).contains(needle)
                || normalize(entry.result()).contains(needle)
                || normalize(entry.detail()).contains(needle);
    }

    static boolean matchesFilter(Entry entry, String field, String value) {
        if (entry == null) return false;
        String needle = normalize(value);
        if (needle.isBlank()) return true;
        return switch (normalize(field)) {
            case "party" -> normalize(entry.party()).contains(needle);
            case "staff", "actor" -> normalize(entry.actor()).contains(needle);
            case "action" -> normalize(entry.action()).contains(needle);
            case "result" -> normalize(entry.result()).contains(needle);
            default -> false;
        };
    }

    static boolean supportedField(String field) {
        return switch (normalize(field)) {
            case "party", "staff", "actor", "action", "result" -> true;
            default -> false;
        };
    }

    private static String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
