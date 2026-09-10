package store.menkiestes.menkiafk.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.afk.AfkManager;
import store.menkiestes.menkiafk.stats.StatsManager;
import store.menkiestes.menkiafk.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class MenkiAfkCommand implements CommandExecutor, TabCompleter {
    private final MenkiAfkPlugin plugin;
    private final AfkManager manager;
    private final StatsManager statsManager;

    public MenkiAfkCommand(MenkiAfkPlugin plugin, AfkManager manager, StatsManager statsManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.statsManager = statsManager;
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
                    + " &8| &7Online tracked: &f" + manager.trackedPlayers()
                    + " &8| &7Stats profiles: &f" + statsManager.trackedPlayerCount()
                    + " &8| &7Stats I/O: " + (statsManager.isPersistenceHealthy() ? "&aOK" : "&cBLOCKED")
                    + " &8| &7PAPI: &f" + (plugin.isPlaceholderApiHooked() ? "ON" : "OFF")
                    + " &8| &7Java: &f" + System.getProperty("java.specification.version", "?")));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPluginConfig();
            sender.sendMessage(Text.cfg(plugin, "messages.reload"));
            return true;
        }

        if (args[0].equalsIgnoreCase("resetstats")) {
            if (args.length < 2) {
                sender.sendMessage(Text.color("&7Gunakan: &f/menkiafk resetstats <player>"));
                return true;
            }

            String requested = args[1];
            Player online = Bukkit.getPlayerExact(requested);
            UUID id;
            String name;
            if (online != null) {
                statsManager.registerPlayer(online);
                id = online.getUniqueId();
                name = online.getName();
            } else {
                Optional<UUID> known = statsManager.findUuidByName(requested);
                if (known.isEmpty()) {
                    sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.player-not-found"), "%player%", requested));
                    return true;
                }
                id = known.get();
                name = statsManager.snapshot(id).name();
            }

            statsManager.reset(id, System.currentTimeMillis());
            statsManager.saveNow();
            sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.stats-reset"), "%player%", name));
            return true;
        }

        sender.sendMessage(Text.color("&7Gunakan: &f/menkiafk <reload|status|resetstats>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            for (String option : List.of("reload", "status", "resetstats")) {
                if (option.startsWith(prefix)) options.add(option);
            }
            return options;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("resetstats")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
