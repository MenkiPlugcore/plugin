package id.cadera.menkiestesparty;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bukkit-free deterministic repair planning used by v1.6.2 dry-run. */
final class AdminRepairPlanner {
    private AdminRepairPlanner() {}

    record MemberState(String uuid, boolean owner, String role, String indexedParty) {}
    record IndexState(String uuidText, boolean validUuid, boolean memberAfterOwnerRepair) {}
    record Plan(boolean safe, List<String> actions, List<String> warnings) {
        int changeCount() { return actions.size(); }
    }

    static Plan plan(String party, String ownerUuid, boolean ownerInRoster, String ownerRole,
                     String ownerIndexedParty, List<MemberState> members, List<IndexState> indexes) {
        String key = safe(party);
        if (ownerUuid == null || ownerUuid.isBlank()) {
            return new Plan(false, List.of(),
                    List.of("Owner UUID missing/invalid; automatic repair would stop without guessing an owner."));
        }

        Set<String> actions = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        if (!ownerInRoster) actions.add("ADD owner to member roster: " + ownerUuid);
        if (!"OWNER".equalsIgnoreCase(safe(ownerRole))) actions.add("SET owner role to OWNER: " + ownerUuid);
        if (!Objects.equals(key, safe(ownerIndexedParty))) actions.add("FIX owner player index -> " + key + ": " + ownerUuid);

        if (members != null) for (MemberState member : members) {
            if (member == null || member.owner()) continue;
            if (!Objects.equals(key, safe(member.indexedParty()))) {
                actions.add("FIX member player index -> " + key + ": " + safe(member.uuid()));
            }
            if ("OWNER".equalsIgnoreCase(safe(member.role()))) {
                actions.add("DEMOTE extra OWNER role -> OFFICER: " + safe(member.uuid()));
            }
        }

        if (indexes != null) for (IndexState index : indexes) {
            if (index == null) continue;
            if (!index.validUuid()) actions.add("REMOVE invalid player index: " + safe(index.uuidText()));
            else if (!index.memberAfterOwnerRepair()) actions.add("REMOVE orphan player index: " + safe(index.uuidText()));
        }

        return new Plan(true, List.copyOf(actions), List.copyOf(warnings));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
