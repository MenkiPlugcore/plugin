package id.cadera.menkiestesparty.api.v2;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MENKIESTESParty public API v2.
 *
 * This service is additive. The original MenkiPartyAPI v1.0 remains registered
 * for existing integrations. Consumers should obtain this interface through
 * Bukkit ServicesManager and avoid depending on internal managers/YAML paths.
 */
public interface MenkiPartyAPIv2 {
    String API_VERSION = "2.0";

    String apiVersion();
    String legacyApiVersion();
    String pluginVersion();

    RuntimeSnapshot runtime();

    Optional<String> partyKey(UUID playerId);
    Optional<PartySnapshot> party(UUID playerId);
    Optional<PartySnapshot> party(String partyKey);
    List<PartySnapshot> parties();
    Optional<MemberSnapshot> member(UUID playerId);

    Optional<ProjectSnapshot> currentProject(String partyKey);
    List<ContractSnapshot> contracts(String partyKey);
    RelationSnapshot relation(String firstPartyKey, String secondPartyKey);

    boolean addPartyExperience(String partyKey, int amount, String reason);
    boolean broadcast(String partyKey, String message);
    boolean hasCapability(UUID playerId, String capability);
    boolean integrationAvailable(String integration);

    record RuntimeSnapshot(
            String pluginVersion,
            int documentSchemaVersion,
            int storageBackendProtocolVersion,
            String configuredBackend,
            String activeBackend,
            boolean storageDegraded,
            String schedulerMode,
            boolean foliaDetected,
            boolean foliaCertified,
            String nodeId,
            String configuredNetworkMode,
            String activeNetworkMode,
            boolean distributedTransport,
            boolean transportHealthy,
            long networkSequence,
            long emittedThisBoot
    ) {}

    record SocialProfileSnapshot(
            String description,
            String tag,
            String color,
            String icon,
            String visibility,
            String activeBadgeId,
            String activeBadgeDisplay,
            List<String> unlockedAchievements,
            long dynamicActivity
    ) {
        public SocialProfileSnapshot {
            unlockedAchievements = List.copyOf(unlockedAchievements);
        }
    }

    record MemberSnapshot(
            UUID uuid,
            String name,
            String role,
            String division,
            boolean online,
            long joinedAt,
            String activityStatus
    ) {}

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
            long createdAt,
            long revision,
            SocialProfileSnapshot social
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
