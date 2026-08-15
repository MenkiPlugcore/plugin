package store.menkiestes.menkiafk.listener;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import store.menkiestes.menkiafk.MenkiAfkPlugin;

import java.util.Arrays;

/**
 * Ensures /afk belongs to MENKIAFK even when EssentialsX registers the same label.
 * Namespaced commands such as /essentials:afk are intentionally untouched.
 */
public final class AfkCommandOverrideListener implements Listener {
    private final MenkiAfkPlugin plugin;

    public AfkCommandOverrideListener(MenkiAfkPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("manual-afk.override-command-conflict", true)) return;
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;

        String[] split = raw.substring(1).trim().split("\\s+");
        if (split.length == 0 || !split[0].equalsIgnoreCase("afk")) return;

        PluginCommand command = plugin.getCommand("afk");
        if (command == null) return;

        event.setCancelled(true);
        String[] args = split.length <= 1 ? new String[0] : Arrays.copyOfRange(split, 1, split.length);
        command.execute(event.getPlayer(), "afk", args);
    }
}
