package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyService {
    public enum Role { OWNER, OFFICER, MEMBER }
    public record Invite(String partyKey, UUID inviter, long expiresAt) {}

    private final MENKIESTESPartyPlugin plugin;
    private final StorageBundle db;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final Set<String> placedMining = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> homeTeleportToken = new ConcurrentHashMap<>();
    private String prefix;

    public PartyService(MENKIESTESPartyPlugin plugin, StorageBundle db) {
        this.plugin = plugin;
        this.db = db;
        this.prefix = Util.color(plugin.getConfig().getString("prefix", "&b&lPARTY &8»&r"));
    }

    public String prefix() { return prefix; }
    public StorageBundle db() { return db; }

    public String partyOf(UUID uuid) {
        return db.parties.getString("players." + uuid + ".party");
    }

    public boolean inParty(UUID uuid) { return partyOf(uuid) != null; }

    public String display(String partyKey) {
        return db.parties.getString("parties." + partyKey + ".display", partyKey);
    }

    public boolean exists(String partyKey) {
        return partyKey != null && db.parties.contains("parties." + partyKey + ".display");
    }

    public UUID owner(String partyKey) {
        String s = db.parties.getString("parties." + partyKey + ".owner");
        try { return s == null ? null : UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public Role role(UUID uuid) {
        String p = partyOf(uuid);
        if (p == null) return null;
        if (uuid.equals(owner(p))) return Role.OWNER;
        String r = db.parties.getString("parties." + p + ".members." + uuid + ".role", "MEMBER");
        try { return Role.valueOf(r.toUpperCase(Locale.ROOT)); } catch (Exception e) { return Role.MEMBER; }
    }

    public boolean canManage(UUID uuid) {
        Role r = role(uuid);
        return r == Role.OWNER || r == Role.OFFICER;
    }

    public List<UUID> members(String partyKey) {
        ConfigurationSection sec = db.parties.getConfigurationSection("parties." + partyKey + ".members");
        if (sec == null) return new ArrayList<>();
        List<UUID> out = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            try { out.add(UUID.fromString(key)); } catch (Exception ignored) {}
        }
        return out;
    }

    public int memberCount(String partyKey) { return members(partyKey).size(); }

    public int rep(String partyKey) {
        return db.parties.getInt("parties." + partyKey + ".reputation", 0);
    }

    public void addRep(String partyKey, int amount) {
        if (!exists(partyKey)) return;
        int oldLevel = level(partyKey);
        int next = Math.max(0, rep(partyKey) + amount);
        db.parties.set("parties." + partyKey + ".reputation", next);
        int newLevel = level(partyKey);
        if (newLevel != oldLevel) {
            broadcastParty(partyKey, prefix + " &fParty naik ke &bLevel " + newLevel + "&f! Slot: &b" + memberLimit(partyKey));
        }
    }

    public int level(String partyKey) {
        int r = rep(partyKey);
        if (r >= plugin.getConfig().getInt("levels.5.reputation", 3000)) return 5;
        if (r >= plugin.getConfig().getInt("levels.4.reputation", 1500)) return 4;
        if (r >= plugin.getConfig().getInt("levels.3.reputation", 750)) return 3;
        if (r >= plugin.getConfig().getInt("levels.2.reputation", 250)) return 2;
        return 1;
    }

    public int memberLimit(String partyKey) {
        int lv = level(partyKey);
        int configured = plugin.getConfig().getInt("levels." + lv + ".slots", switch (lv) {
            case 2 -> 10; case 3 -> 12; case 4 -> 15; case 5 -> 20; default -> 8;
        });
        return Math.min(plugin.getConfig().getInt("party.max-members", 20), configured);
    }

    public int nextLevelRep(String partyKey) {
        int lv = level(partyKey);
        if (lv >= 5) return -1;
        return plugin.getConfig().getInt("levels." + (lv + 1) + ".reputation");
    }

    public void create(Player player, String name) {
        if (inParty(player.getUniqueId())) {
            player.sendMessage(prefix + Util.color(" &cKamu sudah memiliki Party.")); return;
        }
        int min = plugin.getConfig().getInt("party.min-name-length", 3);
        int max = plugin.getConfig().getInt("party.max-name-length", 16);
        if (!Util.validPartyName(name, min, max)) {
            player.sendMessage(prefix + Util.color(" &cNama Party hanya boleh A-Z, 0-9, underscore, panjang " + min + "-" + max + ".")); return;
        }
        String key = Util.key(name);
        if (exists(key)) { player.sendMessage(prefix + Util.color(" &cNama Party sudah digunakan.")); return; }
        String base = "parties." + key;
        db.parties.set(base + ".display", name);
        db.parties.set(base + ".owner", player.getUniqueId().toString());
        db.parties.set(base + ".created-at", System.currentTimeMillis());
        db.parties.set(base + ".reputation", 0);
        db.parties.set(base + ".relic.missions", 0);
        addMemberRaw(key, player.getUniqueId(), player.getName(), Role.OWNER);
        plugin.saveDataSoon();
        player.sendMessage(prefix + Util.color(" &aParty &f" + name + " &aberhasil dibuat."));
    }

    private void addMemberRaw(String partyKey, UUID uuid, String name, Role role) {
        String base = "parties." + partyKey + ".members." + uuid;
        db.parties.set(base + ".name", name == null ? uuid.toString() : name);
        db.parties.set(base + ".role", role.name());
        db.parties.set(base + ".joined-at", System.currentTimeMillis());
        db.parties.set("players." + uuid + ".party", partyKey);
    }

    private void removeMemberRaw(String partyKey, UUID uuid) {
        db.parties.set("parties." + partyKey + ".members." + uuid, null);
        db.parties.set("players." + uuid, null);
    }

    public void invite(Player inviter, Player target) {
        String party = partyOf(inviter.getUniqueId());
        if (party == null) { inviter.sendMessage(prefix + Util.color(" &cKamu belum punya Party.")); return; }
        if (!canManage(inviter.getUniqueId())) { inviter.sendMessage(prefix + Util.color(" &cHanya Owner/Officer yang dapat invite.")); return; }
        if (inParty(target.getUniqueId())) { inviter.sendMessage(prefix + Util.color(" &cPlayer tersebut sudah memiliki Party.")); return; }
        if (memberCount(party) >= memberLimit(party)) { inviter.sendMessage(prefix + Util.color(" &cSlot Party penuh.")); return; }
        long expires = System.currentTimeMillis() + plugin.getConfig().getLong("party.invite-expire-seconds", 60L) * 1000L;
        invites.put(target.getUniqueId(), new Invite(party, inviter.getUniqueId(), expires));
        inviter.sendMessage(prefix + Util.color(" &aUndangan dikirim ke &f" + target.getName() + "&a."));
        target.sendMessage(prefix + Util.color(" &f" + inviter.getName() + " &7mengundangmu ke Party &b" + display(party) + "&7. Ketik &f/party accept&7."));
    }

    public void accept(Player player) {
        Invite inv = invites.remove(player.getUniqueId());
        if (inv == null || inv.expiresAt() < System.currentTimeMillis()) {
            player.sendMessage(prefix + Util.color(" &cTidak ada undangan Party aktif.")); return;
        }
        if (inParty(player.getUniqueId())) { player.sendMessage(prefix + Util.color(" &cKamu sudah memiliki Party.")); return; }
        if (!exists(inv.partyKey()) || memberCount(inv.partyKey()) >= memberLimit(inv.partyKey())) {
            player.sendMessage(prefix + Util.color(" &cUndangan sudah tidak valid atau Party penuh.")); return;
        }
        addMemberRaw(inv.partyKey(), player.getUniqueId(), player.getName(), Role.MEMBER);
        plugin.saveDataSoon();
        broadcastParty(inv.partyKey(), prefix + " &f" + player.getName() + " &atelah bergabung ke Party.");
    }

    public void leave(Player player) {
        String party = partyOf(player.getUniqueId());
        if (party == null) { player.sendMessage(prefix + Util.color(" &cKamu belum punya Party.")); return; }
        if (plugin.war().membershipLocked()) { player.sendMessage(prefix + Util.color(" &cMembership dikunci selama Party War aktif/prepare.")); return; }
        if (player.getUniqueId().equals(owner(party))) {
            player.sendMessage(prefix + Util.color(" &cOwner tidak bisa leave. Gunakan /party disband.")); return;
        }
        removeMemberRaw(party, player.getUniqueId());
        plugin.saveDataSoon();
        broadcastParty(party, prefix + " &f" + player.getName() + " &ckeluar dari Party.");
        player.sendMessage(prefix + Util.color(" &aKamu keluar dari Party."));
    }

    public void disband(Player player) {
        String party = partyOf(player.getUniqueId());
        if (party == null || !player.getUniqueId().equals(owner(party))) {
            player.sendMessage(prefix + Util.color(" &cHanya Owner dapat membubarkan Party.")); return;
        }
        if (plugin.war().membershipLocked()) { player.sendMessage(prefix + Util.color(" &cTidak dapat disband selama Party War.")); return; }
        for (UUID uuid : members(party)) db.parties.set("players." + uuid, null);
        db.parties.set("parties." + party, null);
        plugin.saveDataSoon();
        Bukkit.broadcastMessage(prefix + Util.color(" &cParty &f" + display(party) + " &ctelah dibubarkan."));
    }

    public void kick(Player actor, OfflinePlayer target) {
        String party = partyOf(actor.getUniqueId());
        if (party == null || !canManage(actor.getUniqueId())) { actor.sendMessage(prefix + Util.color(" &cTidak punya akses.")); return; }
        if (plugin.war().membershipLocked()) { actor.sendMessage(prefix + Util.color(" &cMembership dikunci selama Party War.")); return; }
        UUID tuid = target.getUniqueId();
        if (!party.equals(partyOf(tuid))) { actor.sendMessage(prefix + Util.color(" &cTarget bukan member Party-mu.")); return; }
        if (tuid.equals(owner(party))) { actor.sendMessage(prefix + Util.color(" &cOwner tidak bisa dikick.")); return; }
        if (role(actor.getUniqueId()) == Role.OFFICER && role(tuid) == Role.OFFICER) {
            actor.sendMessage(prefix + Util.color(" &cOfficer tidak bisa mengeluarkan Officer lain.")); return;
        }
        removeMemberRaw(party, tuid);
        plugin.saveDataSoon();
        broadcastParty(party, prefix + " &f" + target.getName() + " &cdikeluarkan dari Party.");
        if (target.isOnline() && target.getPlayer() != null) target.getPlayer().sendMessage(prefix + Util.color(" &cKamu dikeluarkan dari Party."));
    }

    public void setRole(Player actor, OfflinePlayer target, Role newRole) {
        String party = partyOf(actor.getUniqueId());
        if (party == null || !actor.getUniqueId().equals(owner(party))) {
            actor.sendMessage(prefix + Util.color(" &cHanya Owner yang dapat mengubah role.")); return;
        }
        UUID id = target.getUniqueId();
        if (!party.equals(partyOf(id)) || id.equals(owner(party))) {
            actor.sendMessage(prefix + Util.color(" &cTarget tidak valid.")); return;
        }
        db.parties.set("parties." + party + ".members." + id + ".role", newRole.name());
        plugin.saveDataSoon();
        broadcastParty(party, prefix + " &f" + target.getName() + " &7sekarang &b" + newRole.name() + "&7.");
    }

    public void setHome(Player player) {
        String party = partyOf(player.getUniqueId());
        if (party == null || !canManage(player.getUniqueId())) { player.sendMessage(prefix + Util.color(" &cHanya Owner/Officer yang dapat sethome.")); return; }
        Location l = player.getLocation();
        String base = "parties." + party + ".home";
        db.parties.set(base + ".world", l.getWorld() == null ? null : l.getWorld().getName());
        db.parties.set(base + ".x", l.getX()); db.parties.set(base + ".y", l.getY()); db.parties.set(base + ".z", l.getZ());
        db.parties.set(base + ".yaw", (double) l.getYaw()); db.parties.set(base + ".pitch", (double) l.getPitch());
        plugin.saveDataSoon();
        player.sendMessage(prefix + Util.color(" &aParty Home disimpan."));
    }

    public void home(Player player) {
        String party = partyOf(player.getUniqueId());
        if (party == null) { player.sendMessage(prefix + Util.color(" &cKamu belum punya Party.")); return; }
        String base = "parties." + party + ".home";
        String world = db.parties.getString(base + ".world");
        if (world == null || Bukkit.getWorld(world) == null) { player.sendMessage(prefix + Util.color(" &cParty Home belum diset.")); return; }
        Location dest = new Location(Bukkit.getWorld(world), db.parties.getDouble(base + ".x"), db.parties.getDouble(base + ".y"), db.parties.getDouble(base + ".z"), (float) db.parties.getDouble(base + ".yaw"), (float) db.parties.getDouble(base + ".pitch"));
        Location start = player.getLocation().clone();
        long token = System.nanoTime(); homeTeleportToken.put(player.getUniqueId(), token);
        int delay = plugin.getConfig().getInt("party.home-delay-seconds", 3);
        player.sendMessage(prefix + Util.color(" &7Teleport dalam &f" + delay + " detik&7. Jangan bergerak."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !Objects.equals(homeTeleportToken.get(player.getUniqueId()), token)) return;
            if (player.getLocation().getWorld() != start.getWorld() || player.getLocation().distanceSquared(start) > 0.25) {
                player.sendMessage(prefix + Util.color(" &cTeleport dibatalkan karena kamu bergerak.")); return;
            }
            player.teleport(dest); player.sendMessage(prefix + Util.color(" &aTeleport ke Party Home."));
        }, delay * 20L);
    }

    public void partyChat(Player player, String message) {
        String party = partyOf(player.getUniqueId());
        if (party == null) { player.sendMessage(prefix + Util.color(" &cKamu belum punya Party.")); return; }
        String line = Util.color("&b[PARTY] &7" + player.getName() + " &8» &f" + message);
        for (UUID uuid : members(party)) {
            Player p = Bukkit.getPlayer(uuid); if (p != null && p.isOnline()) p.sendMessage(line);
        }
    }

    public void broadcastParty(String partyKey, String message) {
        for (UUID uuid : members(partyKey)) {
            Player p = Bukkit.getPlayer(uuid); if (p != null && p.isOnline()) p.sendMessage(Util.color(message));
        }
    }

    public List<String> rankedParties(String metric, int limit) {
        ConfigurationSection sec = db.parties.getConfigurationSection("parties");
        if (sec == null) return new ArrayList<>();
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        keys.sort((a,b) -> Integer.compare(db.parties.getInt("parties." + b + "." + metric, 0), db.parties.getInt("parties." + a + "." + metric, 0)));
        return keys.subList(0, Math.min(limit, keys.size()));
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8MENKIESTES Party"));
        String party = partyOf(player.getUniqueId());
        if (party == null) {
            inv.setItem(13, Util.item(Material.BOOK, "&bBelum punya Party", "&7Gunakan &f/party create <nama>"));
        } else {
            inv.setItem(10, Util.item(Material.PLAYER_HEAD, "&b" + display(party), "&7Role: &f" + role(player.getUniqueId()), "&7Member: &f" + memberCount(party) + "/" + memberLimit(party)));
            inv.setItem(12, Util.item(Material.EXPERIENCE_BOTTLE, "&bLevel &f" + level(party), "&7Reputation: &f" + rep(party), nextLevelRep(party) < 0 ? "&aMAX LEVEL" : "&7Next: &f" + nextLevelRep(party)));
            inv.setItem(14, Util.item(Material.NETHER_STAR, "&dParty Relic Lv." + relicLevel(party), "&7Total misi selesai: &f" + relicMissions(party), "&7Relic naik otomatis dari Weekly Quest."));
            inv.setItem(16, Util.item(Material.WRITABLE_BOOK, "&eWeekly Quest", questLine(party, "mining"), questLine(party, "hunter"), questLine(party, "farmer")));
            inv.setItem(22, Util.item(Material.COMPASS, "&cParty War", "&7Berlangsung langsung di world &f" + plugin.getConfig().getString("war.score-world", "world"), "&7/partywar status"));
        }
        player.openInventory(inv);
    }

    public String questLine(String party, String type) {
        int progress = questProgress(party, type); int goal = questGoal(type);
        boolean done = db.parties.getBoolean("parties." + party + ".quests." + type + ".completed", false);
        return "&7" + Character.toUpperCase(type.charAt(0)) + type.substring(1) + ": &f" + Math.min(progress, goal) + "/" + goal + (done ? " &a✓" : "");
    }

    public int questGoal(String type) { return plugin.getConfig().getInt("quests." + type + ".goal", switch(type){case "hunter"->100;case "farmer"->300;default->500;}); }
    public int questReward(String type) { return plugin.getConfig().getInt("quests." + type + ".reputation", switch(type){case "hunter"->120;case "farmer"->80;default->100;}); }
    public int questProgress(String party, String type) { return db.parties.getInt("parties." + party + ".quests." + type + ".progress", 0); }

    public void addQuestProgress(String party, String type, UUID contributor, int amount) {
        if (!exists(party) || db.parties.getBoolean("parties." + party + ".quests." + type + ".completed", false)) return;
        String base = "parties." + party + ".quests." + type;
        int value = db.parties.getInt(base + ".progress", 0) + amount;
        db.parties.set(base + ".progress", value);
        String cbase = "parties." + party + ".members." + contributor + ".contribution." + type;
        db.parties.set(cbase, db.parties.getInt(cbase, 0) + amount);
        if (value >= questGoal(type)) {
            db.parties.set(base + ".completed", true);
            db.parties.set(base + ".completed-at", System.currentTimeMillis());
            addRep(party, questReward(type));
            int missions = relicMissions(party) + 1;
            db.parties.set("parties." + party + ".relic.missions", missions);
            broadcastParty(party, prefix + " &aWeekly Quest &f" + type + " &aselesai! &e+" + questReward(type) + " Rep &8| &dRelic progress +1");
        }
    }

    public int relicMissions(String party) { return db.parties.getInt("parties." + party + ".relic.missions", 0); }
    public int relicLevel(String party) {
        int m = relicMissions(party);
        if (m >= plugin.getConfig().getInt("relic.level-5-missions", 30)) return 5;
        if (m >= plugin.getConfig().getInt("relic.level-4-missions", 18)) return 4;
        if (m >= plugin.getConfig().getInt("relic.level-3-missions", 9)) return 3;
        if (m >= plugin.getConfig().getInt("relic.level-2-missions", 3)) return 2;
        return 1;
    }

    public void resetWeeklyQuests(boolean announce) {
        ConfigurationSection sec = db.parties.getConfigurationSection("parties");
        if (sec == null) return;
        for (String party : sec.getKeys(false)) {
            for (String type : List.of("mining","hunter","farmer")) {
                String base = "parties." + party + ".quests." + type;
                db.parties.set(base + ".progress", 0); db.parties.set(base + ".completed", false); db.parties.set(base + ".completed-at", null);
            }
            ConfigurationSection mem = db.parties.getConfigurationSection("parties." + party + ".members");
            if (mem != null) for (String u : mem.getKeys(false)) {
                db.parties.set("parties." + party + ".members." + u + ".contribution.mining", 0);
                db.parties.set("parties." + party + ".members." + u + ".contribution.hunter", 0);
                db.parties.set("parties." + party + ".members." + u + ".contribution.farmer", 0);
            }
        }
        db.parties.set("meta.last-weekly-reset", LocalDate.now().toString());
        placedMining.clear(); plugin.saveDataSoon();
        if (announce) Bukkit.broadcastMessage(prefix + Util.color(" &eWeekly Party Quest telah direset. Progress Relic tetap tersimpan."));
    }

    public void tickWeeklyReset() {
        if (!plugin.getConfig().getBoolean("weekly-reset.enabled", true)) return;
        DayOfWeek day;
        try { day = DayOfWeek.valueOf(plugin.getConfig().getString("weekly-reset.day", "MONDAY").toUpperCase(Locale.ROOT)); }
        catch (Exception e) { day = DayOfWeek.MONDAY; }
        LocalTime time;
        try { time = LocalTime.parse(plugin.getConfig().getString("weekly-reset.time", "12:00"), DateTimeFormatter.ofPattern("HH:mm")); }
        catch (Exception e) { time = LocalTime.NOON; }
        LocalDateTime now = LocalDateTime.now();
        if (now.getDayOfWeek() != day || now.toLocalTime().isBefore(time)) return;
        String today = now.toLocalDate().toString();
        if (today.equals(db.parties.getString("meta.last-weekly-reset"))) return;
        resetWeeklyQuests(true);
    }

    public void trackPlacedMining(Location location) {
        if (!plugin.getConfig().getBoolean("quests.ignore-player-placed-mining", true)) return;
        placedMining.add(locationKey(location));
        int cap = plugin.getConfig().getInt("quests.placed-ore-memory-cap", 3000);
        if (placedMining.size() > cap) {
            Iterator<String> it = placedMining.iterator(); if (it.hasNext()) { it.next(); it.remove(); }
        }
    }

    public boolean consumePlacedMining(Location location) {
        return placedMining.remove(locationKey(location));
    }

    private String locationKey(Location l) {
        return (l.getWorld()==null?"?":l.getWorld().getName()) + ":" + l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ();
    }

    public boolean isMiningMaterial(Material m) {
        return switch (m) {
            case STONE, COBBLESTONE, DEEPSLATE, COAL_ORE, DEEPSLATE_COAL_ORE, IRON_ORE, DEEPSLATE_IRON_ORE,
                    COPPER_ORE, DEEPSLATE_COPPER_ORE, GOLD_ORE, DEEPSLATE_GOLD_ORE, REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                    LAPIS_ORE, DEEPSLATE_LAPIS_ORE, DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                    NETHER_GOLD_ORE, NETHER_QUARTZ_ORE, ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    public boolean shouldTrackPlaced(Material m) {
        return isMiningMaterial(m) && m != Material.STONE && m != Material.COBBLESTONE && m != Material.DEEPSLATE;
    }

    public void addWarChestTickets(String party, int amount) {
        for (UUID u : members(party)) {
            String path = "players." + u + ".war-chests";
            db.parties.set(path, Math.min(16, db.parties.getInt(path, 0) + amount));
        }
    }

    public int warChestTickets(UUID u) { return db.parties.getInt("players." + u + ".war-chests", 0); }

    public void claimWarChest(Player p) {
        int n = warChestTickets(p.getUniqueId());
        if (n <= 0) { p.sendMessage(prefix + Util.color(" &cKamu tidak punya Party War Reward Chest.")); return; }
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(
                new ItemStack(Material.DIAMOND, plugin.getConfig().getInt("war.reward.diamonds", 8)),
                new ItemStack(Material.EMERALD, plugin.getConfig().getInt("war.reward.emeralds", 16)),
                new ItemStack(Material.GOLD_INGOT, plugin.getConfig().getInt("war.reward.gold-ingots", 32)),
                new ItemStack(Material.GOLDEN_APPLE, plugin.getConfig().getInt("war.reward.golden-apples", 2)));
        for (ItemStack item : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), item);
        db.parties.set("players." + p.getUniqueId() + ".war-chests", n - 1);
        plugin.saveDataSoon();
        p.sendMessage(prefix + Util.color(" &aParty War Reward Chest berhasil diclaim. Sisa: &f" + (n-1)));
    }

    public void showContribution(Player p) {
        String party = partyOf(p.getUniqueId());
        if (party == null) { p.sendMessage(prefix + Util.color(" &cKamu belum punya Party.")); return; }
        p.sendMessage(Util.color("&8&m-----------------------------"));
        p.sendMessage(Util.color("&b&lPARTY CONTRIBUTION &7- " + display(party)));
        List<UUID> mem = members(party);
        mem.sort((a,b) -> Integer.compare(totalContribution(party,b), totalContribution(party,a)));
        int i=1; for (UUID u : mem) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            p.sendMessage(Util.color("&e#" + i++ + " &f" + (op.getName()==null?u.toString().substring(0,8):op.getName()) + " &7- &b" + totalContribution(party,u)));
        }
    }

    private int totalContribution(String party, UUID u) {
        String base="parties."+party+".members."+u+".contribution.";
        return db.parties.getInt(base+"mining")+db.parties.getInt(base+"hunter")+db.parties.getInt(base+"farmer")+db.parties.getInt(base+"war-kills");
    }
}
