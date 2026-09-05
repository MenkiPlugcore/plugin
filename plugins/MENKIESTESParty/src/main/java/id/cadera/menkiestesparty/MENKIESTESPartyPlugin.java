package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MENKIESTESPartyPlugin extends JavaPlugin {
    private StorageBundle storage;
    private PartyService parties;
    private WarManager war;
    private SeasonManager season;
    private RewardHallManager hall;
    private boolean dirty;

    @Override public void onEnable() {
        saveDefaultConfig();
        this.storage = new StorageBundle(this);
        this.parties = new PartyService(this, storage);
        this.war = new WarManager(this, parties, storage);
        this.season = new SeasonManager(this, parties, storage);
        this.hall = new RewardHallManager(this, parties, storage);

        PartyCommand executor = new PartyCommand(this, parties);
        for (String cmdName : new String[]{"party","pchat","partywar","partyseason","partyhall"}) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) { cmd.setExecutor(executor); cmd.setTabCompleter(executor); }
        }
        Bukkit.getPluginManager().registerEvents(new PartyListener(this, parties, war), this);

        Bukkit.getScheduler().runTaskTimer(this, () -> { war.tickSecond(); if (dirty) flush(); }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> { war.tickMinute(); parties.tickWeeklyReset(); }, 1200L, 1200L);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new PartyPlaceholderExpansion(this).register();
                getLogger().info("PlaceholderAPI integration enabled.");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI ditemukan tetapi hook gagal: " + t.getMessage());
            }
        }
        getLogger().info("MENKIESTESParty v" + getDescription().getVersion() + " enabled. Local YAML storage; Party War world=" + getConfig().getString("war.score-world", "world"));
    }

    @Override public void onDisable() { flush(); }

    public void saveDataSoon() { dirty = true; }
    public void flush() { if (storage != null) storage.saveAll(); dirty = false; }
    public PartyService parties() { return parties; }
    public WarManager war() { return war; }
    public SeasonManager season() { return season; }
    public RewardHallManager hall() { return hall; }

    public void reloadPluginConfig() {
        reloadConfig();
    }
}
