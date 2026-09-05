package id.cadera.menkiestesparty;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class StorageBundle {
    private final JavaPlugin plugin;
    private final File partiesFile;
    private final File warsFile;
    private final File seasonFile;
    private final File hallFile;
    public final YamlConfiguration parties;
    public final YamlConfiguration wars;
    public final YamlConfiguration season;
    public final YamlConfiguration hall;

    public StorageBundle(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.partiesFile = new File(plugin.getDataFolder(), "parties.yml");
        this.warsFile = new File(plugin.getDataFolder(), "wars.yml");
        this.seasonFile = new File(plugin.getDataFolder(), "season.yml");
        this.hallFile = new File(plugin.getDataFolder(), "hall.yml");
        this.parties = YamlConfiguration.loadConfiguration(partiesFile);
        this.wars = YamlConfiguration.loadConfiguration(warsFile);
        this.season = YamlConfiguration.loadConfiguration(seasonFile);
        this.hall = YamlConfiguration.loadConfiguration(hallFile);
    }

    public void saveAll() {
        try {
            parties.save(partiesFile);
            wars.save(warsFile);
            season.save(seasonFile);
            hall.save(hallFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Gagal menyimpan data Party: " + e.getMessage());
        }
    }
}
