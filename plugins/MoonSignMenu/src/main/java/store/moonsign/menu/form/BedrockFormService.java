package store.moonsign.menu.form;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import store.moonsign.menu.MoonSignMenuPlugin;
import store.moonsign.menu.menu.MenuConfigService.MenuButton;
import store.moonsign.menu.menu.MenuConfigService.MenuDefinition;
import store.moonsign.menu.tp.TeleportMode;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BedrockFormService {
    private final MoonSignMenuPlugin plugin;

    public BedrockFormService(MoonSignMenuPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBedrock(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void showMainMenu(Player player) {
        showConfiguredMenu(player, "main");
    }

    public void showConfiguredMenu(Player player, String menuId) {
        MenuDefinition menu = plugin.menus().getMenu(menuId);
        if (menu == null) {
            plugin.message(player, "menu-not-found", "%menu%", menuId == null ? "main" : menuId);
            if (!"main".equalsIgnoreCase(menuId)) showMainMenu(player);
            return;
        }

        List<MenuButton> buttons = plugin.menus().visibleButtons(menu, player);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(plugin.formatMenuText(menu.title(), player))
                .content(plugin.formatMenuText(menu.content(), player));

        for (MenuButton button : buttons) {
            addConfiguredButton(builder, plugin.formatMenuText(button.name(), player), button.icon());
        }
        boolean hasBack = !menu.id().equals("main");
        if (hasBack) addButton(builder, "Kembali", "back", "textures/items/arrow");

        int backIndex = buttons.size();
        SimpleForm form = builder.validResultHandler(response -> sync(() -> {
                    if (!player.isOnline()) return;
                    int selected = response.clickedButtonId();
                    if (hasBack && selected == backIndex) {
                        showConfiguredMenu(player, menu.backMenu());
                        return;
                    }
                    if (selected < 0 || selected >= buttons.size()) return;
                    handleConfiguredButton(player, menu, buttons.get(selected));
                }))
                .build();
        send(player, form);
    }

    private void handleConfiguredButton(Player player, MenuDefinition menu, MenuButton button) {
        if (!plugin.menus().canUse(player, button)) {
            plugin.message(player, "no-permission");
            return;
        }

        switch (button.type().toLowerCase(Locale.ROOT)) {
            case "command" -> plugin.executeMenuCommand(player, button);
            case "teleport" -> showTeleportForm(player, menu.id());
            case "submenu" -> {
                if (button.submenu() == null || button.submenu().isBlank()) {
                    plugin.message(player, "menu-not-found", "%menu%", button.key());
                    return;
                }
                showConfiguredMenu(player, button.submenu());
            }
            case "close" -> { }
            default -> plugin.message(player, "invalid-button-type", "%type%", button.type());
        }
    }

    public void showTeleportForm(Player player) {
        showTeleportForm(player, "main");
    }

    private void showTeleportForm(Player player, String returnMenuId) {
        List<PlayerChoice> choices = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .map(other -> new PlayerChoice(other.getUniqueId(), other.getName()))
                .toList();

        if (choices.isEmpty()) {
            SimpleForm.Builder empty = SimpleForm.builder()
                    .title("Minta Teleport")
                    .content("Tidak ada player lain yang sedang online.");
            addButton(empty, "Kembali", "back", "textures/items/arrow");
            send(player, empty.validResultHandler(response -> sync(() -> showConfiguredMenu(player, returnMenuId))).build());
            return;
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("Minta Teleport")
                .content("Pilih player tujuan.");

        for (PlayerChoice choice : choices) {
            addButton(builder, choice.name(), "player", "textures/items/name_tag");
        }
        addButton(builder, "Kembali", "back", "textures/items/arrow");

        int backIndex = choices.size();
        SimpleForm form = builder.validResultHandler(response -> sync(() -> {
                    if (!player.isOnline()) return;
                    int selected = response.clickedButtonId();
                    if (selected == backIndex) {
                        showConfiguredMenu(player, returnMenuId);
                        return;
                    }
                    if (selected < 0 || selected >= choices.size()) return;
                    PlayerChoice choice = choices.get(selected);
                    Player target = Bukkit.getPlayer(choice.uuid());
                    if (target == null) {
                        plugin.message(player, "player-not-found");
                        showTeleportForm(player, returnMenuId);
                        return;
                    }
                    showTeleportMode(player, target, returnMenuId);
                }))
                .build();
        send(player, form);
    }

    private void showTeleportMode(Player player, Player target, String returnMenuId) {
        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        boolean disabled = plugin.requests().toggles().isDisabled(player.getUniqueId());

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("Teleport • " + targetName)
                .content("Pilih jenis permintaan teleport.");
        addButton(builder, "Pergi ke " + targetName, "tpa", "textures/items/ender_pearl");
        addButton(builder, "Bawa " + targetName + " ke saya", "tpahere", "textures/items/lead");
        addButton(builder,
                "Permintaan masuk: " + (disabled ? "NONAKTIF" : "AKTIF"),
                "toggle", "textures/items/lever");
        addButton(builder, "Kembali", "back", "textures/items/arrow");

        SimpleForm form = builder.validResultHandler(response -> sync(() -> {
                    if (!player.isOnline()) return;
                    switch (response.clickedButtonId()) {
                        case 0 -> createRequest(player, targetId, TeleportMode.TO_TARGET, returnMenuId);
                        case 1 -> createRequest(player, targetId, TeleportMode.TARGET_TO_REQUESTER, returnMenuId);
                        case 2 -> {
                            boolean nowDisabled = plugin.requests().toggles().toggle(player.getUniqueId());
                            plugin.message(player, nowDisabled ? "toggle-off" : "toggle-on");
                            Player currentTarget = Bukkit.getPlayer(targetId);
                            if (currentTarget != null) showTeleportMode(player, currentTarget, returnMenuId);
                            else showTeleportForm(player, returnMenuId);
                        }
                        case 3 -> showTeleportForm(player, returnMenuId);
                        default -> { }
                    }
                }))
                .build();
        send(player, form);
    }

    private void createRequest(Player player, UUID targetId, TeleportMode mode, String returnMenuId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            plugin.message(player, "player-not-found");
            showTeleportForm(player, returnMenuId);
            return;
        }
        plugin.requests().create(player, target, mode);
    }

    public void showIncomingRequest(Player target, Player requester, TeleportMode mode) {
        String content = mode == TeleportMode.TO_TARGET
                ? requester.getName() + " meminta teleport ke lokasimu."
                : requester.getName() + " meminta kamu teleport ke lokasinya.";

        ModalForm form = ModalForm.builder()
                .title("Permintaan Teleport")
                .content(content)
                .button1("TERIMA")
                .button2("TOLAK")
                .validResultHandler(response -> sync(() -> {
                    if (!target.isOnline()) return;
                    if (response.clickedFirst()) plugin.requests().accept(target);
                    else plugin.requests().deny(target);
                }))
                .build();
        send(target, form);
    }

    private void addConfiguredButton(SimpleForm.Builder builder, String text, String path) {
        if (plugin.getConfig().getBoolean("bedrock-icons.enabled", true) && path != null && !path.isBlank()) {
            builder.button(text, FormImage.Type.PATH, path);
            return;
        }
        builder.button(text);
    }

    private void addButton(SimpleForm.Builder builder, String text, String iconKey, String fallbackPath) {
        if (plugin.getConfig().getBoolean("bedrock-icons.enabled", true)) {
            String path = plugin.getConfig().getString("bedrock-icons." + iconKey, fallbackPath);
            if (path != null && !path.isBlank()) {
                builder.button(text, FormImage.Type.PATH, path);
                return;
            }
        }
        builder.button(text);
    }

    private void send(Player player, org.geysermc.cumulus.form.Form form) {
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private record PlayerChoice(UUID uuid, String name) {}
}
