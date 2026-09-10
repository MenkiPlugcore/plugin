package store.menkiestes.menkiafk.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.stats.StatsManager;
import store.menkiestes.menkiafk.util.Text;

import java.util.Optional;
import java.util.UUID;

public final class AfkStatsCommand implements CommandExecutor {
    private final MenkiAfkPlugin plugin;
    private final StatsManager statsManager;

    public AfkStatsCommand(MenkiAfkPlugin plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("menki.afk.stats")) {
            sender.sendMessage(Text.cfg(plugin, "messages.no-permission"));
            return true;
        }

        UUID targetId;
        String requestedName;

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.color("&7Gunakan: &f/afkstats <player>"));
                return true;
            }
            statsManager.registerPlayer(player);
            targetId = player.getUniqueId();
            requestedName = player.getName();
        } else {
            requestedName = args[0];
            Player online = Bukkit.getPlayerExact(requestedName);
            if (online != null) {
                statsManager.registerPlayer(online);
                targetId = online.getUniqueId();
                requestedName = online.getName();
            } else {
                Optional<UUID> known = statsManager.findUuidByName(requestedName);
                if (known.isEmpty()) {
                    sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.player-not-found"), "%player%", requestedName));
                    return true;
                }
                targetId = known.get();
            }
        }

        StatsManager.Snapshot stats = statsManager.snapshot(targetId);
        sender.sendMessage(Text.cfg(plugin, "messages.stats-header"));
        sender.sendMessage(Text.cfg(plugin, "messages.stats-title"));
        sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.stats-player"), "%player%", stats.name()));
        sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.stats-today"), "%time%", Text.duration(stats.todayMillis())));
        sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.stats-week"), "%time%", Text.duration(stats.weekMillis())));
        sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.stats-total"), "%time%", Text.duration(stats.totalMillis())));
        sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.stats-sessions"), "%sessions%", stats.sessions()));
        sender.sendMessage(Text.replace(
                Text.cfg(plugin, "messages.stats-types"),
                "%manual%", stats.manualSessions(),
                "%auto%", stats.autoSessions()));
        if (stats.legacySessions() > 0) {
            sender.sendMessage(Text.replace(
                    Text.cfg(plugin, "messages.stats-legacy"),
                    "%sessions%", stats.legacySessions()));
        }
        sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.stats-longest"), "%time%", Text.duration(stats.longestMillis())));
        if (stats.lastAfkAt() <= 0L) {
            sender.sendMessage(Text.cfg(plugin, "messages.stats-last-never"));
        } else {
            long elapsed = Math.max(0L, System.currentTimeMillis() - stats.lastAfkAt());
            sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.stats-last"), "%time%", Text.duration(elapsed)));
        }
        sender.sendMessage(Text.cfg(plugin, "messages.stats-header"));
        return true;
    }
}
