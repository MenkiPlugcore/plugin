package store.moonsign.menu;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import store.moonsign.menu.command.MenuCommand;
import store.moonsign.menu.command.TeleportCommands;
import store.moonsign.menu.form.BedrockFormService;
import store.moonsign.menu.gui.JavaMenuService;
import store.moonsign.menu.item.MemberBookService;
import store.moonsign.menu.tp.TeleportRequestManager;
import store.moonsign.menu.tp.ToggleStore;
import store.moonsign.menu.util.Colors;

import java.util.Objects;

public final class MoonSignMenuPlugin extends JavaPlugin implements Listener {
    private TeleportRequestManager requestManager;
    private BedrockFormService formService;
    private JavaMenuService javaMenuService;
    private MemberBookService memberBookService;
    private NamespacedKey playerKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();
        playerKey = new NamespacedKey(this, "target-player");

        ToggleStore toggleStore = new ToggleStore(this);
        requestManager = new TeleportRequestManager(this, toggleStore);
        javaMenuService = new JavaMenuService(this);
        memberBookService = new MemberBookService(this);

        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                formService = new BedrockFormService(this);
                getLogger().info("Floodgate detected: native Bedrock forms enabled.");
            } catch (Throwable throwable) {
                formService = null;
                getLogger().warning("Floodgate was detected but Forms could not initialize: " + throwable.getMessage());
            }
        } else {
            getLogger().info("Floodgate not detected: Java inventory fallback only.");
        }

        registerCommands();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(javaMenuService, this);
        Bukkit.getPluginManager().registerEvents(memberBookService, this);
        memberBookService.giveToOnlinePlayers();
        getLogger().info("MoonSignMenu v1.1.0 enabled.");
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("menu")).setExecutor(new MenuCommand(this));
        TeleportCommands tp = new TeleportCommands(this);
        for (String commandName : new String[]{"tpa", "tpahere", "tpaccept", "tpdeny", "tptoggle"}) {
            PluginCommand command = Objects.requireNonNull(getCommand(commandName));
            command.setExecutor(tp);
            command.setTabCompleter(tp);
        }
    }

    public void openMenu(Player player) {
        if (formService != null && formService.isBedrock(player)) {
            formService.showMainMenu(player);
        } else {
            javaMenuService.showMain(player);
        }
    }

    public TeleportRequestManager requests() {
        return requestManager;
    }

    public BedrockFormService forms() {
        return formService;
    }

    public JavaMenuService javaMenus() {
        return javaMenuService;
    }

    public MemberBookService memberBook() {
        return memberBookService;
    }

    public NamespacedKey playerKey() {
        return playerKey;
    }

    public void executeMenuAction(Player player, String actionKey) {
        String command = getConfig().getString("menu-actions." + actionKey, "");
        if (command == null || command.isBlank()) {
            message(player, "action-disabled");
            return;
        }
        player.performCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    public void message(Player player, String key, String... replacements) {
        String prefix = getConfig().getString("prefix", "&8[&dMOONSIGN&8] &r");
        String raw = getConfig().getString("messages." + key, "&cMissing message: " + key);
        if (raw == null) raw = "";
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        player.sendMessage(Colors.legacy((prefix == null ? "" : prefix) + raw));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requestManager.removeRequestsFor(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        requestManager.handleWorldChange(event.getPlayer());
    }
}
