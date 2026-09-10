package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class MENKIESTESPartyPlugin extends JavaPlugin {
    private StorageBundle storage;
    private MessageManager messages;
    private SchedulerCompat schedulerCompat;
    private PartyService parties;
    private ProgressionManager progression;
    private ProgressionGuiV122 progressionGui;
    private InteractionManager interactions;
    private InteractionGui interactionGui;
    private PartyStabilityManager stability;
    private AdministrationManager administration;
    private DeveloperApiManager developerApi;
    private ApiHardeningManager apiHardening;
    private WarManager war;
    private SeasonManager season;
    private RewardHallManager hall;
    private PartyManageGui partyGui;
    private boolean dirty;

    @Override public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        this.messages = new MessageManager(this);
        this.schedulerCompat = new SchedulerCompat(this);

        if (!schedulerCompat.runtimeAllowed()) {
            getLogger().severe("Folia detected. MENKIESTESParty v1.6.0 blocks Folia by default because full region-thread safety is not certified yet.");
            getLogger().severe("Use compatibility.folia.experimental=true only for controlled testing. Core data was not loaded.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (schedulerCompat.foliaDetected()) {
            getLogger().warning("Experimental Folia scheduler mode enabled. This is not production-certified in v1.6.0.");
        }

        try {
            this.storage = new StorageBundle(this);
        } catch (RuntimeException storageFailure) {
            getLogger().severe("MENKIESTESParty storage initialization failed: " + storageFailure.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.parties = new PartyService(this, storage);
        this.progression = new ProgressionManager(this, parties, storage);
        this.war = new WarManager(this, parties, storage);
        this.season = new SeasonManager(this, parties, storage);
        // Compatibility shell retained from v1.1.0. Party Hall is removed;
        // this component only owns Daily Party Missions and legacy no-op methods.
        this.hall = new RewardHallManager(this, parties, storage);
        this.interactions = new InteractionManager(this, parties, storage);
        this.administration = new AdministrationManager(this, parties, progression, interactions, storage);
        this.interactionGui = new InteractionGui(this, parties, storage, interactions);
        this.stability = new PartyStabilityManager(this, parties, storage, interactions);
        this.developerApi = new DeveloperApiManager(this, parties, progression, interactions, storage);
        this.apiHardening = new ApiHardeningManager(this, parties, progression, interactions, storage, developerApi);
        this.partyGui = new PartyManageGui(this, parties);
        this.progressionGui = new ProgressionGuiV122(this, parties, progression);

        PartyCommand executor = new PartyCommand(this, parties);
        for (String cmdName : new String[]{"party","pchat","partywar","partyseason"}) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) { cmd.setExecutor(executor); cmd.setTabCompleter(executor); }
        }

        StorageAdminCommand storageAdmin = new StorageAdminCommand(this);
        PluginCommand storageCommand = getCommand("partystorage");
        if (storageCommand != null) {
            storageCommand.setExecutor(storageAdmin);
            storageCommand.setTabCompleter(storageAdmin);
        }

        // Administration is registered before gameplay/GUI listeners so a freeze
        // can fail closed before stale interaction buttons or commands mutate data.
        Bukkit.getPluginManager().registerEvents(administration, this);
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

        boolean apiHealthy = apiHardening.startupCheck();
        if (developerApi.enabled() && !apiHealthy && apiHardening.failClosed()) {
            Bukkit.getServicesManager().unregister(MenkiPartyAPI.class, developerApi);
            getLogger().severe("Public MENKIESTESParty API service unregistered by v1.4.1 fail-closed guard. Core Party remains enabled.");
        }

        schedulerCompat.runGlobalTimer(() -> {
            war.tickSecond();
            if (dirty) flushAsync();
        }, 20L, 20L);
        schedulerCompat.runGlobalTimer(stability::tickFast, 100L, 100L);
        schedulerCompat.runGlobalTimer(storage::maintenanceTick, 200L, 200L);

        long apiScanTicks = Math.max(1L, getConfig().getLong("developer.events.scan-ticks", 20L));
        schedulerCompat.runGlobalTimer(() -> {
            if (apiHardening.enabled()) apiHardening.tick();
            else developerApi.tick();
        }, apiScanTicks, apiScanTicks);

        schedulerCompat.runGlobalTimer(() -> {
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
                + " enabled. Storage: schema=" + storage.schemaVersion()
                + ", configured=" + storage.configuredBackend()
                + ", active=" + storage.activeBackend()
                + ", async=" + storage.asyncWrites()
                + ", degraded=" + storage.degraded()
                + ", unclean-recovery=" + storage.uncleanShutdownDetected()
                + ". Scheduler=" + schedulerCompat.mode()
                + ". Progression: projects=" + progression.moduleEnabled("projects")
                + ", skills=" + progression.moduleEnabled("skill-tree")
                + ", divisions=" + progression.moduleEnabled("divisions")
                + ", identity=" + progression.moduleEnabled("identity")
                + ", progression-gui=" + progressionGui.enabled()
                + ". Interaction: contracts=" + interactions.moduleEnabled("contracts")
                + ", diplomacy=" + interactions.moduleEnabled("diplomacy")
                + ", applications=" + interactions.moduleEnabled("applications")
                + ", interaction-gui=" + interactionGui.enabled()
                + ", stability=" + stability.enabled()
                + ". Administration: enabled=" + administration.enabled()
                + ". Developer: api=" + developerApi.enabled()
                + ", api-health=" + apiHardening.healthLabel()
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

        String hardeningMarker = "migrations.developer-api-hardening-v1_4_1";
        if (!getConfig().getBoolean(hardeningMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(hardeningMarker, true);
            getLogger().info("Config migration: v1.4.1 API hardening/compatibility defaults merged into config.yml.");
        }

        String storageMarker = "migrations.storage-compatibility-v1_5_0";
        if (!getConfig().getBoolean(storageMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(storageMarker, true);
            getLogger().info("Config migration: v1.5.0 storage/compatibility defaults merged into config.yml.");
        }

        String storageHardeningMarker = "migrations.storage-hardening-v1_5_1";
        if (!getConfig().getBoolean(storageHardeningMarker, false)) {
            getConfig().options().copyDefaults(true);
            getConfig().set(storageHardeningMarker, true);
            getLogger().info("Config migration: v1.5.1 storage hardening marker applied.");
        }

        String administrationMarker = "migrations.administration-v1_6_0";
        if (!getConfig().getBoolean(administrationMarker, false)) {
            getConfig().set(administrationMarker, true);
            getLogger().info("Config migration: v1.6.0 Administration & Moderation marker applied.");
        }
        saveConfig();
    }

    @Override public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (storage != null) storage.close();
        dirty = false;
    }

    public void saveDataSoon() { dirty = true; }

    /** Durable synchronous flush. Required before external reward side effects. */
    public void flush() { flushDurable(); }

    public boolean flushDurable() {
        if (storage == null) return false;
        boolean success = storage.saveAllBlocking();
        if (success) dirty = false;
        return success;
    }

    private void flushAsync() {
        if (storage == null) return;
        storage.saveAllAsync();
        dirty = false;
    }

    public StorageBundle storage() { return storage; }
    public MessageManager messages() { return messages; }
    public SchedulerCompat schedulerCompat() { return schedulerCompat; }
    public PartyService parties() { return parties; }
    public ProgressionManager progression() { return progression; }
    public ProgressionGuiV122 progressionGui() { return progressionGui; }
    public InteractionManager interactions() { return interactions; }
    public InteractionGui interactionGui() { return interactionGui; }
    public PartyStabilityManager stability() { return stability; }
    public AdministrationManager administration() { return administration; }
    public DeveloperApiManager developerApi() { return developerApi; }
    public ApiHardeningManager apiHardening() { return apiHardening; }
    public WarManager war() { return war; }
    public SeasonManager season() { return season; }
    public RewardHallManager hall() { return hall; }
    public PartyManageGui partyGui() { return partyGui; }

    public void reloadPluginConfig() {
        reloadConfig();
        if (messages != null) messages.reload();
        if (administration != null) administration.reloadSettings();
        if (apiHardening != null) apiHardening.verifyCompatibility();
    }
}
