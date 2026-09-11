package id.cadera.menkiestesparty;

import java.time.LocalDate;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

/**
 * v1.1 compatibility shell. Party Hall was removed and this component now owns
 * Daily Party Missions while keeping the old public methods binary-compatible.
 *
 * v2.1.0 can accept trusted profession activity from CdrJobs. When the bridge
 * is authoritative, the legacy raw Bukkit listeners are suspended so one
 * activity can never be counted twice.
 */
public final class RewardHallManager implements Listener, CommandExecutor, TabCompleter {
    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final StorageBundle db;
    private final Set<String> placedMining = new HashSet<>();
    private static final List<String> LEGACY_TYPES = List.of("mining", "hunter", "farmer");
    private static final List<String> FIVE_PATH_TYPES = List.of("mining", "hunter", "farmer", "lumberjack", "fisher");

    public RewardHallManager(MENKIESTESPartyPlugin plugin, PartyService parties, StorageBundle db) {
        this.plugin = plugin;
        this.parties = parties;
        this.db = db;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("partydaily");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        // ProgressionManager is initialized immediately before this compatibility
        // component in MENKIESTESPartyPlugin#onEnable, so the optional bridge can
        // be installed here without changing the core bootstrap order.
        CdrJobsIntegrationManager.install(plugin, parties, plugin.progression(), this, db);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!enabled() || CdrJobsIntegrationManager.authoritativeDaily(plugin)) return;
        Material m = e.getBlockPlaced().getType();
        if (parties.isMiningMaterial(m)) placedMining.add(locationKey(e.getBlockPlaced().getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!enabled() || CdrJobsIntegrationManager.authoritativeDaily(plugin)) return;
        Player p = e.getPlayer();
        String party = parties.partyOf(p.getUniqueId());
        if (party == null) return;
        Block b = e.getBlock();
        Material m = b.getType();
        if (parties.isMiningMaterial(m)) {
            String key = locationKey(b.getLocation());
            if (!placedMining.remove(key)) addProgress(party, "mining", 1);
        }
        if (isMatureCrop(b)) addProgress(party, "farmer", 1);
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (!enabled() || CdrJobsIntegrationManager.authoritativeDaily(plugin)) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null || e.getEntity() instanceof Player) return;
        String party = parties.partyOf(killer.getUniqueId());
        if (party != null) addProgress(party, "hunter", 1);
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage();
        if (raw == null) return;
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("/party daily") || lower.equals("/p daily") || lower.equals("/parties daily")) {
            e.setCancelled(true);
            showDaily(e.getPlayer());
            return;
        }
        if (lower.equals("/party daily resetall") || lower.equals("/p daily resetall")) {
            e.setCancelled(true);
            if (!e.getPlayer().hasPermission("menkiestesparty.admin")) {
                msg(e.getPlayer(), " &cKamu tidak punya izin admin.");
                return;
            }
            resetAll();
            msg(e.getPlayer(), " &aSemua Daily Party Mission berhasil direset.");
            return;
        }
        if (lower.equals("/party hall") || lower.startsWith("/party hall ") ||
            lower.equals("/party claimchest") || lower.equals("/party rewards")) {
            e.setCancelled(true);
            removed(e.getPlayer());
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent e) {
        String b = e.getBuffer();
        if (b == null) return;
        String lower = b.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("/party ") || lower.startsWith("/p ") || lower.startsWith("/parties "))) return;
        List<String> out = new ArrayList<>(e.getCompletions());
        out.removeIf(s -> s.equalsIgnoreCase("hall") || s.equalsIgnoreCase("claimchest") || s.equalsIgnoreCase("rewards"));
        String[] split = lower.trim().split("\\s+");
        if (split.length <= 2) {
            String typed = split.length == 2 ? split[1] : "";
            if ("daily".startsWith(typed) && out.stream().noneMatch(s -> s.equalsIgnoreCase("daily"))) out.add("daily");
        }
        e.setCompletions(out);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("daily-missions.enabled", true);
    }

    private List<String> activeTypes() {
        return CdrJobsIntegrationManager.integrationActive(plugin) ? FIVE_PATH_TYPES : LEGACY_TYPES;
    }

    private void ensureToday(String party) {
        String root = "parties." + party + ".daily";
        String today = LocalDate.now().toString();
        String stored = db.parties.getString(root + ".date");
        if (today.equals(stored)) return;
        db.parties.set(root + ".date", today);
        for (String type : FIVE_PATH_TYPES) {
            db.parties.set(root + "." + type + ".progress", 0);
            db.parties.set(root + "." + type + ".completed", false);
        }
        plugin.saveDataSoon();
    }

    /**
     * Trusted path used only by CdrJobsIntegrationManager after CdrJobs has
     * already accepted a ProfessionActionEvent through its anti-exploit rules.
     */
    public void addTrustedProgress(String party, String type, int amount) {
        if (!enabled() || party == null || amount <= 0 || !FIVE_PATH_TYPES.contains(type)) return;
        addProgress(party, type, amount);
    }

    private void addProgress(String party, String type, int amount) {
        if (amount <= 0 || !FIVE_PATH_TYPES.contains(type) || !parties.exists(party)) return;
        ensureToday(party);
        String root = "parties." + party + ".daily." + type;
        if (db.parties.getBoolean(root + ".completed", false)) return;
        int goal = goal(type);
        long current = Math.max(0L, db.parties.getLong(root + ".progress", 0L));
        long next = Math.min((long) goal, current + amount);
        db.parties.set(root + ".progress", next);
        if (next >= goal) {
            db.parties.set(root + ".completed", true);
            int xp = xp(type);
            parties.addRep(party, xp);
            parties.broadcastParty(party, Util.color(parties.prefix() + " &aDaily Mission &f" + title(type) + " &aselesai! &b+" + xp + " Party XP"));
        }
        plugin.saveDataSoon();
    }

    private int goal(String type) {
        int def = switch (type) {
            case "hunter" -> 30;
            case "farmer" -> 80;
            case "lumberjack" -> 120;
            case "fisher" -> 25;
            default -> 150;
        };
        return Math.max(1, plugin.getConfig().getInt("daily-missions." + type + ".goal", def));
    }

    private int xp(String type) {
        int def = switch (type) {
            case "hunter" -> 50;
            case "farmer" -> 35;
            case "lumberjack" -> 40;
            case "fisher" -> 45;
            default -> 40;
        };
        return Math.max(0, plugin.getConfig().getInt("daily-missions." + type + ".party-xp", def));
    }

    private void showDaily(Player p) {
        if (!enabled()) { msg(p, " &cDaily Party Mission sedang dinonaktifkan."); return; }
        String party = parties.partyOf(p.getUniqueId());
        if (party == null) { msg(p, " &cKamu belum memiliki Party."); return; }
        ensureToday(party);
        p.sendMessage(Util.color("&b&lDAILY PARTY MISSION &8- &f" + parties.display(party)));
        for (String type : activeTypes()) {
            String root = "parties." + party + ".daily." + type;
            int progress = (int) Math.min(goal(type), Math.max(0L, db.parties.getLong(root + ".progress", 0L)));
            boolean done = db.parties.getBoolean(root + ".completed", false);
            String mark = done ? " &a✔" : "";
            p.sendMessage(Util.color("&7" + title(type) + ": &f" + progress + "/" + goal(type) + " &8| &b+" + xp(type) + " Party XP" + mark));
        }
        if (CdrJobsIntegrationManager.integrationActive(plugin)) {
            p.sendMessage(Util.color("&8Sumber progress: CdrJobs accepted profession actions."));
        } else {
            p.sendMessage(Util.color("&8Sumber progress: MENKIESTESParty standalone fallback."));
        }
        p.sendMessage(Util.color("&8Reset otomatis setiap pergantian tanggal server."));
    }

    private String title(String type) {
        return switch (type) {
            case "hunter" -> "Hunter";
            case "farmer" -> "Farmer";
            case "lumberjack" -> "Lumberjack";
            case "fisher" -> "Fisher";
            default -> "Mining";
        };
    }

    private boolean isMatureCrop(Block b) {
        String n = b.getType().name();
        boolean crop = n.equals("WHEAT") || n.equals("CARROTS") || n.equals("POTATOES") || n.equals("BEETROOTS") || n.equals("NETHER_WART") || n.equals("COCOA");
        if (!crop) return false;
        Object data = b.getBlockData();
        return !(data instanceof Ageable) || ((Ageable)data).getAge() >= ((Ageable)data).getMaximumAge();
    }

    private String locationKey(Location l) {
        String world = l.getWorld() == null ? "world" : l.getWorld().getName();
        return world + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private void resetAll() {
        ConfigurationSection sec = db.parties.getConfigurationSection("parties");
        if (sec == null) return;
        for (String party : sec.getKeys(false)) {
            db.parties.set("parties." + party + ".daily", null);
            ensureToday(party);
        }
        plugin.saveDataSoon();
    }

    private void msg(Player p, String s) { p.sendMessage(Util.color(parties.prefix() + s)); }
    private void removed(Player p) { msg(p, " &eParty Hall dan reward otomatis Party War telah dihapus. &7Reward item diberikan manual oleh admin."); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player only."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("resetall")) {
            if (!p.hasPermission("menkiestesparty.admin")) { msg(p, " &cKamu tidak punya izin admin."); return true; }
            resetAll(); msg(p, " &aSemua Daily Party Mission berhasil direset."); return true;
        }
        showDaily(p); return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("menkiestesparty.admin")) return List.of("resetall");
        return Collections.emptyList();
    }

    // Removed Party Hall API kept only for binary compatibility with v1.0.4 classes.
    public void awardWar(String party, String runId, Collection<UUID> eligible) { }
    public void setPlannedItem(Player player, int slot) { removed(player); }
    public void appendPlannedItem(Player player) { removed(player); }
    public void removePlannedItem(Player player, int slot) { removed(player); }
    public void clearPlannedItems(Player player) { removed(player); }
    public void setCurrentItem(Player player) { removed(player); }
    public void showPlan(Player player) { removed(player); }
    public void claim(Player player) { removed(player); }
    public void show(Player player) { removed(player); }
}
