package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import id.cadera.menkiestesparty.api.event.PartyContractStatusEvent;
import id.cadera.menkiestesparty.api.event.PartyCreateEvent;
import id.cadera.menkiestesparty.api.event.PartyDisbandEvent;
import id.cadera.menkiestesparty.api.event.PartyLevelChangeEvent;
import id.cadera.menkiestesparty.api.event.PartyMemberChangeEvent;
import id.cadera.menkiestesparty.api.event.PartyProjectCompleteEvent;
import id.cadera.menkiestesparty.api.event.PartyRelationChangeEvent;
import id.cadera.menkiestesparty.api.event.PartyWarStateEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * v1.4.0 public API provider + post-state event bridge.
 *
 * The bridge observes state after mutations instead of exposing internal YAML
 * objects. Events are intentionally non-cancellable and represent committed
 * state. This keeps external integrations decoupled from MENKIESTESParty's
 * future storage backend.
 */
public final class DeveloperApiManager implements MenkiPartyAPI {
    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final ProgressionManager progression;
    private final InteractionManager interactions;
    private final StorageBundle db;

    private final Map<String, PartySnapshot> partySnapshot = new LinkedHashMap<>();
    private final Map<String, Long> projectCompletionSnapshot = new HashMap<>();
    private final Map<String, ContractSnapshot> contractSnapshot = new HashMap<>();
    private final Map<String, RelationSnapshot> relationSnapshot = new HashMap<>();
    private String warPhaseSnapshot;

    private Object vaultEconomy;
    private Class<?> vaultEconomyClass;
    private long nextVaultProbeAt;

    public DeveloperApiManager(MENKIESTESPartyPlugin plugin, PartyService parties,
                               ProgressionManager progression, InteractionManager interactions,
                               StorageBundle db) {
        this.plugin = plugin;
        this.parties = parties;
        this.progression = progression;
        this.interactions = interactions;
        this.db = db;
        captureBaseline();
        refreshVault();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("developer.api.enabled", true);
    }

    public void tick() {
        if (!enabled()) return;
        if (!vaultReady() && System.currentTimeMillis() >= nextVaultProbeAt) refreshVault();
        scanParties();
        scanContracts();
        scanRelations();
        scanWar();
    }

    private void captureBaseline() {
        partySnapshot.clear();
        projectCompletionSnapshot.clear();
        for (String key : partyKeys()) {
            party(key).ifPresent(snapshot -> partySnapshot.put(key, snapshot));
            projectCompletionSnapshot.put(key, projectCompletedAt(key));
        }

        contractSnapshot.clear();
        for (ContractSnapshot contract : allContracts()) contractSnapshot.put(contract.id(), contract);

        relationSnapshot.clear();
        relationSnapshot.putAll(allRelations());
        warPhaseSnapshot = plugin.war().phase().name();
    }

    private void scanParties() {
        Map<String, PartySnapshot> current = new LinkedHashMap<>();
        Map<String, Long> currentProjectTimes = new HashMap<>();

        for (String key : partyKeys()) {
            Optional<PartySnapshot> optional = party(key);
            if (optional.isEmpty()) continue;
            PartySnapshot now = optional.get();
            current.put(key, now);

            PartySnapshot old = partySnapshot.get(key);
            if (old == null) {
                fire(new PartyCreateEvent(now));
            } else {
                scanMembers(old, now);
                if (old.level() != now.level()) {
                    fire(new PartyLevelChangeEvent(key, old.level(), now.level()));
                    if (now.level() > old.level()) {
                        runReward("party-level-up", key, Map.of(
                                "old_level", String.valueOf(old.level()),
                                "new_level", String.valueOf(now.level())
                        ));
                    }
                }
            }

            long completedAt = projectCompletedAt(key);
            currentProjectTimes.put(key, completedAt);
            long oldCompletedAt = projectCompletionSnapshot.getOrDefault(key, completedAt);
            if (old != null && completedAt > 0L && completedAt > oldCompletedAt) {
                String id = db.parties.getString("parties." + key + ".progression.projects.last-completed", "");
                String name = id == null || id.isBlank() ? "Unknown" : progression.projectName(id);
                fire(new PartyProjectCompleteEvent(key, id == null ? "" : id, name));
                runReward("project-complete", key, Map.of(
                        "project", id == null ? "" : id,
                        "project_name", name
                ));
            }
        }

        for (Map.Entry<String, PartySnapshot> old : partySnapshot.entrySet()) {
            if (!current.containsKey(old.getKey())) fire(new PartyDisbandEvent(old.getValue()));
        }

        partySnapshot.clear();
        partySnapshot.putAll(current);
        projectCompletionSnapshot.clear();
        projectCompletionSnapshot.putAll(currentProjectTimes);
    }

    private void scanMembers(PartySnapshot oldParty, PartySnapshot newParty) {
        Map<UUID, MemberSnapshot> oldMembers = new HashMap<>();
        for (MemberSnapshot member : oldParty.members()) oldMembers.put(member.uuid(), member);

        Map<UUID, MemberSnapshot> newMembers = new HashMap<>();
        for (MemberSnapshot member : newParty.members()) newMembers.put(member.uuid(), member);

        for (Map.Entry<UUID, MemberSnapshot> entry : newMembers.entrySet()) {
            if (!oldMembers.containsKey(entry.getKey())) {
                fire(new PartyMemberChangeEvent(newParty.key(), PartyMemberChangeEvent.Action.JOIN, entry.getValue()));
            }
        }
        for (Map.Entry<UUID, MemberSnapshot> entry : oldMembers.entrySet()) {
            if (!newMembers.containsKey(entry.getKey())) {
                fire(new PartyMemberChangeEvent(oldParty.key(), PartyMemberChangeEvent.Action.LEAVE, entry.getValue()));
            }
        }
    }

    private void scanContracts() {
        Map<String, ContractSnapshot> current = new HashMap<>();
        for (ContractSnapshot contract : allContracts()) {
            current.put(contract.id(), contract);
            ContractSnapshot old = contractSnapshot.get(contract.id());
            if (old == null) {
                fire(new PartyContractStatusEvent(contract, "NONE", contract.status()));
                continue;
            }
            if (!old.status().equalsIgnoreCase(contract.status())) {
                fire(new PartyContractStatusEvent(contract, old.status(), contract.status()));
                if ("COMPLETED".equalsIgnoreCase(contract.status())) {
                    runReward("contract-complete", contract.targetParty(), Map.of(
                            "contract", contract.id(),
                            "contract_type", contract.type(),
                            "source_party_key", contract.sourceParty(),
                            "target_party_key", contract.targetParty(),
                            "source_party", displaySafe(contract.sourceParty()),
                            "target_party", displaySafe(contract.targetParty())
                    ));
                }
            }
        }
        contractSnapshot.clear();
        contractSnapshot.putAll(current);
    }

    private void scanRelations() {
        Map<String, RelationSnapshot> current = allRelations();
        for (Map.Entry<String, RelationSnapshot> entry : current.entrySet()) {
            RelationSnapshot now = entry.getValue();
            RelationSnapshot old = relationSnapshot.get(entry.getKey());
            if (old == null) continue;
            if (!old.relation().equalsIgnoreCase(now.relation()) || old.trust() != now.trust()) {
                fire(new PartyRelationChangeEvent(
                        now, old.relation(), now.relation(), old.trust(), now.trust()));
            }
        }
        relationSnapshot.clear();
        relationSnapshot.putAll(current);
    }

    private void scanWar() {
        String now = plugin.war().phase().name();
        if (warPhaseSnapshot == null) {
            warPhaseSnapshot = now;
            return;
        }
        if (warPhaseSnapshot.equals(now)) return;

        String winner = null;
        if ("ACTIVE".equals(warPhaseSnapshot) && "NONE".equals(now)) {
            int historyId = db.wars.getInt("history-seq", 0);
            winner = historyId <= 0 ? null : db.wars.getString("history." + historyId + ".winner");
            if (winner != null && parties.exists(winner)) {
                runReward("war-win", winner, Map.of(
                        "war_history_id", String.valueOf(historyId)
                ));
            }
        }
        fire(new PartyWarStateEvent(warPhaseSnapshot, now, winner));
        warPhaseSnapshot = now;
    }

    private void fire(org.bukkit.event.Event event) {
        if (!plugin.getConfig().getBoolean("developer.events.enabled", true)) return;
        Bukkit.getPluginManager().callEvent(event);
    }

    @Override public String apiVersion() { return MenkiPartyAPI.API_VERSION; }
    @Override public String pluginVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public Optional<String> partyKey(UUID playerId) {
        if (playerId == null) return Optional.empty();
        String key = parties.partyOf(playerId);
        return key == null || !parties.exists(key) ? Optional.empty() : Optional.of(key);
    }

    @Override
    public Optional<PartySnapshot> party(UUID playerId) {
        return partyKey(playerId).flatMap(this::party);
    }

    @Override
    public Optional<PartySnapshot> party(String partyKey) {
        String key = normalizePartyKey(partyKey);
        if (!parties.exists(key)) return Optional.empty();

        UUID owner = parties.owner(key);
        List<MemberSnapshot> members = new ArrayList<>();
        ConfigurationSection memberSection = db.parties.getConfigurationSection("parties." + key + ".members");
        if (memberSection != null) {
            for (String uuidText : memberSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidText);
                    String name = memberSection.getString(uuidText + ".name", uuidText);
                    String role = owner != null && owner.equals(uuid)
                            ? PartyService.Role.OWNER.name()
                            : memberSection.getString(uuidText + ".role", PartyService.Role.MEMBER.name()).toUpperCase(Locale.ROOT);
                    String division = memberSection.getString(uuidText + ".division", "none").toLowerCase(Locale.ROOT);
                    members.add(new MemberSnapshot(uuid, name, role, division, Bukkit.getPlayer(uuid) != null));
                } catch (Exception ignored) {
                }
            }
        }
        members.sort(Comparator.comparing(MemberSnapshot::name, String.CASE_INSENSITIVE_ORDER));

        String identity = progression.identityDisplay(progression.identity(key));
        String recruitment = interactions.recruitmentMode(key);
        long createdAt = db.parties.getLong("parties." + key + ".created-at", 0L);

        return Optional.of(new PartySnapshot(
                key,
                parties.display(key),
                owner,
                parties.level(key),
                parties.rep(key),
                parties.memberLimit(key),
                members,
                identity,
                recruitment,
                createdAt
        ));
    }

    @Override
    public List<PartySnapshot> parties() {
        List<PartySnapshot> out = new ArrayList<>();
        for (String key : partyKeys()) party(key).ifPresent(out::add);
        return List.copyOf(out);
    }

    @Override
    public Optional<ProjectSnapshot> currentProject(String partyKey) {
        String key = normalizePartyKey(partyKey);
        if (!parties.exists(key)) return Optional.empty();
        String id = progression.currentProject(key);
        if (id == null) return Optional.empty();
        return Optional.of(new ProjectSnapshot(
                id,
                progression.projectName(id),
                progression.projectType(id),
                progression.projectProgress(key),
                progression.projectGoal(id),
                db.parties.getLong("parties." + key + ".progression.projects.active.started-at", 0L)
        ));
    }

    @Override
    public List<ContractSnapshot> contracts(String partyKey) {
        String key = normalizePartyKey(partyKey);
        if (!parties.exists(key)) return List.of();
        List<ContractSnapshot> out = new ArrayList<>();
        for (ContractSnapshot contract : allContracts()) {
            if (key.equals(contract.sourceParty()) || key.equals(contract.targetParty())) out.add(contract);
        }
        out.sort(Comparator.comparingLong(ContractSnapshot::createdAt).reversed());
        return List.copyOf(out);
    }

    @Override
    public RelationSnapshot relation(String firstPartyKey, String secondPartyKey) {
        String first = normalizePartyKey(firstPartyKey);
        String second = normalizePartyKey(secondPartyKey);
        return new RelationSnapshot(
                first,
                second,
                interactions.relation(first, second),
                interactions.trust(first, second)
        );
    }

    @Override
    public boolean addPartyExperience(String partyKey, int amount, String reason) {
        String key = normalizePartyKey(partyKey);
        if (!enabled() || !parties.exists(key) || amount == 0) return false;
        parties.addRep(key, amount);
        plugin.saveDataSoon();
        if (reason != null && !reason.isBlank()) {
            plugin.getLogger().fine("API Party XP " + (amount > 0 ? "+" : "") + amount + " -> " + key + " (" + reason + ")");
        }
        return true;
    }

    @Override
    public boolean broadcast(String partyKey, String message) {
        String key = normalizePartyKey(partyKey);
        if (!enabled() || !parties.exists(key) || message == null || message.isBlank()) return false;
        parties.broadcastParty(key, Util.color(message));
        return true;
    }

    @Override
    public boolean hasCapability(UUID playerId, String capability) {
        return playerId != null && capability != null && interactions.rankAllows(playerId, capability);
    }

    @Override
    public boolean integrationAvailable(String integration) {
        if (integration == null) return false;
        return switch (integration.toLowerCase(Locale.ROOT)) {
            case "vault" -> vaultReady();
            case "placeholderapi", "papi" -> Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
            default -> false;
        };
    }

    private void runReward(String path, String partyKey, Map<String, String> extra) {
        if (!plugin.getConfig().getBoolean("developer.rewards.enabled", true)) return;
        String key = normalizePartyKey(partyKey);
        if (!parties.exists(key)) return;

        Map<String, String> placeholders = rewardPlaceholders(key);
        placeholders.putAll(extra);

        double money = Math.max(0.0,
                plugin.getConfig().getDouble("developer.rewards." + path + ".vault-owner-money", 0.0));
        if (money > 0.0 && plugin.getConfig().getBoolean("developer.integrations.vault.enabled", true)) {
            if (!depositOwner(key, money)) {
                plugin.getLogger().warning("Reward " + path + ": Vault deposit gagal/tidak tersedia untuk Party " + key + ".");
            }
        }

        for (String raw : plugin.getConfig().getStringList("developer.rewards." + path + ".commands")) {
            if (raw == null || raw.isBlank()) continue;
            String command = replace(raw, placeholders);
            if (command.startsWith("/")) command = command.substring(1);
            if (!command.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private Map<String, String> rewardPlaceholders(String partyKey) {
        Map<String, String> map = new HashMap<>();
        UUID owner = parties.owner(partyKey);
        OfflinePlayer offline = owner == null ? null : Bukkit.getOfflinePlayer(owner);
        String ownerName = offline == null || offline.getName() == null ? "" : offline.getName();

        map.put("party_key", partyKey);
        map.put("party", parties.display(partyKey));
        map.put("owner", ownerName);
        map.put("owner_uuid", owner == null ? "" : owner.toString());
        map.put("level", String.valueOf(parties.level(partyKey)));
        map.put("experience", String.valueOf(parties.rep(partyKey)));
        return map;
    }

    private String replace(String input, Map<String, String> placeholders) {
        String out = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return out;
    }

    private void refreshVault() {
        nextVaultProbeAt = System.currentTimeMillis() + 30_000L;
        vaultEconomy = null;
        vaultEconomyClass = null;
        if (!plugin.getConfig().getBoolean("developer.integrations.vault.enabled", true)) return;

        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) return;
        try {
            Class<?> economyClass = Class.forName(
                    "net.milkbowl.vault.economy.Economy",
                    true,
                    vault.getClass().getClassLoader()
            );
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null || registration.getProvider() == null) return;
            vaultEconomyClass = economyClass;
            vaultEconomy = registration.getProvider();
            plugin.getLogger().info("Vault Economy integration enabled: " + vaultEconomy.getClass().getSimpleName());
        } catch (Throwable t) {
            plugin.getLogger().warning("Vault terdeteksi tetapi Economy hook gagal: " + t.getMessage());
        }
    }

    private boolean vaultReady() {
        return vaultEconomy != null && vaultEconomyClass != null;
    }

    private boolean depositOwner(String partyKey, double amount) {
        if (!vaultReady() || amount <= 0.0) return false;
        UUID owner = parties.owner(partyKey);
        if (owner == null) return false;
        try {
            Method method = vaultEconomyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            Object response = method.invoke(vaultEconomy, Bukkit.getOfflinePlayer(owner), amount);
            if (response == null) return true;
            try {
                Method successful = response.getClass().getMethod("transactionSuccess");
                Object value = successful.invoke(response);
                return value instanceof Boolean b && b;
            } catch (ReflectiveOperationException ignored) {
                return true;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Vault deposit gagal: " + t.getMessage());
            return false;
        }
    }

    private List<ContractSnapshot> allContracts() {
        ConfigurationSection root = db.interactions.getConfigurationSection("contracts");
        if (root == null) return List.of();
        List<ContractSnapshot> out = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            String base = "contracts." + id;
            String source = db.interactions.getString(base + ".source", "");
            String target = db.interactions.getString(base + ".target", "");
            if (source.isBlank() || target.isBlank()) continue;
            out.add(new ContractSnapshot(
                    id,
                    source,
                    target,
                    db.interactions.getString(base + ".type", "unknown"),
                    db.interactions.getInt(base + ".progress", 0),
                    db.interactions.getInt(base + ".goal", 0),
                    db.interactions.getString(base + ".status", "UNKNOWN").toUpperCase(Locale.ROOT),
                    db.interactions.getLong(base + ".created-at", 0L),
                    db.interactions.getLong(base + ".deadline", 0L)
            ));
        }
        return out;
    }

    private Map<String, RelationSnapshot> allRelations() {
        ConfigurationSection root = db.interactions.getConfigurationSection("diplomacy.pairs");
        if (root == null) return Collections.emptyMap();
        Map<String, RelationSnapshot> out = new HashMap<>();
        for (String pair : root.getKeys(false)) {
            String base = "diplomacy.pairs." + pair;
            String a = db.interactions.getString(base + ".a", "");
            String b = db.interactions.getString(base + ".b", "");
            if (a.isBlank() || b.isBlank()) continue;
            out.put(pair, new RelationSnapshot(
                    a,
                    b,
                    db.interactions.getString(base + ".relation", "NEUTRAL").toUpperCase(Locale.ROOT),
                    db.interactions.getInt(base + ".trust", 0)
            ));
        }
        return out;
    }

    private List<String> partyKeys() {
        ConfigurationSection root = db.parties.getConfigurationSection("parties");
        if (root == null) return List.of();
        List<String> keys = new ArrayList<>(root.getKeys(false));
        keys.sort(Comparator.comparing(parties::display, String.CASE_INSENSITIVE_ORDER));
        return keys;
    }

    private long projectCompletedAt(String partyKey) {
        return db.parties.getLong("parties." + partyKey + ".progression.projects.last-completed-at", 0L);
    }

    private String normalizePartyKey(String value) {
        return value == null ? "" : Util.key(value);
    }

    private String displaySafe(String key) {
        return parties.exists(key) ? parties.display(key) : key;
    }
}
