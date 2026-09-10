package id.cadera.menkiestesparty.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stable public API for MENKIESTESParty.
 *
 * Obtain the provider through Bukkit ServicesManager. Consumers should never
 * access MENKIESTESParty YAML files or internal managers directly.
 */
public interface MenkiPartyAPI {
    String API_VERSION = "1.0";

    String apiVersion();
    String pluginVersion();

    Optional<String> partyKey(UUID playerId);
    Optional<PartySnapshot> party(UUID playerId);
    Optional<PartySnapshot> party(String partyKey);
    List<PartySnapshot> parties();

    Optional<ProjectSnapshot> currentProject(String partyKey);
    List<ContractSnapshot> contracts(String partyKey);
    RelationSnapshot relation(String firstPartyKey, String secondPartyKey);

    boolean addPartyExperience(String partyKey, int amount, String reason);
    boolean broadcast(String partyKey, String message);
    boolean hasCapability(UUID playerId, String capability);

    /** Known names in v1.4.0: vault, placeholderapi. */
    boolean integrationAvailable(String integration);

    record MemberSnapshot(UUID uuid, String name, String role, String division, boolean online) {}

    record PartySnapshot(
            String key,
            String displayName,
            UUID owner,
            int level,
            int experience,
            int memberLimit,
            List<MemberSnapshot> members,
            String identity,
            String recruitment,
            long createdAt
    ) {
        public PartySnapshot {
            members = List.copyOf(members);
        }

        public int memberCount() {
            return members.size();
        }

        public int onlineMembers() {
            return (int) members.stream().filter(MemberSnapshot::online).count();
        }
    }

    record ProjectSnapshot(
            String id,
            String displayName,
            String type,
            double progress,
            int goal,
            long startedAt
    ) {
        public double percent() {
            return goal <= 0 ? 0.0 : Math.min(100.0, progress * 100.0 / goal);
        }
    }

    record ContractSnapshot(
            String id,
            String sourceParty,
            String targetParty,
            String type,
            int progress,
            int goal,
            String status,
            long createdAt,
            long deadline
    ) {}

    record RelationSnapshot(
            String firstParty,
            String secondParty,
            String relation,
            int trust
    ) {}
}
