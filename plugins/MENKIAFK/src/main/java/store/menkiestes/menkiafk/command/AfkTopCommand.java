package store.menkiestes.menkiafk.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.stats.StatsManager;
import store.menkiestes.menkiafk.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AfkTopCommand implements CommandExecutor, TabCompleter {
    private final MenkiAfkPlugin plugin;
    private final StatsManager statsManager;

    public AfkTopCommand(MenkiAfkPlugin plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("menki.afk.top")) {
            sender.sendMessage(Text.cfg(plugin, "messages.no-permission"));
            return true;
        }

        StatsManager.Metric metric = StatsManager.Metric.TOTAL;
        int page = 1;

        if (args.length >= 1) {
            Integer numericPage = parsePositiveInt(args[0]);
            if (numericPage != null) {
                page = numericPage;
            } else {
                Optional<StatsManager.Metric> parsed = StatsManager.Metric.parse(args[0]);
                if (parsed.isEmpty()) {
                    sender.sendMessage(Text.cfg(plugin, "messages.leaderboard-invalid-metric"));
                    return true;
                }
                metric = parsed.get();
            }
        }

        if (args.length >= 2) {
            Integer parsedPage = parsePositiveInt(args[1]);
            if (parsedPage == null) {
                sender.sendMessage(Text.cfg(plugin, "messages.leaderboard-invalid-page"));
                return true;
            }
            page = parsedPage;
        }

        int pageSize = Math.max(3, Math.min(20, plugin.getConfig().getInt("stats.leaderboard-page-size", 10)));
        List<StatsManager.RankedEntry> all = statsManager.leaderboard(metric);
        int totalPages = Math.max(1, (all.size() + pageSize - 1) / pageSize);

        if (page > totalPages) {
            sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.leaderboard-page-out-of-range"), "%pages%", totalPages));
            return true;
        }

        sender.sendMessage(Text.cfg(plugin, "messages.leaderboard-header"));
        sender.sendMessage(Text.replace(
                Text.cfg(plugin, "messages.leaderboard-title"),
                "%metric%", metric.displayName(),
                "%page%", page,
                "%pages%", totalPages));

        if (all.isEmpty()) {
            sender.sendMessage(Text.cfg(plugin, "messages.leaderboard-empty"));
        } else {
            int from = (page - 1) * pageSize;
            int to = Math.min(all.size(), from + pageSize);
            for (int index = from; index < to; index++) {
                StatsManager.RankedEntry ranked = all.get(index);
                long value = ranked.stats().value(metric);
                String formatted = metric == StatsManager.Metric.SESSIONS
                        ? value + " sesi"
                        : Text.duration(value);

                sender.sendMessage(Text.replace(
                        Text.cfg(plugin, "messages.leaderboard-line"),
                        "%rank%", ranked.rank(),
                        "%player%", ranked.stats().name(),
                        "%value%", formatted));
            }
        }

        sender.sendMessage(Text.cfg(plugin, "messages.leaderboard-header"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (StatsManager.Metric metric : StatsManager.Metric.values()) {
                if (metric.key().startsWith(prefix)) suggestions.add(metric.key());
            }
            return suggestions;
        }
        return List.of();
    }

    private static Integer parsePositiveInt(String input) {
        try {
            int parsed = Integer.parseInt(input);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
