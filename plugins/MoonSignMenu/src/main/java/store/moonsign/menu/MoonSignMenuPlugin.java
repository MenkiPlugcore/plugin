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
import store.moonsign.menu.menu.MenuConfigService;
import store.moonsign.menu.menu.MenuConfigService.MenuButton;
import store.moonsign.menu.tp.TeleportRequestManager;
import store.moonsign.menu.tp.ToggleStore;
import store.moonsign.menu.util.Colors;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class MoonSignMenuPlugin extends JavaPlugin implements Listener {
    private TeleportRequestManager requestManager;
    private BedrockFormService formService;
    private JavaMenuService javaMenuService;
    private MemberBookService memberBookService;
    private MenuConfigService menuConfigService;
    private NamespacedKey playerKey;
    private NamespacedKey buttonKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateAndMergeConfig();
        getDataFolder().mkdirs();
        playerKey = new NamespacedKey(this, "target-player");
        buttonKey = new NamespacedKey(this, "menu-button");

        menuConfigService = new MenuConfigService(this);
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
        getLogger().info("MoonSignMenu v1.2.0 enabled.");
    }

    private void migrateAndMergeConfig() {
        boolean hadDynamicMenu = getConfig().isConfigurationSection("menu.main.buttons");
        Map<String, String> legacyCommands = new HashMap<>();
        Map<String, String> legacyIcons = new HashMap<>();
        String[] legacyKeys = {"warp", "pwarp", "sethome", "land", "transfer", "bank", "team", "shop", "playershop", "report", "barter"};

        if (!hadDynamicMenu) {
            for (String key : legacyKeys) {
                String command = getConfig().getString("menu-actions." + key);
                if (command != null) legacyCommands.put(key, command);
                String icon = getConfig().getString("bedrock-icons." + key);
                if (icon != null) legacyIcons.put(key, icon);
            }
        }

        getConfig().options().copyDefaults(true);
        if (!hadDynamicMenu) {
            for (Map.Entry<String, String> entry : legacyCommands.entrySet()) {
                getConfig().set("menu.main.buttons." + entry.getKey() + ".command", entry.getValue());
            }
            for (Map.Entry<String, String> entry : legacyIcons.entrySet()) {
                getConfig().set("menu.main.buttons." + entry.getKey() + ".icon", entry.getValue());
            }
        }
        saveConfig();
    }

    private void registerCommands() {
        MenuCommand menu = new MenuCommand(this);
        PluginCommand menuCommand = Objects.requireNonNull(getCommand("menu"));
        menuCommand.setExecutor(menu);
        menuCommand.setTabCompleter(menu);

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

    public void reloadMoonSignConfig() {
        reloadConfig();
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

    public MenuConfigService menus() {
        return menuConfigService;
    }

    public NamespacedKey playerKey() {
        return playerKey;
    }

    public NamespacedKey buttonKey() {
        return buttonKey;
    }

    public void executeMenuCommand(Player player, MenuButton button) {
        if (!menuConfigService.canUse(player, button)) {
            message(player, "no-permission");
            return;
        }
        String command = button.command();
        if (command == null || command.isBlank()) {
            message(player, "action-disabled");
            return;
        }

        command = command
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%world%", player.getWorld().getName());
        if (command.startsWith("/")) command = command.substring(1);

        String executor = button.executor() == null ? "player" : button.executor().trim();
        if (executor.equalsIgnoreCase("console")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            player.performCommand(command);
        }
    }

    public String formatMenuText(String value, Player player) {
        if (value == null) return "";
        return Colors.legacy(value
                .replace("%player%", player.getName())
                .replace("%world%", player.getWorld().getName()));
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
