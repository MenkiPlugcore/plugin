package store.menkiestes.menkiafk.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.afk.AfkManager;

import java.util.Locale;

public final class ActivityListener implements Listener {
    private final MenkiAfkPlugin plugin;
    private final AfkManager manager;

    public ActivityListener(MenkiAfkPlugin plugin, AfkManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent && !plugin.getConfig().getBoolean("return-on.teleport", false)) return;
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();

        boolean moved = Double.compare(from.getX(), to.getX()) != 0
                || Double.compare(from.getY(), to.getY()) != 0
                || Double.compare(from.getZ(), to.getZ()) != 0;
        boolean rotated = Float.compare(from.getYaw(), to.getYaw()) != 0
                || Float.compare(from.getPitch(), to.getPitch()) != 0;

        boolean counts = (moved && plugin.getConfig().getBoolean("return-on.move", true))
                || (rotated && plugin.getConfig().getBoolean("return-on.rotate", true));
        if (counts) activity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Async chat can race with disconnect. Never revive/update runtime state for an offline player.
            if (!player.isOnline()) return;
            if (plugin.getConfig().getBoolean("return-on.chat", true)) activity(player);
            manager.handleMentions(player, message);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        String first = raw.length() > 1 ? raw.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT) : "";

        // Only MENKIAFK's own toggle must bypass activity handling before its executor runs.
        // External namespaced commands such as /essentials:afk remain untouched and count as activity.
        if (first.equals("afk") || first.equals("menkiafk:afk")) return;

        manager.handlePrivateMessage(event.getPlayer(), raw);
        if (plugin.getConfig().getBoolean("return-on.command", true)) activity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getConfig().getBoolean("return-on.interact", true)) activity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.getConfig().getBoolean("return-on.interact", true)) activity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (plugin.getConfig().getBoolean("return-on.inventory-click", true) && event.getWhoClicked() instanceof Player player) {
            activity(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getConfig().getBoolean("return-on.death", true)) activity(event.getEntity());
    }

    private void activity(Player player) {
        if (!player.isOnline()) return;
        if (manager.isAfk(player.getUniqueId())) manager.returnFromAfk(player, true);
        else manager.touchThrottled(player, 750L);
    }
}
