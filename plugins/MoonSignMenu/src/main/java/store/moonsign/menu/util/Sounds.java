package store.moonsign.menu.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import store.moonsign.menu.MoonSignMenuPlugin;

public final class Sounds {
    private Sounds() {}

    public static void play(MoonSignMenuPlugin plugin, Player player, String configPath) {
        String raw = plugin.getConfig().getString(configPath, "");
        if (raw == null || raw.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(raw.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid sound in config: " + raw);
        }
    }
}
