package store.moonsign.menu.form;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
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
        SimpleForm form = SimpleForm.builder()
                .title("MOONSIGN • Menu Member")
                .content("Pilih fitur yang ingin kamu buka.")
                .button("🌐 Minta TP", response -> sync(() -> showTeleportForm(player)))
                .button("🌀 Warp", response -> sync(() -> plugin.executeMenuAction(player, "warp")))
                .button("🚪 Player Warp", response -> sync(() -> plugin.executeMenuAction(player, "pwarp")))
                .button("🛏 Set Home", response -> sync(() -> plugin.executeMenuAction(player, "sethome")))
                .button("🗺 Tanah", response -> sync(() -> plugin.executeMenuAction(player, "land")))
                .button("💸 Transfer", response -> sync(() -> plugin.executeMenuAction(player, "transfer")))
                .button("🏦 Bank", response -> sync(() -> plugin.executeMenuAction(player, "bank")))
                .button("🛡 Klan / Team", response -> sync(() -> plugin.executeMenuAction(player, "team")))
                .button("🛒 Toko", response -> sync(() -> plugin.executeMenuAction(player, "shop")))
                .button("🏪 Player Shop", response -> sync(() -> plugin.executeMenuAction(player, "playershop")))
                .button("🚨 Lapor", response -> sync(() -> plugin.executeMenuAction(player, "report")))
                .button("🔄 Barter", response -> sync(() -> plugin.executeMenuAction(player, "barter")))
                .build();
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    public void showTeleportForm(Player player) {
        List<PlayerChoice> choices = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .map(other -> new PlayerChoice(other.getUniqueId(), other.getName()))
                .toList();

        if (choices.isEmpty()) {
            plugin.message(player, "no-other-players");
            return;
        }

        List<String> names = choices.stream().map(PlayerChoice::name).toList();
        boolean currentDisabled = plugin.requests().toggles().isDisabled(player.getUniqueId());

        CustomForm form = CustomForm.builder()
                .title("Minta Teleport")
                .dropdown("Pilih Player", names)
                .toggle("Bawa ke saya\nMATI: Pergi ke mereka | AKTIF: Bawa ke sini", false)
                .toggle("Matikan Permintaan", currentDisabled)
                .validResultHandler(response -> {
                    int selected = response.asDropdown(0);
                    boolean bringHere = response.asToggle(1);
                    boolean disableIncoming = response.asToggle(2);
                    sync(() -> {
                        if (!player.isOnline()) return;
                        plugin.requests().toggles().setDisabled(player.getUniqueId(), disableIncoming);
                        if (selected < 0 || selected >= choices.size()) return;
                        PlayerChoice choice = choices.get(selected);
                        Player target = Bukkit.getPlayer(choice.uuid());
                        if (target == null) {
                            plugin.message(player, "player-not-found");
                            return;
                        }
                        plugin.requests().create(player, target,
                                bringHere ? TeleportMode.TARGET_TO_REQUESTER : TeleportMode.TO_TARGET);
                    });
                })
                .build();
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
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
        FloodgateApi.getInstance().sendForm(target.getUniqueId(), form);
    }

    private void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private record PlayerChoice(UUID uuid, String name) {}
}
