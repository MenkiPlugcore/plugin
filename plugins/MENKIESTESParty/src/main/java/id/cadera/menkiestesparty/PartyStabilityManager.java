package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MENKIESTESParty v1.3.2 stability/UX layer.
 *
 * Keeps notifications and recent activity bounded inside interactions.yml,
 * detects interaction-state changes on a slow snapshot cadence, provides
 * diagnostics, applies command anti-spam, and performs conservative integrity
 * cleanup without introducing a database or background thread.
 */
public final class PartyStabilityManager implements Listener, CommandExecutor, TabCompleter {
    private static final String HUB = "Party Interaction";
    private static final String INBOX = "Party Notification Inbox";
    private static final String ACTIVITY = "Party Recent Activity";

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final StorageBundle db;
    private final InteractionManager interactions;

    private final Map<String, String> contractSnapshot = new HashMap<>();
    private final Map<String, String> relationSnapshot = new HashMap<>();
    private final Map<String, String> requestSnapshot = new HashMap<>();
    private final Map<String, String> applicationSnapshot = new HashMap<>();
    private final Map<String, String> recruitmentSnapshot = new HashMap<>();
    private final Map<String, Integer> projectSnapshot = new HashMap<>();
    private final Map<String, Map<String, String>> memberSnapshot = new HashMap<>();
    private final Map<String, Long> commandGuard = new HashMap<>();
    private boolean snapshotsReady;

    public PartyStabilityManager(MENKIESTESPartyPlugin plugin, PartyService parties,
                                 StorageBundle db, InteractionManager interactions) {
        this.plugin = plugin;
        this.parties = parties;
        this.db = db;
        this.interactions = interactions;

        for (String name : List.of("partyinbox", "partyactivity", "partydebug")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }

        if (enabled() && plugin.getConfig().getBoolean("stability.integrity.safe-repair-on-startup", true)) {
            int repaired = repairSafeIntegrity();
            if (repaired > 0) {
                plugin.getLogger().info("v1.3.2 integrity check repaired " + repaired + " safe orphan/index entries.");
            }
        }
        captureSnapshots(false);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("stability.enabled", true);
    }

    public void tickFast() {
        if (!enabled()) return;
        captureSnapshots(true);
    }

    public void tickMinute() {
        if (!enabled()) return;
        pruneQueues();
        if (plugin.getConfig().getBoolean("stability.integrity.safe-repair-periodic", true)) {
            repairSafeIntegrity();
        }
        cleanupCommandGuard();
    }

    private void captureSnapshots(boolean emit) {
        boolean notify = emit && snapshotsReady;
        scanContracts(notify);
        scanDiplomacy(notify);
        scanApplications(notify);
        scanRecruitment(notify);
        scanProjects(notify);
        scanMembers(notify);
        snapshotsReady = true;
    }

    private void scanContracts(boolean notify) {
        Map<String, String> next = new HashMap<>();
        ConfigurationSection sec = db.interactions.getConfigurationSection("contracts");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                String base = "contracts." + id;
                String status = db.interactions.getString(base + ".status", "UNKNOWN").toUpperCase(Locale.ROOT);
                String source = db.interactions.getString(base + ".source", "?");
                String target = db.interactions.getString(base + ".target", "?");
                String value = status + "|" + source + "|" + target;
                next.put(id, value);

                if (!notify) continue;
                String old = contractSnapshot.get(id);
                if (old == null) {
                    if ("PENDING".equals(status)) {
                        notifyCapableParty(target, "contracts.respond", "CONTRACT",
                                "Contract " + id + " baru dari " + displaySafe(source) + ".");
                        activity(source, "Contract " + id + " dikirim ke " + displaySafe(target) + ".");
                        activity(target, "Contract " + id + " diterima dari " + displaySafe(source) + ".");
                    }
                    continue;
                }

                String oldStatus = old.split("\\|", 2)[0];
                if (!oldStatus.equals(status)) {
                    String message = "Contract " + id + ": " + oldStatus + " -> " + status + ".";
                    notifyParty(source, "CONTRACT", message);
                    if (!source.equalsIgnoreCase(target)) notifyParty(target, "CONTRACT", message);
                    activity(source, message);
                    if (!source.equalsIgnoreCase(target)) activity(target, message);
                }
            }
        }
        contractSnapshot.clear();
        contractSnapshot.putAll(next);
    }

    private void scanDiplomacy(boolean notify) {
        Map<String, String> nextRelations = new HashMap<>();
        ConfigurationSection pairs = db.interactions.getConfigurationSection("diplomacy.pairs");
        if (pairs != null) {
            for (String pair : pairs.getKeys(false)) {
                String base = "diplomacy.pairs." + pair;
                String a = db.interactions.getString(base + ".a", "");
                String b = db.interactions.getString(base + ".b", "");
                String relation = db.interactions.getString(base + ".relation", "NEUTRAL").toUpperCase(Locale.ROOT);
                nextRelations.put(pair, relation + "|" + a + "|" + b);
                if (!notify) continue;
                String old = relationSnapshot.get(pair);
                if (old != null) {
                    String oldRelation = old.split("\\|", 2)[0];
                    if (!oldRelation.equals(relation)) {
                        String message = "Diplomacy " + displaySafe(a) + " <-> " + displaySafe(b)
                                + ": " + oldRelation + " -> " + relation + ".";
                        notifyParty(a, "DIPLOMACY", message);
                        if (!a.equalsIgnoreCase(b)) notifyParty(b, "DIPLOMACY", message);
                        activity(a, message);
                        if (!a.equalsIgnoreCase(b)) activity(b, message);
                    }
                }
            }
        }
        relationSnapshot.clear();
        relationSnapshot.putAll(nextRelations);

        Map<String, String> nextRequests = new HashMap<>();
        ConfigurationSection requests = db.interactions.getConfigurationSection("diplomacy.requests");
        if (requests != null) {
            for (String target : requests.getKeys(false)) {
                ConfigurationSection sources = requests.getConfigurationSection(target);
                if (sources == null) continue;
                for (String source : sources.getKeys(false)) {
                    long expires = sources.getLong(source + ".expires-at", 0L);
                    String key = target + "|" + source;
                    nextRequests.put(key, String.valueOf(expires));
                    if (notify && !requestSnapshot.containsKey(key) && expires >= System.currentTimeMillis()) {
                        notifyCapableParty(target, "diplomacy.manage", "DIPLOMACY",
                                "Alliance request baru dari " + displaySafe(source) + ".");
                        activity(target, "Alliance request diterima dari " + displaySafe(source) + ".");
                        activity(source, "Alliance request dikirim ke " + displaySafe(target) + ".");
                    }
                }
            }
        }
        requestSnapshot.clear();
        requestSnapshot.putAll(nextRequests);
    }

    private void scanApplications(boolean notify) {
        Map<String, String> next = new HashMap<>();
        ConfigurationSection root = db.interactions.getConfigurationSection("applications");
        long now = System.currentTimeMillis();
        if (root != null) {
            for (String party : root.getKeys(false)) {
                ConfigurationSection apps = root.getConfigurationSection(party);
                if (apps == null) continue;
                for (String uuidText : apps.getKeys(false)) {
                    long expires = apps.getLong(uuidText + ".expires-at", 0L);
                    if (expires < now) continue;
                    String name = apps.getString(uuidText + ".name", shortId(uuidText));
                    String key = party + "|" + uuidText;
                    next.put(key, name);
                    if (notify && !applicationSnapshot.containsKey(key)) {
                        notifyCapableParty(party, "applications.manage", "APPLICATION",
                                "Application baru dari " + name + ".");
                        activity(party, name + " mengirim application.");
                    }
                }
            }
        }

        if (notify) {
            for (Map.Entry<String, String> old : applicationSnapshot.entrySet()) {
                if (next.containsKey(old.getKey())) continue;
                String[] parts = old.getKey().split("\\|", 2);
                if (parts.length != 2) continue;
                String party = parts[0];
                try {
                    UUID uuid = UUID.fromString(parts[1]);
                    String current = parties.partyOf(uuid);
                    if (party.equals(current)) {
                        notifyPlayer(uuid, "APPLICATION", "Application ke " + displaySafe(party) + " diterima.");
                    } else if (current != null) {
                        notifyPlayer(uuid, "APPLICATION", "Application ke " + displaySafe(party)
                                + " ditutup karena kamu sudah memiliki Party.");
                    } else {
                        notifyPlayer(uuid, "APPLICATION", "Application ke " + displaySafe(party)
                                + " sudah tidak aktif (ditolak, dibatalkan, atau expired).");
                    }
                } catch (Exception ignored) {
                }
            }
        }

        applicationSnapshot.clear();
        applicationSnapshot.putAll(next);
    }

    private void scanRecruitment(boolean notify) {
        Map<String, String> next = new HashMap<>();
        ConfigurationSection root = db.parties.getConfigurationSection("parties");
        if (root != null) {
            for (String party : root.getKeys(false)) {
                String mode = interactions.recruitmentMode(party);
                next.put(party, mode);
                if (!notify) continue;
                String old = recruitmentSnapshot.get(party);
                if (old != null && !old.equals(mode)) {
                    String message = "Recruitment: " + old + " -> " + mode + ".";
                    notifyParty(party, "RECRUITMENT", message);
                    activity(party, message);
                }
            }
        }
        recruitmentSnapshot.clear();
        recruitmentSnapshot.putAll(next);
    }

    private void scanProjects(boolean notify) {
        Map<String, Integer> next = new HashMap<>();
        ConfigurationSection root = db.parties.getConfigurationSection("parties");
        if (root != null) {
            for (String party : root.getKeys(false)) {
                int completed = db.parties.getInt("parties." + party + ".progression.projects.completed", 0);
                next.put(party, completed);
                if (!notify) continue;
                Integer old = projectSnapshot.get(party);
                if (old != null && completed > old) {
                    String last = db.parties.getString("parties." + party + ".progression.projects.last-completed", "project");
                    String message = "Party Project selesai: " + last + ". Total selesai " + completed + ".";
                    notifyParty(party, "PROJECT", message);
                    activity(party, message);
                }
            }
        }
        projectSnapshot.clear();
        projectSnapshot.putAll(next);
    }

    private void scanMembers(boolean notify) {
        Map<String, Map<String, String>> next = new HashMap<>();
        ConfigurationSection root = db.parties.getConfigurationSection("parties");
        if (root != null) {
            for (String party : root.getKeys(false)) {
                Map<String, String> current = new LinkedHashMap<>();
                ConfigurationSection members = root.getConfigurationSection(party + ".members");
                if (members != null) {
                    for (String uuid : members.getKeys(false)) {
                        current.put(uuid, members.getString(uuid + ".name", shortId(uuid)));
                    }
                }
                next.put(party, current);

                if (!notify) continue;
                Map<String, String> old = memberSnapshot.get(party);
                if (old == null) continue;
                for (Map.Entry<String, String> member : current.entrySet()) {
                    if (!old.containsKey(member.getKey())) activity(party, member.getValue() + " bergabung ke Party.");
                }
                for (Map.Entry<String, String> member : old.entrySet()) {
                    if (!current.containsKey(member.getKey())) activity(party, member.getValue() + " keluar dari Party.");
                }
            }
        }
        memberSnapshot.clear();
        memberSnapshot.putAll(next);
    }

    public int unreadCount(UUID uuid) {
        ConfigurationSection sec = db.interactions.getConfigurationSection("notifications." + uuid);
        if (sec == null) return 0;
        int count = 0;
        for (String id : sec.getKeys(false)) {
            if (!sec.getBoolean(id + ".read", false)) count++;
        }
        return count;
    }

    public void notifyPlayer(UUID uuid, String type, String message) {
        if (!enabled() || uuid == null || message == null || message.isBlank()) return;
        String id = nextId("notification", "N");
        String base = "notifications." + uuid + "." + id;
        db.interactions.set(base + ".type", type == null ? "INFO" : type.toUpperCase(Locale.ROOT));
        db.interactions.set(base + ".message", stripControl(message));
        db.interactions.set(base + ".created-at", System.currentTimeMillis());
        db.interactions.set(base + ".read", false);
        trimQueue("notifications." + uuid,
                Math.max(5, plugin.getConfig().getInt("stability.notifications.max-per-player", 30)));
        plugin.saveDataSoon();
    }

    private void notifyParty(String party, String type, String message) {
        if (party == null || !parties.exists(party)) return;
        for (UUID member : parties.members(party)) notifyPlayer(member, type, message);
    }

    private void notifyCapableParty(String party, String capability, String type, String message) {
        if (party == null || !parties.exists(party)) return;
        boolean delivered = false;
        for (UUID member : parties.members(party)) {
            if (interactions.rankAllows(member, capability)) {
                notifyPlayer(member, type, message);
                delivered = true;
            }
        }
        if (!delivered) {
            UUID owner = parties.owner(party);
            if (owner != null) notifyPlayer(owner, type, message);
        }
    }

    public void openInbox(Player player) {
        if (!enabled()) return;
        if (!player.hasPermission("menkiestesparty.gui.inbox")) {
            msg(player, " &cTidak punya permission menkiestesparty.gui.inbox.");
            return;
        }

        ConfigurationSection sec = db.interactions.getConfigurationSection("notifications." + player.getUniqueId());
        List<String> ids = sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
        ids.sort(Comparator.comparingLong((String id) ->
                db.interactions.getLong("notifications." + player.getUniqueId() + "." + id + ".created-at", 0L)).reversed());

        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + INBOX));
        int slot = 0;
        for (String id : ids) {
            if (slot >= 45) break;
            String base = "notifications." + player.getUniqueId() + "." + id;
            String type = db.interactions.getString(base + ".type", "INFO");
            String message = db.interactions.getString(base + ".message", "");
            long created = db.interactions.getLong(base + ".created-at", 0L);
            boolean read = db.interactions.getBoolean(base + ".read", false);
            inv.setItem(slot++, Util.item(notificationMaterial(type),
                    (read ? "&7" : "&e") + type,
                    "&f" + message,
                    "&8" + age(created) + (read ? " &7• read" : " &e• unread")));
            if (!read) db.interactions.set(base + ".read", true);
        }

        if (ids.isEmpty()) inv.setItem(22, Util.item(Material.PAPER, "&7Inbox kosong", "&8Belum ada notifikasi."));
        inv.setItem(49, Util.item(Material.ARROW, "&eKembali", "&7Kembali ke Interaction Hub."));
        player.openInventory(inv);
        plugin.saveDataSoon();
    }

    private void activity(String party, String message) {
        if (!enabled() || party == null || !parties.exists(party) || message == null || message.isBlank()) return;
        String id = nextId("activity", "A");
        String base = "activity." + party + "." + id;
        db.interactions.set(base + ".message", stripControl(message));
        db.interactions.set(base + ".created-at", System.currentTimeMillis());
        trimQueue("activity." + party,
                Math.max(5, plugin.getConfig().getInt("stability.activity.max-per-party", 30)));
        plugin.saveDataSoon();
    }

    private int activityCount(String party) {
        ConfigurationSection sec = db.interactions.getConfigurationSection("activity." + party);
        return sec == null ? 0 : sec.getKeys(false).size();
    }

    public void openActivity(Player player) {
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) { msg(player, " &cKamu belum punya Party."); return; }
        if (!player.hasPermission("menkiestesparty.gui.activity")) {
            msg(player, " &cTidak punya permission menkiestesparty.gui.activity.");
            return;
        }

        ConfigurationSection sec = db.interactions.getConfigurationSection("activity." + party);
        List<String> ids = sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
        ids.sort(Comparator.comparingLong((String id) ->
                db.interactions.getLong("activity." + party + "." + id + ".created-at", 0L)).reversed());

        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + ACTIVITY));
        int slot = 0;
        for (String id : ids) {
            if (slot >= 45) break;
            String base = "activity." + party + "." + id;
            String message = db.interactions.getString(base + ".message", "");
            long created = db.interactions.getLong(base + ".created-at", 0L);
            inv.setItem(slot++, Util.item(Material.PAPER, "&bRecent Activity", "&f" + message, "&8" + age(created)));
        }
        if (ids.isEmpty()) inv.setItem(22, Util.item(Material.PAPER, "&7Belum ada activity", "&8Feed ini otomatis dibatasi."));
        inv.setItem(49, Util.item(Material.ARROW, "&eKembali", "&7Kembali ke Interaction Hub."));
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHubOpen(InventoryOpenEvent event) {
        if (!enabled() || !(event.getPlayer() instanceof Player player)) return;
        if (!HUB.equalsIgnoreCase(Util.strip(event.getView().getTitle()))) return;
        int unread = unreadCount(player.getUniqueId());
        event.getInventory().setItem(21, Util.item(unread > 0 ? Material.WRITABLE_BOOK : Material.BOOK,
                unread > 0 ? "&eNotification Inbox &c(" + unread + ")" : "&eNotification Inbox",
                "&7Notifikasi Contract, Diplomacy,", "&7Application dan Project.",
                unread > 0 ? "&aKlik untuk baca." : "&8Tidak ada unread notification."));

        String party = parties.partyOf(player.getUniqueId());
        if (party != null) {
            event.getInventory().setItem(23, Util.item(Material.CLOCK, "&bRecent Activity",
                    "&7Aktivitas terbaru Party.", "&7Entry tersimpan: &f" + activityCount(party),
                    "&8Bukan history permanen."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStabilityGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = Util.strip(event.getView().getTitle());
        if (title == null) return;

        if (HUB.equalsIgnoreCase(title)) {
            if (event.getRawSlot() == 21) {
                event.setCancelled(true);
                openInbox(player);
            } else if (event.getRawSlot() == 23 && parties.inParty(player.getUniqueId())) {
                event.setCancelled(true);
                openActivity(player);
            }
            return;
        }

        if (INBOX.equalsIgnoreCase(title) || ACTIVITY.equalsIgnoreCase(title)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 49) plugin.interactionGui().openHub(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled() || !plugin.getConfig().getBoolean("stability.notifications.join-summary", true)) return;
        int unread = unreadCount(event.getPlayer().getUniqueId());
        if (unread <= 0) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            msg(event.getPlayer(), " &eKamu punya &f" + unread + " &enotifikasi Party belum dibaca. &f/partyinbox");
        }, 30L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandGuard(PlayerCommandPreprocessEvent event) {
        if (!enabled()) return;
        String raw = event.getMessage();
        if (raw == null) return;
        String lower = raw.trim().toLowerCase(Locale.ROOT);

        if (lower.matches("^/(party|p|parties)\\s+debug(?:\\s+.*)?$")) {
            event.setCancelled(true);
            String[] split = raw.substring(1).trim().split("\\s+");
            if (!event.getPlayer().hasPermission("menkiestesparty.admin")) {
                msg(event.getPlayer(), " &cNo permission.");
            } else if (split.length < 3) {
                msg(event.getPlayer(), " &c/party debug <party>");
            } else {
                debug(event.getPlayer(), split[2]);
            }
            return;
        }

        if (!isMutatingInteractionCommand(lower)) return;
        long cooldown = Math.max(0L, plugin.getConfig().getLong("stability.anti-spam.command-action-ms", 750L));
        if (cooldown <= 0L) return;
        String key = event.getPlayer().getUniqueId() + "|" + normalizedAction(lower);
        long now = System.currentTimeMillis();
        long last = commandGuard.getOrDefault(key, 0L);
        if (now - last < cooldown) {
            event.setCancelled(true);
            msg(event.getPlayer(), " &eAksi terlalu cepat. Coba lagi sebentar.");
            return;
        }
        commandGuard.put(key, now);
    }

    @EventHandler
    public void onPartyDebugTab(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player) || !player.hasPermission("menkiestesparty.admin")) return;
        String raw = event.getBuffer();
        if (raw == null) return;
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.matches("^/(party|p|parties)\\s+[^\\s]*$")) {
            List<String> out = new ArrayList<>(event.getCompletions());
            String typed = raw.substring(raw.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT);
            if ("debug".startsWith(typed) && out.stream().noneMatch(x -> x.equalsIgnoreCase("debug"))) out.add("debug");
            event.setCompletions(out);
        } else if (lower.matches("^/(party|p|parties)\\s+debug\\s+[^\\s]*$")) {
            String typed = raw.substring(raw.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT);
            event.setCompletions(partyKeys().stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(typed)).toList());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("partyinbox")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
            openInbox(player);
            return true;
        }
        if (name.equals("partyactivity")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
            openActivity(player);
            return true;
        }
        if (name.equals("partydebug")) {
            if (!sender.hasPermission("menkiestesparty.admin")) {
                sender.sendMessage(Util.color("&cNo permission."));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(Util.color("&c/partydebug <party>"));
                return true;
            }
            debug(sender, args[0]);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("partydebug") && sender.hasPermission("menkiestesparty.admin") && args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return partyKeys().stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
        }
        return List.of();
    }

    public void debug(CommandSender sender, String partyInput) {
        String party = Util.key(partyInput);
        if (!parties.exists(party)) {
            sender.sendMessage(Util.color("&cParty not found."));
            return;
        }

        UUID owner = parties.owner(party);
        List<UUID> members = parties.members(party);
        int indexMismatch = 0;
        for (UUID member : members) {
            if (!party.equals(db.parties.getString("players." + member + ".party"))) indexMismatch++;
        }

        int relatedContracts = 0;
        int activeContracts = 0;
        ConfigurationSection contracts = db.interactions.getConfigurationSection("contracts");
        if (contracts != null) {
            for (String id : contracts.getKeys(false)) {
                String base = "contracts." + id;
                String source = db.interactions.getString(base + ".source", "");
                String target = db.interactions.getString(base + ".target", "");
                if (!party.equals(source) && !party.equals(target)) continue;
                relatedContracts++;
                if ("ACTIVE".equalsIgnoreCase(db.interactions.getString(base + ".status", ""))) activeContracts++;
            }
        }

        int diploPairs = 0;
        ConfigurationSection pairs = db.interactions.getConfigurationSection("diplomacy.pairs");
        if (pairs != null) {
            for (String pair : pairs.getKeys(false)) {
                if (party.equals(pairs.getString(pair + ".a")) || party.equals(pairs.getString(pair + ".b"))) diploPairs++;
            }
        }

        int unread = members.stream().mapToInt(this::unreadCount).sum();
        boolean ownerInRoster = owner != null && members.contains(owner);

        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&c&lPARTY DEBUG &8- &f" + parties.display(party)));
        sender.sendMessage(Util.color("&7Key: &f" + party + " &8| &7Owner: &f" + (owner == null ? "INVALID" : owner)));
        sender.sendMessage(Util.color("&7Members: &f" + members.size() + "/" + parties.memberLimit(party)
                + " &8| &7Level: &f" + parties.level(party) + " &8| &7Rep: &f" + parties.rep(party)));
        sender.sendMessage(Util.color("&7Owner in roster: " + (ownerInRoster ? "&aYES" : "&cNO")
                + " &8| &7Player-index mismatch: " + (indexMismatch == 0 ? "&a0" : "&c" + indexMismatch)));
        sender.sendMessage(Util.color("&7Contracts: &f" + relatedContracts + " related / " + activeContracts + " active"
                + " &8| &7Applications: &f" + interactions.pendingApplicationCount(party)));
        sender.sendMessage(Util.color("&7Diplomacy pairs: &f" + diploPairs
                + " &8| &7Recruitment: &f" + interactions.recruitmentMode(party)));
        sender.sendMessage(Util.color("&7Unread member notifications: &f" + unread
                + " &8| &7Recent activity: &f" + activityCount(party)));
        sender.sendMessage(Util.color(indexMismatch == 0 && ownerInRoster
                ? "&aCore integrity check: OK"
                : "&eCore integrity check: review warnings above."));
    }

    public int repairSafeIntegrity() {
        int repaired = 0;

        ConfigurationSection players = db.parties.getConfigurationSection("players");
        if (players != null) {
            for (String uuid : new ArrayList<>(players.getKeys(false))) {
                String party = players.getString(uuid + ".party");
                if (party != null && !parties.exists(party)) {
                    db.parties.set("players." + uuid, null);
                    repaired++;
                }
            }
        }

        Map<String, Integer> membershipCount = new HashMap<>();
        ConfigurationSection partyRoot = db.parties.getConfigurationSection("parties");
        if (partyRoot != null) {
            for (String party : partyRoot.getKeys(false)) {
                ConfigurationSection members = partyRoot.getConfigurationSection(party + ".members");
                if (members == null) continue;
                for (String uuid : members.getKeys(false)) membershipCount.merge(uuid, 1, Integer::sum);
            }
            for (String party : partyRoot.getKeys(false)) {
                ConfigurationSection members = partyRoot.getConfigurationSection(party + ".members");
                if (members == null) continue;
                for (String uuid : members.getKeys(false)) {
                    if (membershipCount.getOrDefault(uuid, 0) != 1) continue;
                    String current = db.parties.getString("players." + uuid + ".party");
                    if (current == null) {
                        db.parties.set("players." + uuid + ".party", party);
                        repaired++;
                    }
                }
            }
        }

        ConfigurationSection applications = db.interactions.getConfigurationSection("applications");
        if (applications != null) {
            for (String party : new ArrayList<>(applications.getKeys(false))) {
                if (!parties.exists(party)) {
                    db.interactions.set("applications." + party, null);
                    repaired++;
                    continue;
                }
                ConfigurationSection apps = applications.getConfigurationSection(party);
                if (apps == null) continue;
                for (String uuidText : new ArrayList<>(apps.getKeys(false))) {
                    try {
                        UUID uuid = UUID.fromString(uuidText);
                        if (parties.inParty(uuid)) {
                            db.interactions.set("applications." + party + "." + uuidText, null);
                            repaired++;
                        }
                    } catch (Exception e) {
                        db.interactions.set("applications." + party + "." + uuidText, null);
                        repaired++;
                    }
                }
            }
        }

        ConfigurationSection pairs = db.interactions.getConfigurationSection("diplomacy.pairs");
        if (pairs != null) {
            for (String pair : new ArrayList<>(pairs.getKeys(false))) {
                String a = pairs.getString(pair + ".a");
                String b = pairs.getString(pair + ".b");
                if (!parties.exists(a) || !parties.exists(b) || a.equalsIgnoreCase(b)) {
                    db.interactions.set("diplomacy.pairs." + pair, null);
                    repaired++;
                }
            }
        }

        ConfigurationSection requests = db.interactions.getConfigurationSection("diplomacy.requests");
        if (requests != null) {
            for (String target : new ArrayList<>(requests.getKeys(false))) {
                ConfigurationSection sources = requests.getConfigurationSection(target);
                if (!parties.exists(target)) {
                    db.interactions.set("diplomacy.requests." + target, null);
                    repaired++;
                    continue;
                }
                if (sources == null) continue;
                for (String source : new ArrayList<>(sources.getKeys(false))) {
                    if (!parties.exists(source) || source.equalsIgnoreCase(target)) {
                        db.interactions.set("diplomacy.requests." + target + "." + source, null);
                        repaired++;
                    }
                }
            }
        }

        if (repaired > 0) plugin.saveDataSoon();
        return repaired;
    }

    private void pruneQueues() {
        long now = System.currentTimeMillis();
        long notificationCutoff = now - Math.max(1L,
                plugin.getConfig().getLong("stability.notifications.retention-days", 14L)) * 86_400_000L;
        ConfigurationSection notifications = db.interactions.getConfigurationSection("notifications");
        if (notifications != null) {
            for (String uuid : new ArrayList<>(notifications.getKeys(false))) {
                pruneOlderThan("notifications." + uuid, notificationCutoff);
                trimQueue("notifications." + uuid,
                        Math.max(5, plugin.getConfig().getInt("stability.notifications.max-per-player", 30)));
            }
        }

        long activityCutoff = now - Math.max(1L,
                plugin.getConfig().getLong("stability.activity.retention-days", 7L)) * 86_400_000L;
        ConfigurationSection activity = db.interactions.getConfigurationSection("activity");
        if (activity != null) {
            for (String party : new ArrayList<>(activity.getKeys(false))) {
                if (!parties.exists(party)) {
                    db.interactions.set("activity." + party, null);
                    continue;
                }
                pruneOlderThan("activity." + party, activityCutoff);
                trimQueue("activity." + party,
                        Math.max(5, plugin.getConfig().getInt("stability.activity.max-per-party", 30)));
            }
        }
        plugin.saveDataSoon();
    }

    private void pruneOlderThan(String base, long cutoff) {
        ConfigurationSection sec = db.interactions.getConfigurationSection(base);
        if (sec == null) return;
        for (String id : new ArrayList<>(sec.getKeys(false))) {
            if (sec.getLong(id + ".created-at", 0L) < cutoff) db.interactions.set(base + "." + id, null);
        }
    }

    private void trimQueue(String base, int max) {
        ConfigurationSection sec = db.interactions.getConfigurationSection(base);
        if (sec == null) return;
        List<String> ids = new ArrayList<>(sec.getKeys(false));
        ids.sort(Comparator.comparingLong(id -> db.interactions.getLong(base + "." + id + ".created-at", 0L)));
        while (ids.size() > max) {
            db.interactions.set(base + "." + ids.remove(0), null);
        }
    }

    private String nextId(String counter, String prefix) {
        String path = "meta.next-" + counter + "-id";
        long next = Math.max(1L, db.interactions.getLong(path, 1L));
        db.interactions.set(path, next + 1L);
        return prefix + String.format(Locale.ROOT, "%08d", next);
    }

    private List<String> partyKeys() {
        ConfigurationSection sec = db.parties.getConfigurationSection("parties");
        if (sec == null) return List.of();
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        keys.sort(Comparator.comparing(parties::display, String.CASE_INSENSITIVE_ORDER));
        return keys;
    }

    private boolean isMutatingInteractionCommand(String lower) {
        return lower.matches("^/(party|p|parties)\\s+(contract\\s+(create|accept|deny|cancel|abandon)|diplomacy\\s+(request|accept|deny|neutral|rival)|apply\\s+.+|applications\\s+(accept|deny)|recruitment\\s+.+)(?:\\s+.*)?$")
                || lower.matches("^/(partycontract|pcontract)\\s+(create|accept|deny|cancel|abandon)(?:\\s+.*)?$")
                || lower.matches("^/(partydiplomacy|pdiplomacy|pdiplo)\\s+(request|accept|deny|neutral|rival)(?:\\s+.*)?$")
                || lower.matches("^/(partyapply|papply)\\s+.+$")
                || lower.matches("^/(partyapplications|papps)\\s+(accept|deny)(?:\\s+.*)?$")
                || lower.matches("^/(partyrecruitment|precruit)\\s+.+$");
    }

    private String normalizedAction(String lower) {
        String[] split = lower.replaceFirst("^/", "").split("\\s+");
        if (split.length >= 3 && Set.of("party", "p", "parties").contains(split[0])) return split[1] + "." + split[2];
        if (split.length >= 2) return split[0] + "." + split[1];
        return lower;
    }

    private void cleanupCommandGuard() {
        long cutoff = System.currentTimeMillis() - 60_000L;
        commandGuard.entrySet().removeIf(e -> e.getValue() < cutoff);
    }

    private Material notificationMaterial(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CONTRACT" -> Material.WRITABLE_BOOK;
            case "DIPLOMACY" -> Material.TOTEM_OF_UNDYING;
            case "APPLICATION" -> Material.NAME_TAG;
            case "PROJECT" -> Material.NETHER_STAR;
            case "RECRUITMENT" -> Material.OAK_SIGN;
            default -> Material.PAPER;
        };
    }

    private String age(long created) {
        if (created <= 0L) return "unknown";
        long seconds = Math.max(0L, (System.currentTimeMillis() - created) / 1000L);
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    private String displaySafe(String party) {
        return party != null && parties.exists(party) ? parties.display(party) : (party == null ? "?" : party);
    }

    private String shortId(String value) {
        if (value == null) return "?";
        return value.substring(0, Math.min(8, value.length()));
    }

    private String stripControl(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private void msg(Player player, String text) {
        player.sendMessage(Util.color(parties.prefix() + text));
    }
}
