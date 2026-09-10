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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AfkListCommand implements CommandExecutor {
    private record Entry(String name, AfkSession session) {
    }

    private final MenkiAfkPlugin plugin;
    private final AfkManager manager;

    public AfkListCommand(MenkiAfkPlugin plugin, AfkManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("menki.afk.list")) {
            sender.sendMessage(Text.cfg(plugin, "messages.no-permission"));
            return true;
        }

        List<Entry> entries = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            AfkSession session = manager.getSession(player.getUniqueId());
            if (session != null) entries.add(new Entry(player.getName(), session));
        }

        entries.sort(Comparator.comparingLong(entry -> entry.session().startedAt()));

        sender.sendMessage(Text.cfg(plugin, "messages.afk-list-header"));
        sender.sendMessage(Text.replace(
                Text.cfg(plugin, "messages.afk-list-title"),
                "%count%", entries.size()));

        if (entries.isEmpty()) {
            sender.sendMessage(Text.cfg(plugin, "messages.afk-list-empty"));
            sender.sendMessage(Text.cfg(plugin, "messages.afk-list-header"));
            return true;
        }

        int limit = Math.max(1, Math.min(100, plugin.getConfig().getInt("afk-list.max-entries", 20)));
        int shown = Math.min(limit, entries.size());
        long now = System.currentTimeMillis();

        for (int i = 0; i < shown; i++) {
            Entry entry = entries.get(i);
            sender.sendMessage(Text.replace(
                    Text.cfg(plugin, "messages.afk-list-line"),
                    "%player%", entry.name(),
                    "%type%", entry.session().type() == store.menkiestes.menkiafk.afk.AfkType.AUTO
                            ? plugin.getConfig().getString("placeholder.auto-text", "Otomatis")
                            : plugin.getConfig().getString("placeholder.manual-text", "Manual"),
                    "%duration%", Text.duration(now - entry.session().startedAt()),
                    "%reason%", entry.session().reason()));
        }

        if (entries.size() > shown) {
            sender.sendMessage(Text.replace(
                    Text.cfg(plugin, "messages.afk-list-more"),
                    "%count%", entries.size() - shown));
        }

        sender.sendMessage(Text.cfg(plugin, "messages.afk-list-header"));
        return true;
    }
}
