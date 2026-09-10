package store.menkiestes.menkiafk.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.afk.AfkManager;
import store.menkiestes.menkiafk.stats.StatsManager;
import store.menkiestes.menkiafk.util.Text;

import java.util.Locale;

public final class MenkiAfkExpansion extends PlaceholderExpansion {
    private final MenkiAfkPlugin plugin;
    private final AfkManager manager;
    private final StatsManager statsManager;

    public MenkiAfkExpansion(MenkiAfkPlugin plugin, AfkManager manager, StatsManager statsManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.statsManager = statsManager;
    }

    @Override
    public String getIdentifier() {
        return "menkiafk";
    }

    @Override
    public String getAuthor() {
        return "MENKIESTES";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        StatsManager.Snapshot stats = statsManager.snapshot(player.getUniqueId());

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "status" -> manager.placeholderStatus(player.getUniqueId());
            case "reason" -> manager.placeholderReason(player.getUniqueId());
            case "time" -> manager.placeholderTime(player.getUniqueId());
            case "type" -> manager.placeholderType(player.getUniqueId());
            case "stats_today" -> Text.duration(stats.todayMillis());
            case "stats_week" -> Text.duration(stats.weekMillis());
            case "stats_total" -> Text.duration(stats.totalMillis());
            case "stats_sessions" -> String.valueOf(stats.sessions());
            case "stats_longest" -> Text.duration(stats.longestMillis());
            default -> null;
        };
    }
}
