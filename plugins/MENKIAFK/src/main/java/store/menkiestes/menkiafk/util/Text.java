package store.menkiestes.menkiafk.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import store.menkiestes.menkiafk.MenkiAfkPlugin;


public final class Text {
    private Text() {}

    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String plainReason(String input) {
        if (input == null) return "";
        String singleLine = input.replace('\n', ' ').replace('\r', ' ').trim();
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', singleLine));
    }

    public static String coloredReason(String input) {
        if (input == null) return "";
        return color(input.replace('\n', ' ').replace('\r', ' ').trim());
    }

    public static String cfg(MenkiAfkPlugin plugin, String path) {
        String raw = plugin.getConfig().getString(path, path);
        return color(raw.replace("{@prefix}", plugin.getConfig().getString("prefix", "&8[&6AFK&8]")));
    }

    public static String replace(String source, Object... pairs) {
        if (source == null) return "";
        String out = source;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
        }
        return out;
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    public static String duration(long millis) {
        long total = Math.max(0L, millis / 1000L);
        long days = total / 86400L;
        long hours = (total % 86400L) / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;

        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append("hri ");
        if (hours > 0) out.append(hours).append("j ");
        if (minutes > 0) out.append(minutes).append("m ");
        if (seconds > 0 || out.isEmpty()) out.append(seconds).append("d");
        return out.toString().trim();
    }
}
