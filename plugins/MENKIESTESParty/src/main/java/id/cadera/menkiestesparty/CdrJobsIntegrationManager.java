package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Optional CdrJobs Public API v2 bridge.
 *
 * There is intentionally no compile dependency and no direct access to the
 * CdrJobs SQLite database. The bridge discovers the public service at runtime,
 * subscribes to accepted ProfessionActionEvent instances, and falls back to
 * MENKIESTESParty's legacy standalone listeners whenever CdrJobs is absent.
 */
public final class CdrJobsIntegrationManager implements Listener {
    private static final String CDRJOBS = "CdrJobs";
    private static final String SETTINGS_FILE = "cdrjobs-integration.yml";
    private static volatile CdrJobsIntegrationManager instance;

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final ProgressionManager progression;
    private final RewardHallManager daily;
    private final StorageBundle db;
    private final Listener actionListener = new Listener() {};
    private final File settingsFile;
    private final Map<UUID, CachedMemberProfile> profileCache = new ConcurrentHashMap<>();
    private final Map<String, CachedConvergence> convergenceCache = new ConcurrentHashMap<>();

    private YamlConfiguration settings;
    private Object api;
    private Method getPlayerSnapshot;
    private int apiVersion;
    private boolean active;
    private String health = "NOT_CONNECTED";
    private String sourceVersion = "unknown";

    private CdrJobsIntegrationManager(MENKIESTESPartyPlugin plugin, PartyService parties,
                                      ProgressionManager progression, RewardHallManager daily,
                                      StorageBundle db) {
        this.plugin = plugin;
        this.parties = parties;
        this.progression = progression;
        this.daily = daily;
        this.db = db;
        this.settingsFile = new File(plugin.getDataFolder(), SETTINGS_FILE);
        loadSettings();
    }

    public static synchronized CdrJobsIntegrationManager install(MENKIESTESPartyPlugin plugin,
                                                                  PartyService parties,
                                                                  ProgressionManager progression,
                                                                  RewardHallManager daily,
                                                                  StorageBundle db) {
        if (instance != null && instance.plugin == plugin) return instance;
        if (instance != null) instance.disconnect("REPLACED");
        CdrJobsIntegrationManager manager = new CdrJobsIntegrationManager(plugin, parties, progression, daily, db);
        instance = manager;
        Bukkit.getPluginManager().registerEvents(manager, plugin);
        manager.ensureDefaults();
        manager.connect();
        return manager;
    }

    public static CdrJobsIntegrationManager current() { return instance; }

    public static boolean integrationActive(MENKIESTESPartyPlugin plugin) {
        CdrJobsIntegrationManager manager = instance;
        return manager != null && manager.plugin == plugin && manager.active;
    }

    public static boolean authoritativeWeekly(MENKIESTESPartyPlugin plugin) {
        CdrJobsIntegrationManager manager = instance;
        return manager != null && manager.plugin == plugin && manager.active
                && manager.bool("activity.feed-weekly-quests", true)
                && manager.bool("activity.authoritative-weekly-quests", true);
    }

    public static boolean authoritativeDaily(MENKIESTESPartyPlugin plugin) {
        CdrJobsIntegrationManager manager = instance;
        return manager != null && manager.plugin == plugin && manager.active
                && manager.bool("activity.feed-daily-missions", true)
                && manager.bool("activity.authoritative-daily-missions", true);
    }

    public boolean active() { return active; }
    public int apiVersion() { return apiVersion; }
    public String health() { return health; }
    public String sourceVersion() { return sourceVersion; }

    private boolean bool(String path, boolean fallback) {
        return settings == null ? fallback : settings.getBoolean(path, fallback);
    }

    private int integer(String path, int fallback) {
        return settings == null ? fallback : settings.getInt(path, fallback);
    }

    private long longValue(String path, long fallback) {
        return settings == null ? fallback : settings.getLong(path, fallback);
    }

    private void loadSettings() {
        try {
            if (!settingsFile.isFile()) plugin.saveResource(SETTINGS_FILE, false);
            settings = YamlConfiguration.loadConfiguration(settingsFile);
            try (var stream = plugin.getResource(SETTINGS_FILE)) {
                if (stream != null) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(stream, StandardCharsets.UTF_8));
                    settings.setDefaults(defaults);
                    settings.options().copyDefaults(true);
                    settings.save(settingsFile);
                }
            }
        } catch (Exception exception) {
            settings = new YamlConfiguration();
            plugin.getLogger().log(Level.WARNING, "Could not load " + SETTINGS_FILE, exception);
        }
    }

    private synchronized void connect() {
        disconnect("RECONNECTING");
        if (!bool("integration.enabled", true)) {
            health = "DISABLED";
            return;
        }

        Plugin cdr = Bukkit.getPluginManager().getPlugin(CDRJOBS);
        if (cdr == null || !cdr.isEnabled()) {
            health = "CDRJOBS_NOT_INSTALLED";
            return;
        }

        try {
            ClassLoader loader = cdr.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("store.cadera.cdrjobs.api.CdrJobsAPI", true, loader);
            Object service = Bukkit.getServicesManager().load(apiClass);
            if (service == null) service = cdr.getClass().getMethod("getApi").invoke(cdr);
            if (service == null) throw new IllegalStateException("CdrJobs API service is unavailable");

            int version = ((Number) apiClass.getMethod("getApiVersion").invoke(service)).intValue();
            int minimum = Math.max(2, integer("integration.minimum-api-version", 2));
            if (version < minimum) {
                health = "API_TOO_OLD_" + version;
                plugin.getLogger().warning("CdrJobs integration disabled: API v" + version + " < required v" + minimum + ".");
                return;
            }

            Class<?> rawEvent = Class.forName("store.cadera.cdrjobs.api.event.ProfessionActionEvent", true, loader);
            if (!Event.class.isAssignableFrom(rawEvent)) throw new IllegalStateException("ProfessionActionEvent is not a Bukkit Event");

            api = service;
            apiVersion = version;
            getPlayerSnapshot = apiClass.getMethod("getPlayerSnapshot", UUID.class);
            sourceVersion = cdr.getDescription().getVersion();

            @SuppressWarnings("unchecked")
            Class<? extends Event> actionEvent = (Class<? extends Event>) rawEvent;
            EventExecutor executor = (listener, event) -> consumeAction(event);
            Bukkit.getPluginManager().registerEvent(actionEvent, actionListener, EventPriority.MONITOR,
                    executor, plugin, true);

            active = true;
            health = "HEALTHY";
            profileCache.clear();
            convergenceCache.clear();
            syncWeeklyResetMarker();
            plugin.getLogger().info("CdrJobs integration enabled: plugin=" + sourceVersion
                    + ", api=v" + apiVersion
                    + ", weekly-authoritative=" + authoritativeWeekly(plugin)
                    + ", daily-authoritative=" + authoritativeDaily(plugin) + ".");
        } catch (Throwable throwable) {
            disconnect("ERROR");
            health = "ERROR_" + throwable.getClass().getSimpleName();
            plugin.getLogger().log(Level.WARNING,
                    "CdrJobs integration failed; standalone Party activity tracking remains active.", throwable);
        }
    }

    private synchronized void disconnect(String reason) {
        HandlerList.unregisterAll(actionListener);
        active = false;
        api = null;
        getPlayerSnapshot = null;
        apiVersion = 0;
        sourceVersion = "unknown";
        profileCache.clear();
        convergenceCache.clear();
        if (reason != null) health = reason;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase(CDRJOBS)) connect();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equalsIgnoreCase(CDRJOBS)) return;
        disconnect("CDRJOBS_DISABLED");
        plugin.getLogger().warning("CdrJobs disabled; MENKIESTESParty returned to standalone fallback activity listeners.");
    }

    private void consumeAction(Event event) {
        if (!active || api == null) return;
        try {
            Object rawPlayer = event.getClass().getMethod("getPlayer").invoke(event);
            Object rawProfession = event.getClass().getMethod("getProfession").invoke(event);
            Object rawAmount = event.getClass().getMethod("getAmount").invoke(event);
            if (!(rawPlayer instanceof Player player) || !(rawAmount instanceof Number number)) return;

            String profession = CdrJobsBridgePolicy.normalizeProfession(String.valueOf(rawProfession));
            String questType = CdrJobsBridgePolicy.questType(profession);
            int amount = CdrJobsBridgePolicy.safeEventAmount(number.longValue());
            if (questType.isBlank() || amount <= 0) return;

            String party = parties.partyOf(player.getUniqueId());
            if (party == null || !parties.exists(party)) return;
            syncWeeklyResetMarker();

            if (bool("activity.feed-weekly-quests", true)) {
                parties.addQuestProgress(party, questType, player.getUniqueId(), amount);
            }
            if (bool("activity.feed-daily-missions", true)) {
                daily.addTrustedProgress(party, questType, amount);
            }
            if (bool("activity.feed-party-projects", true)) {
                progressCdrProject(party, profession, amount, player.getUniqueId());
            }

            String memberRoot = "parties." + party + ".members." + player.getUniqueId() + ".cdrjobs.";
            String actionPath = memberRoot + profession.toLowerCase(Locale.ROOT) + ".actions";
            db.parties.set(actionPath, saturatingAdd(db.parties.getLong(actionPath, 0L), amount));
            db.parties.set(memberRoot + "last-action-at", System.currentTimeMillis());
            String partyActions = "parties." + party + ".cdrjobs.total-actions";
            db.parties.set(partyActions, saturatingAdd(db.parties.getLong(partyActions, 0L), amount));
            plugin.saveDataSoon();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Failed to consume CdrJobs ProfessionActionEvent", throwable);
        }
    }

    private long saturatingAdd(long current, long amount) {
        if (amount > 0L && current > Long.MAX_VALUE - amount) return Long.MAX_VALUE;
        return current + amount;
    }

    private void progressCdrProject(String party, String profession, int amount, UUID contributor) {
        String id = progression.currentProject(party);
        if (id == null) return;
        String expected = CdrJobsBridgePolicy.projectType(profession);
        if (expected.isBlank() || !progression.projectType(id).equalsIgnoreCase(expected)) return;

        double multiplier = projectProgressMultiplier(party, contributor, profession);
        double contribution = amount * multiplier;
        String root = "parties." + party + ".progression.projects.active";
        double current = Math.max(0.0D, db.parties.getDouble(root + ".progress", 0.0D));
        int goal = progression.projectGoal(id);
        double next = Math.min(goal, current + contribution);
        db.parties.set(root + ".progress", next);

        String memberPath = "parties." + party + ".members." + contributor + ".progression.project-contribution";
        db.parties.set(memberPath, Math.max(0.0D, db.parties.getDouble(memberPath, 0.0D)) + contribution);
        plugin.saveDataSoon();
        if (next < goal) return;

        int reward = effectiveProjectReward(party, id);
        db.parties.set("parties." + party + ".progression.projects.completed",
                db.parties.getInt("parties." + party + ".progression.projects.completed", 0) + 1);
        db.parties.set("parties." + party + ".progression.projects.last-completed", id);
        db.parties.set("parties." + party + ".progression.projects.last-completed-at", System.currentTimeMillis());
        db.parties.set(root, null);
        parties.addRep(party, reward);
        progression.recordActivityForParty(party, "projects",
                plugin.getConfig().getInt("progression.identity.project-completion-weight", 50));
        parties.broadcastParty(party, parties.prefix() + " &a&lCDRJOBS PROJECT SELESAI! &f"
                + progression.projectName(id) + " &8| &b+" + reward + " Party XP");
    }

    private double projectProgressMultiplier(String party, UUID contributor, String profession) {
        int skillBonus = 0;
        for (String node : progression.skillNodeIds()) {
            if (!progression.hasSkill(party, node)) continue;
            String base = "progression.skill-tree.nodes." + node + ".effects.";
            skillBonus += plugin.getConfig().getInt(base + "all-project-progress-bonus-percent", 0);
            if (profession.equals("HUNTER")) skillBonus += plugin.getConfig().getInt(base + "hunter-progress-bonus-percent", 0);
            if (CdrJobsBridgePolicy.resourceProfession(profession)) {
                skillBonus += plugin.getConfig().getInt(base + "resource-progress-bonus-percent", 0);
            }
        }

        int divisionBonus = 0;
        String division = progression.divisionOf(contributor);
        if (division != null && !division.equalsIgnoreCase("none")) {
            String base = "progression.divisions.types." + division + ".effects.";
            divisionBonus += plugin.getConfig().getInt(base + "all-project-progress-bonus-percent", 0);
            if (profession.equals("HUNTER")) divisionBonus += plugin.getConfig().getInt(base + "hunter-progress-bonus-percent", 0);
            if (CdrJobsBridgePolicy.resourceProfession(profession)) {
                divisionBonus += plugin.getConfig().getInt(base + "resource-progress-bonus-percent", 0);
            }
        }

        double multiplier = (1.0D + Math.max(0, skillBonus) / 100.0D)
                * (1.0D + Math.max(0, divisionBonus) / 100.0D);
        if (convergenceActive(party)) {
            multiplier *= 1.0D + Math.max(0, integer("five-paths-convergence.project-progress-bonus-percent", 10)) / 100.0D;
        }
        return multiplier;
    }

    private int effectiveProjectReward(String party, String id) {
        int bonus = 0;
        for (String node : progression.skillNodeIds()) {
            if (progression.hasSkill(party, node)) {
                bonus += plugin.getConfig().getInt(
                        "progression.skill-tree.nodes." + node + ".effects.project-xp-bonus-percent", 0);
            }
        }
        return (int) Math.round(progression.projectReward(id) * (1.0D + Math.max(0, bonus) / 100.0D));
    }

    private void ensureDefaults() {
        boolean changed = false;
        changed |= setDefault("quests.lumberjack.goal", 400);
        changed |= setDefault("quests.lumberjack.reputation", 90);
        changed |= setDefault("quests.fisher.goal", 120);
        changed |= setDefault("quests.fisher.reputation", 100);
        changed |= setDefault("daily-missions.lumberjack.goal", 120);
        changed |= setDefault("daily-missions.lumberjack.party-xp", 40);
        changed |= setDefault("daily-missions.fisher.goal", 25);
        changed |= setDefault("daily-missions.fisher.party-xp", 45);

        if (bool("projects.auto-register-defaults", true)) {
            ConfigurationSection defaults = settings.getConfigurationSection("projects.defaults");
            if (defaults != null) {
                for (String id : defaults.getKeys(false)) {
                    String source = "projects.defaults." + id + ".";
                    String target = "progression.projects.definitions." + id + ".";
                    if (plugin.getConfig().contains(target + "display-name")) continue;
                    plugin.getConfig().set(target + "display-name", settings.getString(source + "display-name", id));
                    plugin.getConfig().set(target + "type", settings.getString(source + "type", "cdr_miner"));
                    plugin.getConfig().set(target + "goal", Math.max(1, settings.getInt(source + "goal", 100)));
                    plugin.getConfig().set(target + "party-xp", Math.max(0, settings.getInt(source + "party-xp", 100)));
                    changed = true;
                }
            }
        }
        if (changed) {
            plugin.saveConfig();
            plugin.getLogger().info("CdrJobs integration defaults merged into config.yml without overwriting custom values.");
        }
    }

    private boolean setDefault(String path, Object value) {
        if (plugin.getConfig().contains(path)) return false;
        plugin.getConfig().set(path, value);
        return true;
    }

    private void syncWeeklyResetMarker() {
        String source = db.parties.getString("meta.last-weekly-reset", "");
        String markerPath = "meta.cdrjobs-integration.last-seen-weekly-reset";
        String seen = db.parties.getString(markerPath);
        if (seen == null) {
            db.parties.set(markerPath, source);
            plugin.saveDataSoon();
            return;
        }
        if (Objects.equals(source, seen)) return;

        ConfigurationSection section = db.parties.getConfigurationSection("parties");
        if (section != null) {
            for (String party : section.getKeys(false)) {
                for (String type : List.of("lumberjack", "fisher")) {
                    String root = "parties." + party + ".quests." + type;
                    db.parties.set(root + ".progress", 0);
                    db.parties.set(root + ".completed", false);
                    db.parties.set(root + ".completed-at", null);
                }
                ConfigurationSection members = db.parties.getConfigurationSection("parties." + party + ".members");
                if (members != null) {
                    for (String member : members.getKeys(false)) {
                        db.parties.set("parties." + party + ".members." + member + ".contribution.lumberjack", 0);
                        db.parties.set("parties." + party + ".members." + member + ".contribution.fisher", 0);
                    }
                }
            }
        }
        db.parties.set(markerPath, source);
        plugin.saveDataSoon();
    }

    private MemberProfile memberProfile(UUID uuid) {
        long now = System.currentTimeMillis();
        long ttl = Math.max(1000L, longValue("profession-roster.cache-millis", 10000L));
        CachedMemberProfile cached = profileCache.get(uuid);
        if (cached != null && cached.expiresAt() > now) return cached.profile();
        MemberProfile loaded = loadMemberProfile(uuid);
        if (profileCache.size() > 512) profileCache.clear();
        profileCache.put(uuid, new CachedMemberProfile(loaded, now + ttl));
        return loaded;
    }

    private MemberProfile loadMemberProfile(UUID uuid) {
        if (!active || api == null || getPlayerSnapshot == null) return MemberProfile.empty(uuid);
        try {
            Object snapshot = getPlayerSnapshot.invoke(api, uuid);
            Object rawMap = snapshot.getClass().getMethod("professions").invoke(snapshot);
            if (!(rawMap instanceof Map<?, ?> professions)) return MemberProfile.empty(uuid);

            Map<String, PathProgress> progress = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : professions.entrySet()) {
                String profession = CdrJobsBridgePolicy.normalizeProfession(String.valueOf(entry.getKey()));
                if (!CdrJobsBridgePolicy.PATHS.contains(profession) || entry.getValue() == null) continue;
                Object value = entry.getValue();
                int level = ((Number) value.getClass().getMethod("level").invoke(value)).intValue();
                Object mastery = value.getClass().getMethod("mastery").invoke(value);
                int masteryTier = mastery == null ? 0
                        : ((Number) mastery.getClass().getMethod("tier").invoke(mastery)).intValue();
                progress.put(profession, new PathProgress(level, masteryTier));
            }
            return new MemberProfile(uuid, Map.copyOf(progress));
        } catch (Throwable throwable) {
            plugin.getLogger().fine("CdrJobs profile read failed for " + uuid + ": " + throwable.getMessage());
            return MemberProfile.empty(uuid);
        }
    }

    public Map<String, Integer> specialistCounts(String party) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String path : CdrJobsBridgePolicy.PATHS) counts.put(path, 0);
        for (UUID member : parties.members(party)) {
            for (String path : eligibleSpecialistPaths(memberProfile(member))) {
                counts.computeIfPresent(path, (ignored, value) -> value + 1);
            }
        }
        return Map.copyOf(counts);
    }

    public boolean convergenceActive(String party) {
        if (!active || !bool("five-paths-convergence.enabled", true) || party == null || !parties.exists(party)) return false;
        long now = System.currentTimeMillis();
        long ttl = Math.max(1000L, longValue("five-paths-convergence.cache-millis", 10000L));
        CachedConvergence cached = convergenceCache.get(party);
        if (cached != null && cached.expiresAt() > now) return cached.active();

        Map<UUID, Collection<String>> eligible = new LinkedHashMap<>();
        Set<String> union = new HashSet<>();
        for (UUID member : parties.members(party)) {
            Set<String> paths = eligibleSpecialistPaths(memberProfile(member));
            eligible.put(member, paths);
            union.addAll(paths);
        }
        boolean result = bool("five-paths-convergence.require-distinct-members", true)
                ? CdrJobsBridgePolicy.hasDistinctFivePathCoverage(eligible)
                : union.containsAll(CdrJobsBridgePolicy.PATHS);
        convergenceCache.put(party, new CachedConvergence(result, now + ttl));
        return result;
    }

    private Set<String> eligibleSpecialistPaths(MemberProfile profile) {
        int minLevel = Math.max(1, integer("profession-roster.specialist-min-level", 50));
        int minMastery = Math.max(0, integer("profession-roster.specialist-min-mastery-tier", 0));
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, PathProgress> entry : profile.paths().entrySet()) {
            if (entry.getValue().level() >= minLevel && entry.getValue().masteryTier() >= minMastery) out.add(entry.getKey());
        }
        return out;
    }

    private void showProfessions(Player player) {
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) {
            player.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party."));
            return;
        }
        if (!active) {
            player.sendMessage(parties.prefix() + Util.color(" &eCdrJobs integration tidak aktif. &7Status: &f" + health));
            return;
        }

        Map<String, Integer> counts = specialistCounts(party);
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&b&lFIVE PATHS ROSTER &8- &f" + parties.display(party)));
        player.sendMessage(Util.color("&7Specialist gate: &fLv." + Math.max(1, integer("profession-roster.specialist-min-level", 50))
                + " &8| &7Mastery: &f" + Math.max(0, integer("profession-roster.specialist-min-mastery-tier", 0))));
        player.sendMessage(Util.color("&7Coverage: &b" + coverageLine(counts)));
        player.sendMessage(Util.color("&7Five Paths Convergence: " + (convergenceActive(party) ? "&aACTIVE" : "&cLOCKED")
                + " &8| &7Project bonus: &b+" + Math.max(0, integer("five-paths-convergence.project-progress-bonus-percent", 10)) + "%"));

        for (UUID member : parties.members(party)) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member);
            String name = offline.getName() == null ? member.toString().substring(0, 8) : offline.getName();
            MemberProfile profile = memberProfile(member);
            Set<String> specialist = eligibleSpecialistPaths(profile);
            player.sendMessage(Util.color("&7- &f" + name + " &8| &b" + profile.highestDisplay()
                    + " &8| &7Specialist: &f" + (specialist.isEmpty() ? "-" : String.join(", ", specialist))));
        }
    }

    private void showConvergence(Player player) {
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) {
            player.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party."));
            return;
        }
        Map<String, Integer> counts = active ? specialistCounts(party) : Collections.emptyMap();
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&d&lFIVE PATHS CONVERGENCE"));
        player.sendMessage(Util.color("&7CdrJobs: " + (active ? "&aCONNECTED &8(API v" + apiVersion + ")" : "&c" + health)));
        if (active) {
            player.sendMessage(Util.color("&7Coverage: &f" + coverageLine(counts)));
            player.sendMessage(Util.color("&7Distinct members required: &f" + bool("five-paths-convergence.require-distinct-members", true)));
            player.sendMessage(Util.color("&7State: " + (convergenceActive(party) ? "&aACTIVE" : "&cLOCKED")));
        }
    }

    private void showStatus(Player player) {
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&b&lCDRJOBS INTEGRATION"));
        player.sendMessage(Util.color("&7Health: &f" + health));
        player.sendMessage(Util.color("&7Connected: &f" + active));
        player.sendMessage(Util.color("&7CdrJobs: &f" + sourceVersion + " &8| &7API: &f" + apiVersion));
        player.sendMessage(Util.color("&7Weekly authoritative: &f" + authoritativeWeekly(plugin)));
        player.sendMessage(Util.color("&7Daily authoritative: &f" + authoritativeDaily(plugin)));
        player.sendMessage(Util.color("&7Cdr Project feed: &f" + bool("activity.feed-party-projects", true)));
    }

    private String coverageLine(Map<String, Integer> counts) {
        List<String> values = new ArrayList<>();
        for (String path : CdrJobsBridgePolicy.PATHS) values.add(shortPath(path) + " " + counts.getOrDefault(path, 0));
        return String.join(" &8• &b", values);
    }

    private String shortPath(String path) {
        return switch (path) {
            case "LUMBERJACK" -> "Lumber";
            default -> path.substring(0, 1) + path.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] split = raw.substring(1).trim().split("\\s+");
        if (split.length < 2) return;
        String root = split[0].toLowerCase(Locale.ROOT);
        if (!root.equals("party") && !root.equals("p") && !root.equals("parties")) return;
        String sub = split[1].toLowerCase(Locale.ROOT);
        if (!Set.of("professions", "profession", "jobs", "convergence", "cdrjobs").contains(sub)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        switch (sub) {
            case "professions", "profession", "jobs" -> showProfessions(player);
            case "convergence" -> showConvergence(player);
            case "cdrjobs" -> {
                if (split.length >= 3 && split[2].equalsIgnoreCase("reload")) {
                    if (!player.hasPermission("menkiestesparty.admin")) {
                        player.sendMessage(parties.prefix() + Util.color(" &cKamu tidak punya izin admin."));
                        return;
                    }
                    loadSettings();
                    ensureDefaults();
                    connect();
                    player.sendMessage(parties.prefix() + Util.color(" &aCdrJobs integration direload. &7Status: &f" + health));
                    return;
                }
                showStatus(player);
            }
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) return;
        String raw = buffer.substring(1);
        boolean trailing = raw.endsWith(" ");
        String[] split = raw.trim().isEmpty() ? new String[0] : raw.trim().split("\\s+");
        if (split.length == 0) return;
        String root = split[0].toLowerCase(Locale.ROOT);
        if (!root.equals("party") && !root.equals("p") && !root.equals("parties")) return;

        List<String> completions = new ArrayList<>(event.getCompletions());
        if (split.length == 1 || (split.length == 2 && !trailing)) {
            String typed = split.length == 2 ? split[1].toLowerCase(Locale.ROOT) : "";
            for (String value : List.of("professions", "convergence", "cdrjobs")) {
                if (value.startsWith(typed) && completions.stream().noneMatch(x -> x.equalsIgnoreCase(value))) completions.add(value);
            }
            event.setCompletions(completions);
            return;
        }
        if (split.length == 2 && trailing && split[1].equalsIgnoreCase("cdrjobs")
                && event.getSender() instanceof Player player && player.hasPermission("menkiestesparty.admin")) {
            event.setCompletions(List.of("reload"));
        }
    }

    private record PathProgress(int level, int masteryTier) {}

    private record MemberProfile(UUID uuid, Map<String, PathProgress> paths) {
        static MemberProfile empty(UUID uuid) { return new MemberProfile(uuid, Map.of()); }

        String highestDisplay() {
            if (paths.isEmpty()) return "No data";
            int max = paths.values().stream().mapToInt(PathProgress::level).max().orElse(0);
            List<String> top = paths.entrySet().stream()
                    .filter(entry -> entry.getValue().level() == max)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            return String.join("/", top) + " Lv." + max;
        }
    }

    private record CachedMemberProfile(MemberProfile profile, long expiresAt) {}
    private record CachedConvergence(boolean active, long expiresAt) {}
}
