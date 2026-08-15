package store.menkiestes.menkiafk.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.afk.AfkManager;
import store.menkiestes.menkiafk.afk.AfkSession;
import store.menkiestes.menkiafk.util.Text;

public final class AfkCheckCommand implements CommandExecutor {
    private final MenkiAfkPlugin plugin;
    private final AfkManager manager;

    public AfkCheckCommand(MenkiAfkPlugin plugin, AfkManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Gunakan /afkcheck <player> dari console.");
                return true;
            }
            target = player;
        } else {
            if (!sender.hasPermission("menki.afk.admin")) {
                sender.sendMessage(Text.cfg(plugin, "messages.no-permission"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.player-not-found"), "%player%", args[0]));
                return true;
            }
        }

        AfkSession session = manager.getSession(target.getUniqueId());
        sender.sendMessage(Text.cfg(plugin, "messages.status-header"));
        sender.sendMessage(Text.cfg(plugin, "messages.status-title"));
        sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.status-player"), "%player%", target.getName()));

        if (session == null) {
            sender.sendMessage(Text.color(Text.replace(Text.cfg(plugin, "messages.status-afk"), "%status%", "&aTidak")));
        } else {
            sender.sendMessage(Text.color(Text.replace(Text.cfg(plugin, "messages.status-afk"), "%status%", "&6Ya")));
            sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.status-type"), "%type%", manager.placeholderType(target.getUniqueId())));
            sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.status-reason"), "%reason%", session.reason()));
            sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.status-duration"), "%duration%", Text.duration(System.currentTimeMillis() - session.startedAt())));
        }
        sender.sendMessage(Text.cfg(plugin, "messages.status-header"));
        return true;
    }
}
