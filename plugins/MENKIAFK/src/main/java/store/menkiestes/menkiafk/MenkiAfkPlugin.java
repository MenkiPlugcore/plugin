package store.menkiestes.menkiafk;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import store.menkiestes.menkiafk.afk.AfkManager;
import store.menkiestes.menkiafk.command.AfkCheckCommand;
import store.menkiestes.menkiafk.command.AfkCommand;
import store.menkiestes.menkiafk.command.MenkiAfkCommand;
import store.menkiestes.menkiafk.listener.ActivityListener;
import store.menkiestes.menkiafk.listener.AfkCommandOverrideListener;
import store.menkiestes.menkiafk.listener.ConnectionListener;

import java.util.Objects;

public final class MenkiAfkPlugin extends JavaPlugin {
    private AfkManager afkManager;
    private BukkitTask autoAfkTask;
    private boolean placeholderApiHooked;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        afkManager = new AfkManager(this);

        registerCommands();
        getServer().getPluginManager().registerEvents(new AfkCommandOverrideListener(this), this);
        getServer().getPluginManager().registerEvents(new ActivityListener(this, afkManager), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(afkManager), this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            afkManager.initializePlayer(player);
        }

        hookPlaceholderApi();
        restartAutoAfkTask();

        getLogger().info("MENKIAFK v" + getDescription().getVersion() + " aktif. Universal API baseline: 1.21.11 | Java bytecode: 21 | Runtime storage: RAM only.");
    }

    @Override
    public void onDisable() {
        if (autoAfkTask != null) autoAfkTask.cancel();
        getLogger().info("MENKIAFK dinonaktifkan. Runtime AFK dibersihkan otomatis.");
    }

    private void registerCommands() {
        PluginCommand afk = Objects.requireNonNull(getCommand("afk"), "Command /afk tidak terdaftar");
        afk.setExecutor(new AfkCommand(this, afkManager));

        PluginCommand afkCheck = Objects.requireNonNull(getCommand("afkcheck"), "Command /afkcheck tidak terdaftar");
        afkCheck.setExecutor(new AfkCheckCommand(this, afkManager));

        PluginCommand admin = Objects.requireNonNull(getCommand("menkiafk"), "Command /menkiafk tidak terdaftar");
        MenkiAfkCommand adminExecutor = new MenkiAfkCommand(this, afkManager);
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
                    .getConstructor(MenkiAfkPlugin.class, AfkManager.class)
                    .newInstance(this, afkManager);
            Object registered = expansionClass.getMethod("register").invoke(expansion);
            placeholderApiHooked = Boolean.TRUE.equals(registered);
            getLogger().info(placeholderApiHooked
                    ? "PlaceholderAPI hook aktif: %menkiafk_status%, %menkiafk_reason%, %menkiafk_time%, %menkiafk_type%"
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

    public void reloadPluginConfig() {
        reloadConfig();
        restartAutoAfkTask();
    }

    public boolean isPlaceholderApiHooked() {
        return placeholderApiHooked;
    }
}
