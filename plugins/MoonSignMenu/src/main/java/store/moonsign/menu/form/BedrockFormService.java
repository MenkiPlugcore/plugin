package store.moonsign.menu.form;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import store.moonsign.menu.MoonSignMenuPlugin;
import store.moonsign.menu.tp.TeleportMode;

import java.util.List;
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
        SimpleForm.Builder builder = SimpleForm.builder()
                .title("MOONSIGN • Menu Member")
                .content("Pilih fitur yang ingin kamu buka.");

        addButton(builder, "Minta TP", "tpa", "textures/items/ender_pearl");
        addButton(builder, "Warp", "warp", "textures/items/compass_item");
        addButton(builder, "Player Warp", "pwarp", "textures/items/map_filled");
        addButton(builder, "Set Home", "sethome", "textures/items/bed_red");
        addButton(builder, "Tanah", "land", "textures/items/map_empty");
        addButton(builder, "Transfer", "transfer", "textures/items/paper");
        addButton(builder, "Bank", "bank", "textures/items/gold_ingot");
        addButton(builder, "Klan / Team", "team", "textures/items/iron_sword");
        addButton(builder, "Toko", "shop", "textures/items/nether_star");
        addButton(builder, "Player Shop", "playershop", "textures/items/name_tag");
        addButton(builder, "Lapor", "report", "textures/items/book_writable");
        addButton(builder, "Barter", "barter", "textures/items/lead");
        addButton(builder, "Tutup", "close", "textures/items/barrier");

        SimpleForm form = builder.validResultHandler(response -> sync(() -> {
                    if (!player.isOnline()) return;
                    switch (response.clickedButtonId()) {
                        case 0 -> showTeleportForm(player);
                        case 1 -> plugin.executeMenuAction(player, "warp");
                        case 2 -> plugin.executeMenuAction(player, "pwarp");
                        case 3 -> plugin.executeMenuAction(player, "sethome");
                        case 4 -> plugin.executeMenuAction(player, "land");
                        case 5 -> plugin.executeMenuAction(player, "transfer");
                        case 6 -> plugin.executeMenuAction(player, "bank");
                        case 7 -> plugin.executeMenuAction(player, "team");
                        case 8 -> plugin.executeMenuAction(player, "shop");
                        case 9 -> plugin.executeMenuAction(player, "playershop");
                        case 10 -> plugin.executeMenuAction(player, "report");
                        case 11 -> plugin.executeMenuAction(player, "barter");
                        case 12 -> { }
                        default -> { }
                    }
                }))
                .build();
        send(player, form);
    }

    public void showTeleportForm(Player player) {
        List<PlayerChoice> choices = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .map(other -> new PlayerChoice(other.getUniqueId(), other.getName()))
                .toList();

        if (choices.isEmpty()) {
            SimpleForm.Builder empty = SimpleForm.builder()
                    .title("Minta Teleport")
                    .content("Tidak ada player lain yang sedang online.");
            addButton(empty, "Kembali", "back", "textures/items/arrow");
            send(player, empty.validResultHandler(response -> sync(() -> showMainMenu(player))).build());
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
                        showMainMenu(player);
                        return;
                    }
                    if (selected < 0 || selected >= choices.size()) return;
                    PlayerChoice choice = choices.get(selected);
                    Player target = Bukkit.getPlayer(choice.uuid());
                    if (target == null) {
                        plugin.message(player, "player-not-found");
                        showTeleportForm(player);
                        return;
                    }
                    showTeleportMode(player, target);
                }))
                .build();
        send(player, form);
    }

    private void showTeleportMode(Player player, Player target) {
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
                        case 0 -> createRequest(player, targetId, TeleportMode.TO_TARGET);
                        case 1 -> createRequest(player, targetId, TeleportMode.TARGET_TO_REQUESTER);
                        case 2 -> {
                            boolean nowDisabled = plugin.requests().toggles().toggle(player.getUniqueId());
                            plugin.message(player, nowDisabled ? "toggle-off" : "toggle-on");
                            Player currentTarget = Bukkit.getPlayer(targetId);
                            if (currentTarget != null) showTeleportMode(player, currentTarget);
                            else showTeleportForm(player);
                        }
                        case 3 -> showTeleportForm(player);
                        default -> { }
                    }
                }))
                .build();
        send(player, form);
    }

    private void createRequest(Player player, UUID targetId, TeleportMode mode) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            plugin.message(player, "player-not-found");
            showTeleportForm(player);
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
