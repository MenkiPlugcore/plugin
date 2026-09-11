package id.cadera.menkiestesparty;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v2.0.1 global visual theme for MENKIESTESParty inventory UIs.
 *
 * Existing GUI managers keep ownership of all functional items and click
 * actions. This layer only fills empty top-inventory slots with tagged visual
 * panes, adds a decorative header to the root Party menu, and prevents GUI
 * inventory items from being moved by players.
 */
public final class GuiThemeManager implements Listener {
    private static final Set<String> EXACT_TITLES = Set.of(
            "MENKIESTES Party",
            "Party Invite",
            "Party Members",
            "Manage Member",
            "Party Progression",
            "Party Profile",
            "Party Levels",
            "Party Interaction",
            "Party Overview",
            "Contract Detail",
            "Contract Target",
            "Contract Type",
            "Contract Goal",
            "Diplomacy Detail",
            "Party Recruitment",
            "Application Detail",
            "Rank Capabilities",
            "Confirm Interaction",
            "Confirm Action",
            "Set Division"
    );

    private static final List<String> PREFIX_TITLES = List.of(
            "Party Projects",
            "Party Skills",
            "Party Divisions",
            "Party Identity",
            "Division Members",
            "Party Browser",
            "Party Contracts",
            "Party Diplomacy",
            "Party Applications",
            "My Applications",
            "Party Notification Inbox",
            "Party Recent Activity",
            "Party Admin Browser",
            "Party Admin Inspect"
    );

    private final MENKIESTESPartyPlugin plugin;
    private final NamespacedKey decorationKey;
    private final File settingsFile;
    private YamlConfiguration settings;
    private ItemStack primaryPane;
    private ItemStack accentPane;
    private ItemStack secondaryPane;

    public GuiThemeManager(MENKIESTESPartyPlugin plugin) {
        this.plugin = plugin;
        this.decorationKey = new NamespacedKey(plugin, "gui_theme_decoration");
        this.settingsFile = new File(plugin.getDataFolder(), "gui-theme.yml");
        load();
    }

    public boolean enabled() {
        return settings == null || settings.getBoolean("theme.enabled", true);
    }

    public void reload() {
        load();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!enabled() || !(event.getPlayer() instanceof Player player)) return;
        String title = clean(event.getView().getTitle());
        if (!themedTitle(title)) return;
        decorate(player, event.getInventory(), title);
    }

    /**
     * Run after the existing functional GUI handlers. The action has already
     * been delegated at this point; cancellation only prevents taking/swapping
     * the inventory item itself.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!enabled() || !themedTitle(clean(event.getView().getTitle()))) return;
        int topSize = event.getView().getTopInventory().getSize();
        int raw = event.getRawSlot();
        if ((raw >= 0 && raw < topSize) || event.isShiftClick() || isDecoration(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!enabled() || !themedTitle(clean(event.getView().getTitle()))) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw >= 0 && raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void decorate(Player player, Inventory inventory, String title) {
        int size = inventory.getSize();
        if (size < 9 || size % 9 != 0) return;

        if (title.equalsIgnoreCase("MENKIESTES Party")
                && settings.getBoolean("theme.main-header.enabled", true)
                && empty(inventory.getItem(4))) {
            inventory.setItem(4, mainHeader(player));
        }

        if (!settings.getBoolean("theme.fill-empty-slots", true)) return;
        for (int slot = 0; slot < size; slot++) {
            if (!empty(inventory.getItem(slot))) continue;
            ItemStack decoration = switch (GuiThemeLayout.tone(size, slot)) {
                case ACCENT -> accentPane;
                case SECONDARY -> secondaryPane;
                default -> primaryPane;
            };
            inventory.setItem(slot, decoration.clone());
        }
    }

    private ItemStack mainHeader(Player player) {
        Material material = material("theme.main-header.material", Material.NETHER_STAR);
        String configuredName = settings.getString("theme.main-header.name", "&b&lMENKIESTES &fPARTY");
        List<String> lore = new ArrayList<>();
        String party = plugin.parties().partyOf(player.getUniqueId());

        if (party == null || !plugin.parties().exists(party)) {
            lore.add("&7Party system by &fCADERA");
            lore.add("");
            lore.add("&7Status: &cBelum punya Party");
            lore.add("&7Create: &f/party create <nama>");
            lore.add("&7Browse: &f/party browse");
        } else {
            lore.add("&7Party: &f" + plugin.parties().display(party));
            lore.add("&7Role: &f" + plugin.parties().role(player.getUniqueId()));
            lore.add("&7Level: &b" + plugin.parties().level(party)
                    + " &8• &7Member: &f" + plugin.parties().memberCount(party)
                    + "/" + plugin.parties().memberLimit(party));
            String tag = plugin.socialIdentity() == null ? "" : plugin.socialIdentity().tag(party);
            String badge = plugin.socialIdentity() == null ? "" : plugin.socialIdentity().activeBadgeDisplay(party);
            if (!tag.isBlank() || !badge.isBlank()) {
                lore.add("&7Identity: &f" + (tag.isBlank() ? "-" : "[" + tag + "]")
                        + (badge.isBlank() ? "" : " &8• &d" + badge));
            }
        }
        lore.add("");
        lore.add(settings.getString("theme.watermark", "&8MENKIESTES UI &7• &fCADERA"));
        lore.add("&8v" + plugin.getDescription().getVersion());

        ItemStack item = Util.item(material, configuredName, lore.toArray(String[]::new));
        markDecoration(item);
        return item;
    }

    private void load() {
        try {
            if (!settingsFile.isFile()) plugin.saveResource("gui-theme.yml", false);
            settings = YamlConfiguration.loadConfiguration(settingsFile);
            try (InputStream input = plugin.getResource("gui-theme.yml")) {
                if (input != null) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(input, StandardCharsets.UTF_8));
                    settings.setDefaults(defaults);
                    settings.options().copyDefaults(true);
                    settings.save(settingsFile);
                }
            }
        } catch (Exception failure) {
            plugin.getLogger().warning("Unable to load gui-theme.yml; using safe in-memory defaults: " + safe(failure));
            settings = new YamlConfiguration();
        }
        rebuildDecorations();
    }

    private void rebuildDecorations() {
        primaryPane = pane(material("theme.palette.primary", Material.BLACK_STAINED_GLASS_PANE));
        accentPane = pane(material("theme.palette.accent", Material.CYAN_STAINED_GLASS_PANE));
        secondaryPane = pane(material("theme.palette.secondary", Material.PURPLE_STAINED_GLASS_PANE));
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Util.color(settings.getString("theme.filler-name", "&0")));
            meta.getPersistentDataContainer().set(decorationKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void markDecoration(ItemStack item) {
        if (item == null) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(decorationKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private boolean isDecoration(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(decorationKey, PersistentDataType.BYTE);
    }

    private Material material(String path, Material fallback) {
        String raw = settings == null ? fallback.name() : settings.getString(path, fallback.name());
        Material value = raw == null ? null : Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return value == null ? fallback : value;
    }

    private boolean themedTitle(String title) {
        if (title == null || title.isBlank()) return false;
        if (EXACT_TITLES.stream().anyMatch(value -> value.equalsIgnoreCase(title))) return true;
        for (String prefix : PREFIX_TITLES) {
            if (title.regionMatches(true, 0, prefix, 0, prefix.length())) return true;
        }
        return false;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private static String clean(String raw) {
        String value = Util.strip(raw);
        return value == null ? "" : value.trim();
    }

    private static String safe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
