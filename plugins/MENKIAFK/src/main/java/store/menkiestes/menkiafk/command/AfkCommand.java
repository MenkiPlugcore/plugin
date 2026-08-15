package store.menkiestes.menkiafk.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.afk.AfkManager;
import store.menkiestes.menkiafk.util.Text;

import java.util.StringJoiner;

public final class AfkCommand implements CommandExecutor {
    private final MenkiAfkPlugin plugin;
    private final AfkManager manager;

    public AfkCommand(MenkiAfkPlugin plugin, AfkManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya untuk player.");
            return true;
        }
        if (!player.hasPermission("menki.afk")) {
            player.sendMessage(Text.cfg(plugin, "messages.no-permission"));
            return true;
        }

        if (manager.isAfk(player.getUniqueId())) {
            manager.returnFromAfk(player, true);
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Text.cfg(plugin, "messages.reason-required"));
            return true;
        }

        long remaining = manager.manualCooldownRemaining(player);
        if (remaining > 0) {
            long seconds = Math.max(1L, (remaining + 999L) / 1000L);
            player.sendMessage(Text.replace(Text.cfg(plugin, "messages.cooldown"), "%seconds%", seconds));
            return true;
        }

        StringJoiner joiner = new StringJoiner(" ");
        for (String arg : args) joiner.add(arg);
        String reason = joiner.toString().trim();

        int max = Math.max(10, plugin.getConfig().getInt("manual-afk.max-reason-length", 80));
        if (reason.length() > max) {
            player.sendMessage(Text.replace(Text.cfg(plugin, "messages.reason-too-long"), "%max%", max));
            return true;
        }

        manager.setManualAfk(player, reason);
        return true;
    }
}
