package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import id.cadera.menkiestesparty.api.v2.MenkiPartyAPIv2;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Additive public API v2 provider. API v1 remains untouched and registered. */
public final class DeveloperApiV2Manager implements MenkiPartyAPIv2 {
    private final MENKIESTESPartyPlugin plugin;
    private final DeveloperApiManager legacy;
    private final SocialIdentityManager social;
    private final ArchitectureManager architecture;
    private final StorageBundle db;

    public DeveloperApiV2Manager(MENKIESTESPartyPlugin plugin,
                                 DeveloperApiManager legacy,
                                 SocialIdentityManager social,
                                 ArchitectureManager architecture,
                                 StorageBundle db) {
        this.plugin = plugin;
        this.legacy = legacy;
        this.social = social;
        this.architecture = architecture;
        this.db = db;
    }

    public boolean enabled() {
        return legacy.enabled() && architecture.apiV2Enabled();
    }

    public boolean verifyContract() {
        try {
            if (!"2.0".equals(MenkiPartyAPIv2.API_VERSION)) return false;
            if (!"1.0".equals(MenkiPartyAPI.API_VERSION)) return false;
            if (!architecture.architectureHealthy()) return false;
            if (runtime().documentSchemaVersion() != 2) return false;
            MenkiPartyAPIv2.class.getMethod("party", String.class);
            MenkiPartyAPIv2.class.getMethod("member", UUID.class);
            MenkiPartyAPIv2.class.getMethod("runtime");
            MenkiPartyAPIv2.PartySnapshot.class.getRecordComponents();
            MenkiPartyAPIv2.SocialProfileSnapshot.class.getRecordComponents();
            return true;
        } catch (Throwable failure) {
            plugin.getLogger().warning("API v2 contract verification failed: " + failure.getMessage());
            return false;
        }
    }

    @Override public String apiVersion() { return MenkiPartyAPIv2.API_VERSION; }
    @Override public String legacyApiVersion() { return MenkiPartyAPI.API_VERSION; }
    @Override public String pluginVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public RuntimeSnapshot runtime() {
        return new RuntimeSnapshot(
                pluginVersion(),
                architecture.documentSchemaVersion(),
                db.schemaVersion(),
                db.configuredBackend(),
                db.activeBackend(),
                db.degraded(),
                plugin.schedulerCompat().mode(),
                plugin.schedulerCompat().foliaDetected(),
                false,
                architecture.nodeId(),
                architecture.configuredNetworkMode(),
                architecture.activeNetworkMode(),
                architecture.distributedTransport(),
                architecture.transportHealthy(),
                architecture.networkSequence(),
                architecture.emittedThisBoot()
        );
    }

    @Override
    public Optional<String> partyKey(UUID playerId) {
        return legacy.partyKey(playerId);
    }

    @Override
    public Optional<PartySnapshot> party(UUID playerId) {
        return legacy.party(playerId).map(this::enrich);
    }

    @Override
    public Optional<PartySnapshot> party(String partyKey) {
        return legacy.party(partyKey).map(this::enrich);
    }

    @Override
    public List<PartySnapshot> parties() {
        List<PartySnapshot> out = new ArrayList<>();
        for (MenkiPartyAPI.PartySnapshot snapshot : legacy.parties()) out.add(enrich(snapshot));
        return List.copyOf(out);
    }

    @Override
    public Optional<MemberSnapshot> member(UUID playerId) {
        if (playerId == null) return Optional.empty();
        return party(playerId).flatMap(party -> party.members().stream()
                .filter(member -> member.uuid().equals(playerId)).findFirst());
    }

    private PartySnapshot enrich(MenkiPartyAPI.PartySnapshot base) {
        List<MemberSnapshot> members = new ArrayList<>();
        for (MenkiPartyAPI.MemberSnapshot member : base.members()) {
            members.add(new MemberSnapshot(
                    member.uuid(),
                    member.name(),
                    member.role(),
                    member.division(),
                    member.online(),
                    social.memberSince(member.uuid()),
                    social.memberStatus(member.uuid())
            ));
        }
        members.sort(Comparator.comparing(MemberSnapshot::name, String.CASE_INSENSITIVE_ORDER));

        SocialProfileSnapshot socialSnapshot = new SocialProfileSnapshot(
                social.description(base.key()),
                social.tag(base.key()),
                social.colorName(base.key()),
                social.iconName(base.key()),
                social.visibility(base.key()),
                social.activeBadgeId(base.key()),
                social.activeBadgeDisplay(base.key()),
                unlockedAchievements(base.key()),
                social.activityTotal(base.key())
        );

        return new PartySnapshot(
                base.key(),
                base.displayName(),
                base.owner(),
                base.level(),
                base.experience(),
                base.memberLimit(),
                members,
                base.identity(),
                base.recruitment(),
                base.createdAt(),
                architecture.revision(base.key()),
                socialSnapshot
        );
    }

    private List<String> unlockedAchievements(String partyKey) {
        ConfigurationSection section = db.parties.getConfigurationSection(
                "parties." + partyKey + ".social.achievements");
        if (section == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            if (section.getLong(id + ".unlocked-at", 0L) > 0L) out.add(id.toLowerCase(Locale.ROOT));
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(out);
    }

    @Override
    public Optional<ProjectSnapshot> currentProject(String partyKey) {
        return legacy.currentProject(partyKey).map(project -> new ProjectSnapshot(
                project.id(),
                project.displayName(),
                project.type(),
                project.progress(),
                project.goal(),
                project.startedAt()
        ));
    }

    @Override
    public List<ContractSnapshot> contracts(String partyKey) {
        List<ContractSnapshot> out = new ArrayList<>();
        for (MenkiPartyAPI.ContractSnapshot contract : legacy.contracts(partyKey)) {
            out.add(new ContractSnapshot(
                    contract.id(),
                    contract.sourceParty(),
                    contract.targetParty(),
                    contract.type(),
                    contract.progress(),
                    contract.goal(),
                    contract.status(),
                    contract.createdAt(),
                    contract.deadline()
            ));
        }
        return List.copyOf(out);
    }

    @Override
    public RelationSnapshot relation(String firstPartyKey, String secondPartyKey) {
        MenkiPartyAPI.RelationSnapshot relation = legacy.relation(firstPartyKey, secondPartyKey);
        return new RelationSnapshot(
                relation.firstParty(),
                relation.secondParty(),
                relation.relation(),
                relation.trust()
        );
    }

    @Override
    public boolean addPartyExperience(String partyKey, int amount, String reason) {
        return legacy.addPartyExperience(partyKey, amount, reason);
    }

    @Override
    public boolean broadcast(String partyKey, String message) {
        return legacy.broadcast(partyKey, message);
    }

    @Override
    public boolean hasCapability(UUID playerId, String capability) {
        return legacy.hasCapability(playerId, capability);
    }

    @Override
    public boolean integrationAvailable(String integration) {
        if (integration == null) return false;
        return switch (integration.trim().toLowerCase(Locale.ROOT)) {
            case "api_v1", "api-v1" -> true;
            case "api_v2", "api-v2" -> enabled();
            case "network", "network-foundation" -> true;
            case "distributed-network", "redis" -> architecture.distributedTransport();
            default -> legacy.integrationAvailable(integration);
        };
    }
}
