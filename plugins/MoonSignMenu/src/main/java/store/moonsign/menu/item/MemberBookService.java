package store.moonsign.menu.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import store.moonsign.menu.MoonSignMenuPlugin;
import store.moonsign.menu.util.Colors;

import java.util.ArrayList;
import java.util.List;

public final class MemberBookService implements Listener {
    private final MoonSignMenuPlugin plugin;
    private final NamespacedKey bookKey;

    public MemberBookService(MoonSignMenuPlugin plugin) {
        this.plugin = plugin;
        this.bookKey = new NamespacedKey(plugin, "member-book");
    }

    public void giveToOnlinePlayers() {
        if (!isEnabled() || !plugin.getConfig().getBoolean("member-book.give-on-join", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureBook(player);
        }
    }

    public void ensureBook(Player player) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("member-book.give-on-join", true)) return;
        if (hasBook(player)) return;

        ItemStack book = createBook();
        int configuredSlot = plugin.getConfig().getInt("member-book.hotbar-slot", 8);
        int slot = Math.max(0, Math.min(8, configuredSlot));
        ItemStack current = player.getInventory().getItem(slot);

        if (current == null || current.getType().isAir()) {
            player.getInventory().setItem(slot, book);
            return;
        }

        if (!player.getInventory().addItem(book).isEmpty()) {
            plugin.message(player, "member-book-inventory-full");
        }
    }

    public boolean isMemberBook(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(bookKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean hasBook(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMemberBook(item)) return true;
        }
        return false;
    }

    private ItemStack createBook() {
        String materialName = plugin.getConfig().getString("member-book.material", "BOOK");
        Material material = materialName == null ? Material.BOOK : Material.matchMaterial(materialName);
        if (material == null) material = Material.BOOK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Colors.legacy(plugin.getConfig().getString(
                "member-book.name", "&d&lMOONSIGN &fMember Book")));

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("member-book.lore")) {
            lore.add(Colors.legacy(line));
        }
        if (!lore.isEmpty()) meta.setLore(lore);

        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("member-book.enabled", true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("member-book.give-on-join", true)) return;
        long delay = Math.max(1L, plugin.getConfig().getLong("member-book.give-delay-ticks", 10L));
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) ensureBook(player);
        }, delay);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) ensureBook(player);
        }, 1L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isEnabled() || event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMemberBook(event.getItem())) return;

        event.setCancelled(true);
        plugin.openMenu(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("member-book.prevent-drop", true)) return;
        if (!isMemberBook(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        plugin.message(event.getPlayer(), "member-book-cannot-drop");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!isEnabled()) return;
        event.getDrops().removeIf(this::isMemberBook);
    }
}
