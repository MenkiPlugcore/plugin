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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
 * v1.4.1 compatibility guard for the public API and event/reward bridge.
 *
 * The public MenkiPartyAPI v1.0 interface stays unchanged. This manager owns
 * runtime contract verification, de-duplicated post-state events, at-most-once
 * reward receipts, and admin diagnostics. Core Party gameplay never depends on
 * this manager being healthy.
 */
public final class ApiHardeningManager implements CommandExecutor, TabCompleter {
    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final ProgressionManager progression;
    private final InteractionManager interactions;
    private final StorageBundle db;
    private final DeveloperApiManager api;

    private final Map<String, MenkiPartyAPI.PartySnapshot> partySnapshot = new LinkedHashMap<>();
    private final Map<String, Long> projectCompletionSnapshot = new HashMap<>();
    private final Map<String, MenkiPartyAPI.ContractSnapshot> contractSnapshot = new HashMap<>();
    private final Map<String, MenkiPartyAPI.RelationSnapshot> relationSnapshot = new HashMap<>();
    private final Map<String, Long> eventFingerprints = new HashMap<>();
    private String warPhaseSnapshot;

    private Object vaultEconomy;
    private Class<?> vaultEconomyClass;
    private long nextVaultProbeAt;
    private long ticks;
    private CompatibilityReport lastReport = new CompatibilityReport(false, List.of("Not checked yet"), 0L);

    public record CompatibilityReport(boolean healthy, List<String> checks, long checkedAt) {
        public CompatibilityReport {
            checks = List.copyOf(checks);
        }
    }

    public ApiHardeningManager(MENKIESTESPartyPlugin plugin, PartyService parties,
                               ProgressionManager progression, InteractionManager interactions,
                               StorageBundle db, DeveloperApiManager api) {
        this.plugin = plugin;
        this.parties = parties;
        this.progression = progression;
        this.interactions = interactions;
        this.db = db;
        this.api = api;

        PluginCommand command = plugin.getCommand("partyapi");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        captureBaseline();
        refreshVault();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("developer.hardening.enabled", true);
    }

    public boolean failClosed() {
        return plugin.getConfig().getBoolean("developer.hardening.fail-closed-on-contract-error", true);
    }

    public boolean startupCheck() {
        lastReport = verifyCompatibility();
        if (lastReport.healthy()) {
            if (plugin.getConfig().getBoolean("developer.hardening.log-startup-diagnostics", true)) {
                plugin.getLogger().info("API hardening check: HEALTHY (API v" + api.apiVersion() + ").");
            }
        } else {
            plugin.getLogger().severe("API hardening check FAILED. Public API service should not be trusted.");
            for (String line : lastReport.checks()) {
                if (line.startsWith("FAIL")) plugin.getLogger().severe(" - " + line);
            }
        }
        return lastReport.healthy();
    }

    public String healthLabel() {
        if (!enabled()) return "DISABLED";
        if (lastReport.checkedAt() <= 0L) return "UNCHECKED";
        return lastReport.healthy() ? "HEALTHY" : "FAILED";
    }

    public CompatibilityReport verifyCompatibility() {
        List<String> checks = new ArrayList<>();
        boolean ok = true;

        ok &= check(checks, "API version constant is 1.0", "1.0".equals(MenkiPartyAPI.API_VERSION));
        ok &= checkMethod(checks, "apiVersion", String.class);
        ok &= checkMethod(checks, "pluginVersion", String.class);
        ok &= checkMethod(checks, "partyKey", Optional.class, UUID.class);
        ok &= checkMethod(checks, "party", Optional.class, UUID.class);
        ok &= checkMethod(checks, "party", Optional.class, String.class);
        ok &= checkMethod(checks, "parties", List.class);
        ok &= checkMethod(checks, "currentProject", Optional.class, String.class);
        ok &= checkMethod(checks, "contracts", List.class, String.class);
        ok &= checkMethod(checks, "relation", MenkiPartyAPI.RelationSnapshot.class, String.class, String.class);
        ok &= checkMethod(checks, "addPartyExperience", boolean.class, String.class, int.class, String.class);
        ok &= checkMethod(checks, "broadcast", boolean.class, String.class, String.class);
        ok &= checkMethod(checks, "hasCapability", boolean.class, UUID.class, String.class);
        ok &= checkMethod(checks, "integrationAvailable", boolean.class, String.class);

        ok &= check(checks, "PartySnapshot remains a record", MenkiPartyAPI.PartySnapshot.class.isRecord());
        ok &= check(checks, "MemberSnapshot remains a record", MenkiPartyAPI.MemberSnapshot.class.isRecord());
        ok &= check(checks, "ProjectSnapshot remains a record", MenkiPartyAPI.ProjectSnapshot.class.isRecord());
        ok &= check(checks, "ContractSnapshot remains a record", MenkiPartyAPI.ContractSnapshot.class.isRecord());
        ok &= check(checks, "RelationSnapshot remains a record", MenkiPartyAPI.RelationSnapshot.class.isRecord());

        Class<?>[] eventTypes = {
                PartyCreateEvent.class,
                PartyDisbandEvent.class,
                PartyMemberChangeEvent.class,
                PartyLevelChangeEvent.class,
                PartyProjectCompleteEvent.class,
                PartyContractStatusEvent.class,
                PartyRelationChangeEvent.class,
                PartyWarStateEvent.class
        };
        for (Class<?> eventType : eventTypes) {
            ok &= check(checks, eventType.getSimpleName() + " is a Bukkit Event",
                    Event.class.isAssignableFrom(eventType));
        }

        if (api.enabled()) {
            RegisteredServiceProvider<MenkiPartyAPI> registration =
                    Bukkit.getServicesManager().getRegistration(MenkiPartyAPI.class);
            ok &= check(checks, "ServicesManager provider is registered",
                    registration != null && registration.getProvider() == api);
        } else {
            checks.add("PASS API disabled by configuration; service registration is optional");
        }

        try {
            List<MenkiPartyAPI.PartySnapshot> snapshot = api.parties();
            ok &= check(checks, "API smoke read returned immutable top-level list", snapshot != null);
        } catch (Throwable t) {
            ok &= check(checks, "API smoke read succeeded: " + t.getClass().getSimpleName(), false);
        }

        CompatibilityReport report = new CompatibilityReport(ok, checks, System.currentTimeMillis());
        lastReport = report;
        return report;
    }

    private boolean checkMethod(List<String> checks, String name, Class<?> returnType, Class<?>... parameters) {
        try {
            Method method = MenkiPartyAPI.class.getMethod(name, parameters);
            return check(checks, "Method " + signature(name, parameters) + " -> " + returnType.getSimpleName(),
                    method.getReturnType().equals(returnType));
        } catch (NoSuchMethodException e) {
            return check(checks, "Method " + signature(name, parameters) + " exists", false);
        }
    }

    private String signature(String name, Class<?>[] parameters) {
        List<String> parts = new ArrayList<>();
        for (Class<?> parameter : parameters) parts.add(parameter.getSimpleName());
        return name + "(" + String.join(",", parts) + ")";
    }

    private boolean check(List<String> checks, String label, boolean passed) {
        checks.add((passed ? "PASS " : "FAIL ") + label);
        return passed;
    }

    public void tick() {
        if (!enabled()) {
            api.tick();
            return;
        }
        if (failClosed() && lastReport.checkedAt() > 0L && !lastReport.healthy()) return;

        long now = System.currentTimeMillis();
        if (!vaultReady() && now >= nextVaultProbeAt) refreshVault();

        scanParties();
        scanContracts();
        scanRelations();
        scanWar();
        cleanupEventFingerprints(now);

        ticks++;
        if (ticks % 300L == 0L) pruneRewardReceipts();
    }

    private void captureBaseline() {
        partySnapshot.clear();
        projectCompletionSnapshot.clear();
        for (MenkiPartyAPI.PartySnapshot snapshot : api.parties()) {
            partySnapshot.put(snapshot.key(), snapshot);
            projectCompletionSnapshot.put(snapshot.key(), projectCompletedAt(snapshot.key()));
        }
        contractSnapshot.clear();
        for (MenkiPartyAPI.ContractSnapshot contract : allContracts()) contractSnapshot.put(contract.id(), contract);
        relationSnapshot.clear();
        relationSnapshot.putAll(allRelations());
        warPhaseSnapshot = plugin.war().phase().name();
    }

    private void scanParties() {
        Map<String, MenkiPartyAPI.PartySnapshot> current = new LinkedHashMap<>();
        Map<String, Long> projectTimes = new HashMap<>();

        for (MenkiPartyAPI.PartySnapshot now : api.parties()) {
            String key = now.key();
            current.put(key, now);
            MenkiPartyAPI.PartySnapshot old = partySnapshot.get(key);

            if (old == null) {
                fireOnce("party-create|" + key, new PartyCreateEvent(now));
            } else {
                scanMembers(old, now);
                if (old.level() != now.level()) {
                    fireOnce("level|" + key + "|" + old.level() + ">" + now.level(),
                            new PartyLevelChangeEvent(key, old.level(), now.level()));
                    if (now.level() > old.level()) {
                        runRewardOnce("party-level-up", key,
                                "party-level-up|" + key + "|level=" + now.level(),
                                Map.of("old_level", String.valueOf(old.level()),
                                        "new_level", String.valueOf(now.level())));
                    }
                }
            }

            long completedAt = projectCompletedAt(key);
            projectTimes.put(key, completedAt);
            long previous = projectCompletionSnapshot.getOrDefault(key, completedAt);
            if (old != null && completedAt > 0L && completedAt > previous) {
                String id = db.parties.getString("parties." + key + ".progression.projects.last-completed", "");
                String name = id == null || id.isBlank() ? "Unknown" : progression.projectName(id);
                fireOnce("project|" + key + "|" + completedAt,
                        new PartyProjectCompleteEvent(key, id == null ? "" : id, name));
                runRewardOnce("project-complete", key,
                        "project-complete|" + key + "|" + completedAt,
                        Map.of("project", id == null ? "" : id, "project_name", name));
            }
        }

        for (Map.Entry<String, MenkiPartyAPI.PartySnapshot> old : partySnapshot.entrySet()) {
            if (!current.containsKey(old.getKey())) {
                fireOnce("party-disband|" + old.getKey(), new PartyDisbandEvent(old.getValue()));
            }
        }

        partySnapshot.clear();
        partySnapshot.putAll(current);
        projectCompletionSnapshot.clear();
        projectCompletionSnapshot.putAll(projectTimes);
    }

    private void scanMembers(MenkiPartyAPI.PartySnapshot oldParty, MenkiPartyAPI.PartySnapshot newParty) {
        Map<UUID, MenkiPartyAPI.MemberSnapshot> oldMembers = new HashMap<>();
        for (MenkiPartyAPI.MemberSnapshot member : oldParty.members()) oldMembers.put(member.uuid(), member);
        Map<UUID, MenkiPartyAPI.MemberSnapshot> newMembers = new HashMap<>();
        for (MenkiPartyAPI.MemberSnapshot member : newParty.members()) newMembers.put(member.uuid(), member);

        for (Map.Entry<UUID, MenkiPartyAPI.MemberSnapshot> entry : newMembers.entrySet()) {
            if (!oldMembers.containsKey(entry.getKey())) {
                fireOnce("member-join|" + newParty.key() + "|" + entry.getKey(),
                        new PartyMemberChangeEvent(newParty.key(), PartyMemberChangeEvent.Action.JOIN, entry.getValue()));
            }
        }
        for (Map.Entry<UUID, MenkiPartyAPI.MemberSnapshot> entry : oldMembers.entrySet()) {
            if (!newMembers.containsKey(entry.getKey())) {
                fireOnce("member-leave|" + oldParty.key() + "|" + entry.getKey(),
                        new PartyMemberChangeEvent(oldParty.key(), PartyMemberChangeEvent.Action.LEAVE, entry.getValue()));
            }
        }
    }

    private void scanContracts() {
        Map<String, MenkiPartyAPI.ContractSnapshot> current = new HashMap<>();
        for (MenkiPartyAPI.ContractSnapshot contract : allContracts()) {
            current.put(contract.id(), contract);
            MenkiPartyAPI.ContractSnapshot old = contractSnapshot.get(contract.id());
            if (old == null) {
                fireOnce("contract|" + contract.id() + "|NONE>" + contract.status(),
                        new PartyContractStatusEvent(contract, "NONE", contract.status()));
                continue;
            }
            if (!old.status().equalsIgnoreCase(contract.status())) {
                fireOnce("contract|" + contract.id() + "|" + old.status() + ">" + contract.status(),
                        new PartyContractStatusEvent(contract, old.status(), contract.status()));
                if ("COMPLETED".equalsIgnoreCase(contract.status())) {
                    runRewardOnce("contract-complete", contract.targetParty(),
                            "contract-complete|" + contract.id(),
                            Map.of(
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
        Map<String, MenkiPartyAPI.RelationSnapshot> current = allRelations();
        for (Map.Entry<String, MenkiPartyAPI.RelationSnapshot> entry : current.entrySet()) {
            MenkiPartyAPI.RelationSnapshot now = entry.getValue();
            MenkiPartyAPI.RelationSnapshot old = relationSnapshot.get(entry.getKey());
            if (old == null) continue;
            if (!old.relation().equalsIgnoreCase(now.relation()) || old.trust() != now.trust()) {
                String fingerprint = "relation|" + entry.getKey() + "|" + old.relation() + ":" + old.trust()
                        + ">" + now.relation() + ":" + now.trust();
                fireOnce(fingerprint,
                        new PartyRelationChangeEvent(now, old.relation(), now.relation(), old.trust(), now.trust()));
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
        int historyId = db.wars.getInt("history-seq", 0);
        if ("ACTIVE".equals(warPhaseSnapshot) && "NONE".equals(now)) {
            winner = historyId <= 0 ? null : db.wars.getString("history." + historyId + ".winner");
            if (winner != null && parties.exists(winner)) {
                runRewardOnce("war-win", winner,
                        "war-win|history=" + historyId,
                        Map.of("war_history_id", String.valueOf(historyId)));
            }
        }

        fireOnce("war|" + warPhaseSnapshot + ">" + now + "|history=" + historyId,
                new PartyWarStateEvent(warPhaseSnapshot, now, winner));
        warPhaseSnapshot = now;
    }

    private boolean fireOnce(String fingerprint, Event event) {
        if (!plugin.getConfig().getBoolean("developer.events.enabled", true)) return false;
        long now = System.currentTimeMillis();
        long window = Math.max(0L,
                plugin.getConfig().getLong("developer.hardening.event-dedup-window-ms", 5000L));
        Long previous = eventFingerprints.get(fingerprint);
        if (previous != null && now - previous < window) {
            plugin.getLogger().warning("Suppressed duplicate API event fingerprint: " + fingerprint);
            return false;
        }
        eventFingerprints.put(fingerprint, now);
        Bukkit.getPluginManager().callEvent(event);
        return true;
    }

    private void cleanupEventFingerprints(long now) {
        long window = Math.max(1000L,
                plugin.getConfig().getLong("developer.hardening.event-dedup-window-ms", 5000L));
        eventFingerprints.entrySet().removeIf(entry -> now - entry.getValue() > window * 2L);
    }

    private void runRewardOnce(String path, String partyKey, String receiptFingerprint,
                               Map<String, String> extra) {
        if (!plugin.getConfig().getBoolean("developer.rewards.enabled", true)) return;
        String key = normalizePartyKey(partyKey);
        if (!parties.exists(key)) return;

        double money = Math.max(0.0,
                plugin.getConfig().getDouble("developer.rewards." + path + ".vault-owner-money", 0.0));
        List<String> configuredCommands = plugin.getConfig().getStringList("developer.rewards." + path + ".commands");
        if (money <= 0.0 && configuredCommands.isEmpty()) return;

        String receiptId = receiptId(receiptFingerprint);
        String base = "developer-hardening.reward-receipts." + receiptId;
        if (db.interactions.contains(base + ".reserved-at")) {
            plugin.getLogger().warning("Suppressed duplicate reward receipt: " + receiptFingerprint);
            return;
        }

        long now = System.currentTimeMillis();
        db.interactions.set(base + ".fingerprint", receiptFingerprint);
        db.interactions.set(base + ".reward", path);
        db.interactions.set(base + ".party", key);
        db.interactions.set(base + ".reserved-at", now);
        db.interactions.set(base + ".state", "RESERVED");
        // Rare synchronous flush by design: reserve before external side effects.
        plugin.flush();

        Map<String, String> placeholders = rewardPlaceholders(key);
        placeholders.putAll(extra);

        boolean moneyOk = true;
        if (money > 0.0 && plugin.getConfig().getBoolean("developer.integrations.vault.enabled", true)) {
            moneyOk = depositOwner(key, money);
            if (!moneyOk) plugin.getLogger().warning("Reward " + path + ": Vault deposit failed/unavailable for Party " + key + ".");
        }

        int maxCommands = Math.max(0,
                plugin.getConfig().getInt("developer.hardening.rewards.max-commands-per-trigger", 20));
        int maxLength = Math.max(32,
                plugin.getConfig().getInt("developer.hardening.rewards.max-command-length", 512));
        int executed = 0;
        int failed = 0;

        for (String raw : configuredCommands) {
            if (executed + failed >= maxCommands) {
                failed++;
                plugin.getLogger().warning("Reward " + path + ": command limit exceeded; remaining commands skipped.");
                break;
            }
            if (raw == null || raw.isBlank()) continue;
            String command = replace(raw, placeholders);
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank()) continue;
            if (command.length() > maxLength) {
                failed++;
                plugin.getLogger().warning("Reward " + path + ": command exceeds max length and was skipped.");
                continue;
            }
            if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) executed++;
            else failed++;
        }

        db.interactions.set(base + ".completed-at", System.currentTimeMillis());
        db.interactions.set(base + ".money", money);
        db.interactions.set(base + ".money-success", moneyOk);
        db.interactions.set(base + ".commands-executed", executed);
        db.interactions.set(base + ".commands-failed", failed);
        db.interactions.set(base + ".state", moneyOk && failed == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
        plugin.flush();
    }

    private String receiptId(String fingerprint) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private void pruneRewardReceipts() {
        int retentionDays = Math.max(1,
                plugin.getConfig().getInt("developer.hardening.reward-receipt-retention-days", 90));
        long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
        ConfigurationSection section = db.interactions.getConfigurationSection("developer-hardening.reward-receipts");
        if (section == null) return;
        boolean changed = false;
        for (String id : new ArrayList<>(section.getKeys(false))) {
            long completed = section.getLong(id + ".completed-at", section.getLong(id + ".reserved-at", 0L));
            if (completed > 0L && completed < cutoff) {
                db.interactions.set("developer-hardening.reward-receipts." + id, null);
                changed = true;
            }
        }
        if (changed) plugin.saveDataSoon();
    }

    public int rewardReceiptCount() {
        ConfigurationSection section = db.interactions.getConfigurationSection("developer-hardening.reward-receipts");
        return section == null ? 0 : section.getKeys(false).size();
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
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy", true,
                    vault.getClass().getClassLoader());
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null || registration.getProvider() == null) return;
            vaultEconomyClass = economyClass;
            vaultEconomy = registration.getProvider();
        } catch (Throwable t) {
            plugin.getLogger().warning("v1.4.1 Vault hardening hook failed: " + t.getMessage());
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
                return value instanceof Boolean bool && bool;
            } catch (ReflectiveOperationException ignored) {
                return true;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("v1.4.1 Vault deposit failed: " + t.getMessage());
            return false;
        }
    }

    private List<MenkiPartyAPI.ContractSnapshot> allContracts() {
        ConfigurationSection root = db.interactions.getConfigurationSection("contracts");
        if (root == null) return List.of();
        List<MenkiPartyAPI.ContractSnapshot> out = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            String base = "contracts." + id;
            String source = db.interactions.getString(base + ".source", "");
            String target = db.interactions.getString(base + ".target", "");
            if (source.isBlank() || target.isBlank()) continue;
            out.add(new MenkiPartyAPI.ContractSnapshot(
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

    private Map<String, MenkiPartyAPI.RelationSnapshot> allRelations() {
        ConfigurationSection root = db.interactions.getConfigurationSection("diplomacy.pairs");
        if (root == null) return Collections.emptyMap();
        Map<String, MenkiPartyAPI.RelationSnapshot> out = new HashMap<>();
        for (String pair : root.getKeys(false)) {
            String base = "diplomacy.pairs." + pair;
            String a = db.interactions.getString(base + ".a", "");
            String b = db.interactions.getString(base + ".b", "");
            if (a.isBlank() || b.isBlank()) continue;
            out.put(pair, new MenkiPartyAPI.RelationSnapshot(
                    a,
                    b,
                    db.interactions.getString(base + ".relation", "NEUTRAL").toUpperCase(Locale.ROOT),
                    db.interactions.getInt(base + ".trust", 0)
            ));
        }
        return out;
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("menkiestesparty.admin")) {
            sender.sendMessage(Util.color("&cNo permission."));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("status")) {
            showStatus(sender);
            return true;
        }
        if (sub.equals("verify")) {
            CompatibilityReport report = verifyCompatibility();
            sender.sendMessage(Util.color("&8&m--------------------------------"));
            sender.sendMessage(Util.color("&b&lMENKIESTESParty API VERIFY &8- "
                    + (report.healthy() ? "&aHEALTHY" : "&cFAILED")));
            for (String line : report.checks()) {
                sender.sendMessage(Util.color((line.startsWith("PASS") ? "&a" : "&c") + line));
            }
            return true;
        }
        sender.sendMessage(Util.color("&c/partyapi [status|verify]"));
        return true;
    }

    private void showStatus(CommandSender sender) {
        RegisteredServiceProvider<MenkiPartyAPI> registration =
                Bukkit.getServicesManager().getRegistration(MenkiPartyAPI.class);
        boolean service = registration != null && registration.getProvider() == api;
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&b&lMENKIESTESParty API STATUS"));
        sender.sendMessage(Util.color("&7Plugin: &f" + api.pluginVersion() + " &8| &7API: &f" + api.apiVersion()));
        sender.sendMessage(Util.color("&7Health: " + (lastReport.healthy() ? "&aHEALTHY" : "&c" + healthLabel())));
        sender.sendMessage(Util.color("&7Service registered: " + (service ? "&aYES" : "&cNO")));
        sender.sendMessage(Util.color("&7Events: &f" + plugin.getConfig().getBoolean("developer.events.enabled", true)
                + " &8| &7De-dup window: &f"
                + plugin.getConfig().getLong("developer.hardening.event-dedup-window-ms", 5000L) + "ms"));
        sender.sendMessage(Util.color("&7Rewards: &f" + plugin.getConfig().getBoolean("developer.rewards.enabled", true)
                + " &8| &7Receipts: &f" + rewardReceiptCount()));
        sender.sendMessage(Util.color("&7Vault reward hook: " + (vaultReady() ? "&aREADY" : "&7UNAVAILABLE")
                + " &8| &7PAPI: " + (api.integrationAvailable("placeholderapi") ? "&aREADY" : "&7UNAVAILABLE")));
        sender.sendMessage(Util.color("&7Fail-closed API: &f" + failClosed()));
        sender.sendMessage(Util.color("&7Use &f/partyapi verify &7for the full contract check."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("menkiestesparty.admin")) return List.of();
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return List.of("status", "verify").stream().filter(x -> x.startsWith(typed)).toList();
        }
        return List.of();
    }
}
