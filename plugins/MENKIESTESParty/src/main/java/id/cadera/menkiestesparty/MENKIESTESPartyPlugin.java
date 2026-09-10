package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class MENKIESTESPartyPlugin extends JavaPlugin {
    private StorageBundle storage;
    private PartyService parties;
    private ProgressionManager progression;
    private ProgressionGuiV122 progressionGui;
    private InteractionManager interactions;
    private InteractionGui interactionGui;
    private PartyStabilityManager stability;
    private DeveloperApiManager developerApi;
    private WarManager war;
    private SeasonManager season;
    private RewardHallManager hall;
    private PartyManageGui partyGui;
    private boolean dirty;

    @Override public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        this.storage = new StorageBundle(this);
        this.parties = new PartyService(this, storage);
        this.progression = new ProgressionManager(this, parties, storage);
        this.war = new WarManager(this, parties, storage);
        this.season = new SeasonManager(this, parties, storage);
        // Compatibility shell retained from v1.1.0. Party Hall is removed;
        // this component only owns Daily Party Missions and legacy no-op methods.
        this.hall = new RewardHallManager(this, parties, storage);
        this.interactions = new InteractionManager(this, parties, storage);
        this.interactionGui = new InteractionGui(this, parties, storage, interactions);
        this.stability = new PartyStabilityManager(this, parties, storage, interactions);
        this.developerApi = new DeveloperApiManager(this, parties, progression, interactions, storage);
        this.partyGui = new PartyManageGui(this, parties);
        this.progressionGui = new ProgressionGuiV122(this, parties, progression);

        PartyCommand executor = new PartyCommand(this, parties);
        for (String cmdName : new String[]{"party","pchat","partywar","partyseason"}) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) { cmd.setExecutor(executor); cmd.setTabCompleter(executor); }
        }
        Bukkit.getPluginManager().registerEvents(new PartyListener(this, parties, war), this);
        Bukkit.getPluginManager().registerEvents(progression, this);
        Bukkit.getPluginManager().registerEvents(interactions, this);
        Bukkit.getPluginManager().registerEvents(interactionGui, this);
        Bukkit.getPluginManager().registerEvents(stability, this);
        Bukkit.getPluginManager().registerEvents(partyGui, this);
        Bukkit.getPluginManager().registerEvents(progressionGui, this);

        if (developerApi.enabled()) {
            Bukkit.getServicesManager().register(MenkiPartyAPI.class, developerApi, this, ServicePriority.Normal);
            getLogger().info("MENKIESTESParty API v" + developerApi.apiVersion() + " registered in Bukkit ServicesManager.");
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> { war.tickSecond(); if (dirty) flush(); }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, stability::tickFast, 100L, 100L);
        long apiScanTicks = Math.max(1L, getConfig().getLong("developer.events.scan-ticks", 20L));
        Bukkit.getScheduler().runTaskTimer(this, developerApi::tick, apiScanTicks, apiScanTicks);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            war.tickMinute();
            parties.tickWeeklyReset();
            interactions.tickMinute();
            stability.tickMinute();
        }, 1200L, 1200L);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new PartyPlaceholderExpansion(this).register();
                getLogger().info("PlaceholderAPI integration enabled.");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI ditemukan tetapi hook gagal: " + t.getMessage());
            }
        }
        getLogger().info("MENKIESTESParty v" + getDescription().getVersion()
                + " enabled. Progression: projects=" + progression.moduleEnabled("projects")
                + ", skills=" + progression.moduleEnabled("skill-tree")
                + ", divisions=" + progression.moduleEnabled("divisions")
                + ", identity=" + progression.moduleEnabled("identity")
                + ", progression-gui=" + progressionGui.enabled()
                + ". Interaction: contracts=" + interactions.moduleEnabled("contracts")
                + ", diplomacy=" + interactions.moduleEnabled("diplomacy")
                + ", applications=" + interactions.moduleEnabled("applications")
                + ", interaction-gui=" + interactionGui.enabled()
                + ", stability=" + stability.enabled()
                + ". Developer: api=" + developerApi.enabled()
                + ", vault=" + developerApi.integrationAvailable("vault"));
    }

    private void migrateConfig() {
        String oldMarker = "migrations.level1-slot-cap-v1_0_4";
        if (!getConfig().getBoolean(oldMarker, false)) {
            int current = getConfig().getInt("levels.1.slots", 5);
            if (current > 5) {
                getConfig().set("levels.1.slots", 5);
                getLogger().info("Config migration: Party Level 1 member cap changed from " + current + " to 5.");
            }
            getConfig().set(oldMarker, true);
        }

        String progressionMarker = "migrations.progression-v1_2_0";
        if (!getConfig().getBoolean(progressionMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(progressionMarker, true);
            getLogger().info("Config migration: v1.2.0 progression defaults merged into config.yml.");
        }

        String guiMarker = "migrations.progression-gui-v1_2_1";
        if (!getConfig().getBoolean(guiMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(guiMarker, true);
            getLogger().info("Config migration: v1.2.1 progression GUI defaults merged into config.yml.");
        }

        String polishMarker = "migrations.progression-gui-polish-v1_2_2";
        if (!getConfig().getBoolean(polishMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(polishMarker, true);
            getLogger().info("Config migration: v1.2.2 GUI safety/pagination defaults merged into config.yml.");
        }

        String interactionMarker = "migrations.interaction-v1_3_0";
        if (!getConfig().getBoolean(interactionMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(interactionMarker, true);
            getLogger().info("Config migration: v1.3.0 interaction/rank defaults merged into config.yml.");
        }

        String interactionGuiMarker = "migrations.interaction-gui-v1_3_1";
        if (!getConfig().getBoolean(interactionGuiMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(interactionGuiMarker, true);
            getLogger().info("Config migration: v1.3.1 Interaction GUI defaults merged into config.yml.");
        }

        String stabilityMarker = "migrations.interaction-stability-v1_3_2";
        if (!getConfig().getBoolean(stabilityMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(stabilityMarker, true);
            getLogger().info("Config migration: v1.3.2 stability/inbox defaults merged into config.yml.");
        }

        String developerMarker = "migrations.developer-api-v1_4_0";
        if (!getConfig().getBoolean(developerMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(developerMarker, true);
            getLogger().info("Config migration: v1.4.0 Developer API/integration defaults merged into config.yml.");
        }
        saveConfig();
    }

    @Override public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        flush();
    }

    public void saveDataSoon() { dirty = true; }
    public void flush() { if (storage != null) storage.saveAll(); dirty = false; }
    public PartyService parties() { return parties; }
    public ProgressionManager progression() { return progression; }
    public ProgressionGuiV122 progressionGui() { return progressionGui; }
    public InteractionManager interactions() { return interactions; }
    public InteractionGui interactionGui() { return interactionGui; }
    public PartyStabilityManager stability() { return stability; }
    public DeveloperApiManager developerApi() { return developerApi; }
    public WarManager war() { return war; }
    public SeasonManager season() { return season; }
    public RewardHallManager hall() { return hall; }
    public PartyManageGui partyGui() { return partyGui; }

    public void reloadPluginConfig() {
        reloadConfig();
    }
}
