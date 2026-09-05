package id.cadera.menkiestesparty;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Util {
    private Util() {}

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static String strip(String s) {
        return ChatColor.stripColor(color(s));
    }

    public static String key(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).replace('.', '_').replace(' ', '_');
    }

    public static boolean validPartyName(String name, int min, int max) {
        if (name == null || name.length() < min || name.length() > max) return false;
        return name.matches("[A-Za-z0-9_]+$");
    }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && lore.length > 0) {
                List<String> lines = new ArrayList<>();
                Arrays.stream(lore).forEach(line -> lines.add(color(line)));
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static int parseInt(String value, int def, int min, int max) {
        try {
            int n = Integer.parseInt(value);
            return Math.max(min, Math.min(max, n));
        } catch (Exception ignored) {
            return def;
        }
    }
}
