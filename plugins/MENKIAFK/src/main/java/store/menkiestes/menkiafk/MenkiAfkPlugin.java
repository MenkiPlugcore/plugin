package store.menkiestes.menkiafk;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import store.menkiestes.menkiafk.afk.AfkManager;
import store.menkiestes.menkiafk.command.AfkCheckCommand;
import store.menkiestes.menkiafk.command.AfkCommand;
import store.menkiestes.menkiafk.command.AfkStatsCommand;
import store.menkiestes.menkiafk.command.AfkTopCommand;
import store.menkiestes.menkiafk.command.MenkiAfkCommand;
import store.menkiestes.menkiafk.listener.ActivityListener;
import store.menkiestes.menkiafk.listener.AfkCommandOverrideListener;
import store.menkiestes.menkiafk.listener.ConnectionListener;
import store.menkiestes.menkiafk.stats.StatsManager;

import java.util.Objects;

public final class MenkiAfkPlugin extends JavaPlugin {
    private AfkManager afkManager;
    private StatsManager statsManager;
    private BukkitTask autoAfkTask;
    private BukkitTask statsSaveTask;
    private boolean placeholderApiHooked;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        statsManager = new StatsManager(this);
        afkManager = new AfkManager(this, statsManager);

        registerCommands();
        getServer().getPluginManager().registerEvents(new AfkCommandOverrideListener(this), this);
        getServer().getPluginManager().registerEvents(new ActivityListener(this, afkManager), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(afkManager), this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            afkManager.initializePlayer(player);
        }

        hookPlaceholderApi();
        restartAutoAfkTask();
        restartStatsSaveTask();

        getLogger().info("MENKIAFK v" + getDescription().getVersion()
                + " aktif. Universal API baseline: 1.21.11 | Java bytecode: 21"
                + " | AFK sessions: RAM | Persistent stats: stats.yml.");
    }

    @Override
    public void onDisable() {
        if (autoAfkTask != null) autoAfkTask.cancel();
        if (statsSaveTask != null) statsSaveTask.cancel();
        if (afkManager != null) afkManager.shutdown();
        if (statsManager != null) statsManager.saveNow();
        getLogger().info("MENKIAFK dinonaktifkan. Statistik AFK telah disimpan.");
    }

    private void registerCommands() {
        PluginCommand afk = Objects.requireNonNull(getCommand("afk"), "Command /afk tidak terdaftar");
        afk.setExecutor(new AfkCommand(this, afkManager));

        PluginCommand afkCheck = Objects.requireNonNull(getCommand("afkcheck"), "Command /afkcheck tidak terdaftar");
        afkCheck.setExecutor(new AfkCheckCommand(this, afkManager));

        PluginCommand afkStats = Objects.requireNonNull(getCommand("afkstats"), "Command /afkstats tidak terdaftar");
        afkStats.setExecutor(new AfkStatsCommand(this, statsManager));

        PluginCommand afkTop = Objects.requireNonNull(getCommand("afktop"), "Command /afktop tidak terdaftar");
        AfkTopCommand topExecutor = new AfkTopCommand(this, statsManager);
        afkTop.setExecutor(topExecutor);
        afkTop.setTabCompleter(topExecutor);

        PluginCommand admin = Objects.requireNonNull(getCommand("menkiafk"), "Command /menkiafk tidak terdaftar");
        MenkiAfkCommand adminExecutor = new MenkiAfkCommand(this, afkManager, statsManager);
        admin.setExecutor(adminExecutor);
        admin.setTabCompleter(adminExecutor);
    }

    private void hookPlaceholderApi() {
        placeholderApiHooked = false;
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI tidak ditemukan. Placeholder MENKIAFK dinonaktifkan, core tetap berjalan.");
            return;
        }
        try {
            // Reflection keeps PlaceholderAPI truly optional: its classes are never resolved when PAPI is absent.
            Class<?> expansionClass = Class.forName("store.menkiestes.menkiafk.placeholder.MenkiAfkExpansion");
            Object expansion = expansionClass
                    .getConstructor(MenkiAfkPlugin.class, AfkManager.class, StatsManager.class)
                    .newInstance(this, afkManager, statsManager);
            Object registered = expansionClass.getMethod("register").invoke(expansion);
            placeholderApiHooked = Boolean.TRUE.equals(registered);
            getLogger().info(placeholderApiHooked
                    ? "PlaceholderAPI hook aktif untuk status AFK dan statistik."
                    : "PlaceholderAPI ditemukan tetapi expansion MENKIAFK gagal diregistrasi.");
        } catch (Throwable throwable) {
            placeholderApiHooked = false;
            getLogger().warning("Gagal hook PlaceholderAPI: " + throwable.getMessage());
        }
    }

    private void restartAutoAfkTask() {
        if (autoAfkTask != null) autoAfkTask.cancel();
        long intervalSeconds = Math.max(5L, getConfig().getLong("auto-afk.check-interval-seconds", 20L));
        long ticks = intervalSeconds * 20L;
        autoAfkTask = getServer().getScheduler().runTaskTimer(this, afkManager::checkAutoAfk, ticks, ticks);
    }

    private void restartStatsSaveTask() {
        if (statsSaveTask != null) statsSaveTask.cancel();
        long intervalSeconds = Math.max(30L, getConfig().getLong("stats.autosave-seconds", 300L));
        long ticks = intervalSeconds * 20L;
        statsSaveTask = getServer().getScheduler().runTaskTimer(this, statsManager::saveIfNeeded, ticks, ticks);
    }

    public void reloadPluginConfig() {
        reloadConfig();
        statsManager.reloadSettings();
        restartAutoAfkTask();
        restartStatsSaveTask();
    }

    public boolean isPlaceholderApiHooked() {
        return placeholderApiHooked;
    }
}
