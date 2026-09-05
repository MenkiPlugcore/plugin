package store.moonsign.menu.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import store.moonsign.menu.MoonSignMenuPlugin;
import store.moonsign.menu.tp.TeleportMode;

import java.util.List;
import java.util.UUID;

public final class JavaMenuService implements Listener {
    private final MoonSignMenuPlugin plugin;

    public JavaMenuService(MoonSignMenuPlugin plugin) {
        this.plugin = plugin;
    }

    public void showMain(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.MAIN, null, 27, "MOONSIGN • Menu Member");
        Inventory inv = holder.getInventory();
        inv.setItem(10, item(Material.ENDER_EYE, "Minta TP"));
        inv.setItem(11, item(Material.ENDER_PEARL, "Warp"));
        inv.setItem(12, item(Material.OAK_DOOR, "Player Warp"));
        inv.setItem(13, item(Material.RED_BED, "Set Home"));
        inv.setItem(14, item(Material.GRASS_BLOCK, "Tanah"));
        inv.setItem(15, item(Material.EMERALD, "Transfer"));
        inv.setItem(16, item(Material.GOLD_INGOT, "Bank"));
        inv.setItem(19, item(Material.SHIELD, "Klan / Team"));
        inv.setItem(20, item(Material.CHEST, "Toko"));
        inv.setItem(21, item(Material.BARREL, "Player Shop"));
        inv.setItem(22, item(Material.WRITABLE_BOOK, "Lapor"));
        inv.setItem(23, item(Material.AMETHYST_SHARD, "Barter"));
        player.openInventory(inv);
    }

    public void showPlayerSelect(Player player) {
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .toList();
        if (players.isEmpty()) {
            plugin.message(player, "no-other-players");
            return;
        }

        int size = Math.min(54, Math.max(9, ((players.size() + 8) / 9) * 9));
        MenuHolder holder = new MenuHolder(MenuHolder.Type.PLAYER_SELECT, null, size, "Pilih Player");
        Inventory inv = holder.getInventory();
        int slot = 0;
        for (Player target : players) {
            if (slot >= size) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setDisplayName("§b" + target.getName());
            meta.setOwningPlayer(target);
            meta.getPersistentDataContainer().set(plugin.playerKey(), PersistentDataType.STRING, target.getUniqueId().toString());
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        player.openInventory(inv);
    }

    private void showMode(Player player, Player target) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.TP_MODE, target.getUniqueId(), 9,
                "TP • " + target.getName());
        Inventory inv = holder.getInventory();
        inv.setItem(3, item(Material.ENDER_PEARL, "Pergi ke " + target.getName()));
        inv.setItem(5, item(Material.LEAD, "Bawa " + target.getName() + " ke saya"));
        inv.setItem(8, item(plugin.requests().toggles().isDisabled(player.getUniqueId())
                ? Material.REDSTONE_BLOCK : Material.LIME_CONCRETE,
                "Incoming TP: " + (plugin.requests().toggles().isDisabled(player.getUniqueId()) ? "OFF" : "ON")));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        switch (holder.type()) {
            case MAIN -> handleMain(player, event.getSlot());
            case PLAYER_SELECT -> {
                ItemMeta meta = clicked.getItemMeta();
                String raw = meta.getPersistentDataContainer().get(plugin.playerKey(), PersistentDataType.STRING);
                if (raw == null) return;
                try {
                    Player target = Bukkit.getPlayer(UUID.fromString(raw));
                    if (target == null) {
                        plugin.message(player, "player-not-found");
                        return;
                    }
                    showMode(player, target);
                } catch (IllegalArgumentException ignored) {
                }
            }
            case TP_MODE -> handleTpMode(player, holder.targetId(), event.getSlot());
        }
    }

    private void handleMain(Player player, int slot) {
        switch (slot) {
            case 10 -> showPlayerSelect(player);
            case 11 -> plugin.executeMenuAction(player, "warp");
            case 12 -> plugin.executeMenuAction(player, "pwarp");
            case 13 -> plugin.executeMenuAction(player, "sethome");
            case 14 -> plugin.executeMenuAction(player, "land");
            case 15 -> plugin.executeMenuAction(player, "transfer");
            case 16 -> plugin.executeMenuAction(player, "bank");
            case 19 -> plugin.executeMenuAction(player, "team");
            case 20 -> plugin.executeMenuAction(player, "shop");
            case 21 -> plugin.executeMenuAction(player, "playershop");
            case 22 -> plugin.executeMenuAction(player, "report");
            case 23 -> plugin.executeMenuAction(player, "barter");
            default -> {
                return;
            }
        }
        if (slot != 10) player.closeInventory();
    }

    private void handleTpMode(Player player, UUID targetId, int slot) {
        if (slot == 8) {
            boolean disabled = plugin.requests().toggles().toggle(player.getUniqueId());
            plugin.message(player, disabled ? "toggle-off" : "toggle-on");
            Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
            if (target != null) showMode(player, target);
            return;
        }
        if (targetId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            plugin.message(player, "player-not-found");
            player.closeInventory();
            return;
        }
        if (slot == 3) {
            plugin.requests().create(player, target, TeleportMode.TO_TARGET);
            player.closeInventory();
        } else if (slot == 5) {
            plugin.requests().create(player, target, TeleportMode.TARGET_TO_REQUESTER);
            player.closeInventory();
        }
    }

    private ItemStack item(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f" + name);
        item.setItemMeta(meta);
        return item;
    }
}
