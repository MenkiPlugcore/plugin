package store.menkiestes.menkiafk.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.afk.AfkManager;
import store.menkiestes.menkiafk.util.Text;

import java.util.List;

public final class MenkiAfkCommand implements CommandExecutor, TabCompleter {
    private final MenkiAfkPlugin plugin;
    private final AfkManager manager;

    public MenkiAfkCommand(MenkiAfkPlugin plugin, AfkManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("menki.afk.admin")) {
            sender.sendMessage(Text.cfg(plugin, "messages.no-permission"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Text.color("&6MENKIAFK &fv" + plugin.getDescription().getVersion()
                    + " &8| &7AFK: &f" + manager.afkCount()
                    + " &8| &7Tracked: &f" + manager.trackedPlayers()
                    + " &8| &7PAPI: &f" + (plugin.isPlaceholderApiHooked() ? "ON" : "OFF")
                    + " &8| &7Java: &f" + System.getProperty("java.specification.version", "?")));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPluginConfig();
            sender.sendMessage(Text.cfg(plugin, "messages.reload"));
            return true;
        }
        sender.sendMessage(Text.color("&7Gunakan: &f/menkiafk <reload|status>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("reload", "status");
        return List.of();
    }
}
