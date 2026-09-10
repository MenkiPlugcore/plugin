package id.cadera.menkiestesparty;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** v1.5.x localization/messages facade with non-destructive default merging. */
public final class MessageManager {
    private final MENKIESTESPartyPlugin plugin;
    private final File file;
    private YamlConfiguration messages;

    public MessageManager(MENKIESTESPartyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        boolean existed = file.isFile();
        if (!existed) plugin.saveResource("messages.yml", false);
        this.messages = YamlConfiguration.loadConfiguration(file);

        // Preserve custom prefixes from pre-v1.5.0 installations on first migration.
        if (!existed) {
            String legacyPrefix = plugin.getConfig().getString("prefix");
            if (legacyPrefix != null && !legacyPrefix.isBlank()) {
                messages.set("prefix", legacyPrefix);
            }
        }
        mergeDefaults();
    }

    public void reload() {
        this.messages = YamlConfiguration.loadConfiguration(file);
        mergeDefaults();
    }

    public String prefix() {
        return Util.color(messages.getString("prefix",
                plugin.getConfig().getString("prefix", "&b&lPARTY &8»&r")));
    }

    public String text(String key) {
        return text(key, Map.of());
    }

    public String text(String key, Map<String, ?> placeholders) {
        String fallback = "&cMissing message: " + key;
        String value = messages.getString(key, fallback);
        if (value == null) value = fallback;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return Util.color(value);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        sender.sendMessage(prefix() + " " + text(key, placeholders));
    }

    private void mergeDefaults() {
        try (InputStream input = plugin.getResource("messages.yml")) {
            if (input != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
                messages.options().copyDefaults(true);
            }
            save();
        } catch (IOException failure) {
            plugin.getLogger().warning("Unable to merge messages.yml defaults: " + failure.getMessage());
        }
    }

    private void save() {
        try {
            messages.save(file);
        } catch (IOException failure) {
            plugin.getLogger().warning("Unable to save messages.yml: " + failure.getMessage());
        }
    }
}
