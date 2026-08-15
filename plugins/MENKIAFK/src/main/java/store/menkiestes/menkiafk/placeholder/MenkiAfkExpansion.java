package store.menkiestes.menkiafk.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.afk.AfkManager;

import java.util.Locale;

public final class MenkiAfkExpansion extends PlaceholderExpansion {
    private final MenkiAfkPlugin plugin;
    private final AfkManager manager;

    public MenkiAfkExpansion(MenkiAfkPlugin plugin, AfkManager manager) {
        this.plugin = plugin;
        this.manager = manager;
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
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "status" -> manager.placeholderStatus(player.getUniqueId());
            case "reason" -> manager.placeholderReason(player.getUniqueId());
            case "time" -> manager.placeholderTime(player.getUniqueId());
            case "type" -> manager.placeholderType(player.getUniqueId());
            default -> null;
        };
    }
}
