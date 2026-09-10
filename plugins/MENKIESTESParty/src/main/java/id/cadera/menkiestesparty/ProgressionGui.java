package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * v1.2.1 inventory UI for the v1.2 progression layer.
 * Commands remain available; this GUI is an optional usability layer.
 */
public final class ProgressionGui implements Listener {
    private static final String HUB = "Party Progression";
    private static final String PROFILE = "Party Profile";
    private static final String PROJECTS = "Party Projects";
    private static final String SKILLS = "Party Skills";
    private static final String DIVISIONS = "Party Divisions";
    private static final String IDENTITY = "Party Identity";
    private static final String DIVISION_MEMBERS = "Division Members";
    private static final String SET_DIVISION = "Set Division";
    private static final String TAG_PREFIX = "MENKI:";

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final ProgressionManager progression;

    public ProgressionGui(MENKIESTESPartyPlugin plugin, PartyService parties, ProgressionManager progression) {
        this.plugin = plugin;
        this.parties = parties;
        this.progression = progression;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("gui.progression.enabled", true);
    }

    public void openHub(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        if (!enabled()) {
            player.sendMessage(parties.prefix() + Util.color(" &cProgression GUI dinonaktifkan. Gunakan /party profile."));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + HUB));
        inv.setItem(10, tagged(Material.NAME_TAG, "&bParty Profile", "profile",
                "&7Ringkasan Party, level, XP dan progression.",
                "&aKlik untuk buka."));
        inv.setItem(12, moduleButton("projects", Material.DIAMOND_PICKAXE, "&6Party Projects", "projects",
                "&7Objective besar yang dikerjakan bersama Party."));
        inv.setItem(14, moduleButton("skill-tree", Material.NETHER_STAR, "&dParty Skill Tree", "skills",
                "&7Gunakan Skill Point untuk perkembangan Party."));
        inv.setItem(16, moduleButton("divisions", Material.SHIELD, "&bParty Divisions", "divisions",
                "&7Spesialisasi tambahan untuk setiap member."));
        inv.setItem(22, moduleButton("identity", Material.COMPASS, "&eDynamic Identity", "identity",
                "&7Identitas otomatis dari aktivitas Party."));
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "back-root", "&7Kembali ke menu Party."));
        player.openInventory(inv);
    }

    public void openProfile(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + PROFILE));

        int next = parties.nextLevelRep(party);
        inv.setItem(10, Util.item(Material.EXPERIENCE_BOTTLE, "&bLevel " + parties.level(party),
                "&7Party XP: &f" + parties.rep(party),
                next < 0 ? "&aMAX LEVEL" : "&7Next Level: &f" + next + " XP",
                "&7Member: &f" + parties.memberCount(party) + "/" + parties.memberLimit(party)));

        if (progression.moduleEnabled("projects")) {
            String id = progression.currentProject(party);
            if (id == null) {
                inv.setItem(12, tagged(Material.DIAMOND_PICKAXE, "&6Project", "projects",
                        "&7Tidak ada Project aktif.", "&aKlik untuk memilih Project."));
            } else {
                inv.setItem(12, tagged(Material.DIAMOND_PICKAXE, "&6" + progression.projectName(id), "projects",
                        "&7Progress: &f" + (int)Math.floor(progression.projectProgress(party)) + "/" + progression.projectGoal(id),
                        "&7Type: &f" + progression.projectType(id), "&aKlik untuk detail."));
            }
        }

        if (progression.moduleEnabled("skill-tree")) {
            inv.setItem(14, tagged(Material.NETHER_STAR, "&dSkill Tree", "skills",
                    "&7Skill Point tersedia: &d" + progression.availableSkillPoints(party),
                    "&aKlik untuk buka."));
        }
        if (progression.moduleEnabled("divisions")) {
            inv.setItem(16, tagged(Material.SHIELD, "&bDivision", "divisions",
                    "&7Divisimu: &f" + progression.divisionDisplay(progression.divisionOf(player.getUniqueId())),
                    "&aKlik untuk buka."));
        }
        if (progression.moduleEnabled("identity")) {
            inv.setItem(22, tagged(Material.COMPASS, "&e" + progression.identityDisplay(progression.identity(party)), "identity",
                    "&7Dynamic Party Identity", "&aKlik untuk lihat aktivitas."));
        }
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openProjects(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        if (!progression.moduleEnabled("projects")) { moduleDisabled(player, "Party Projects"); return; }

        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + PROJECTS));
        String active = progression.currentProject(party);
        if (active != null) {
            int goal = progression.projectGoal(active);
            int value = (int)Math.floor(progression.projectProgress(party));
            int percent = goal <= 0 ? 0 : Math.min(100, (int)Math.floor(value * 100.0 / goal));
            inv.setItem(4, Util.item(Material.BEACON, "&6&lACTIVE: " + progression.projectName(active),
                    "&7Type: &f" + progression.projectType(active),
                    "&7Progress: &f" + value + "/" + goal + " &8(&b" + percent + "%&8)",
                    "&7Base reward: &b+" + progression.projectReward(active) + " Party XP"));
            if (parties.canManage(player.getUniqueId())) {
                inv.setItem(48, tagged(Material.BARRIER, "&cCancel Project", "project-cancel",
                        "&7Batalkan Project aktif.", "&cProgress saat ini akan hilang."));
            }
        } else {
            inv.setItem(4, Util.item(Material.PAPER, "&7Belum ada Project aktif",
                    parties.canManage(player.getUniqueId()) ? "&aPilih Project di bawah untuk memulai." : "&7Owner/Officer dapat memulai Project."));
        }

        int slot = 9;
        for (String id : progression.projectIds()) {
            if (slot >= 45) break;
            boolean isActive = id.equalsIgnoreCase(active);
            List<String> lore = new ArrayList<>();
            lore.add("&7ID: &f" + id);
            lore.add("&7Type: &f" + progression.projectType(id));
            lore.add("&7Goal: &f" + progression.projectGoal(id));
            lore.add("&7Reward: &b+" + progression.projectReward(id) + " Party XP");
            if (isActive) lore.add("&6Sedang aktif.");
            else if (active != null) lore.add("&8Selesaikan/cancel Project aktif dulu.");
            else if (parties.canManage(player.getUniqueId())) lore.add("&aKlik untuk mulai.");
            else lore.add("&8Hanya Owner/Officer yang dapat memulai.");
            inv.setItem(slot++, tagged(Material.CHEST, "&6" + progression.projectName(id), "project-start:" + id, lore.toArray(String[]::new)));
        }
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openSkills(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        if (!progression.moduleEnabled("skill-tree")) { moduleDisabled(player, "Party Skill Tree"); return; }

        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + SKILLS));
        inv.setItem(4, Util.item(Material.NETHER_STAR, "&d&lPARTY SKILL TREE",
                "&7Party Level: &f" + parties.level(party),
                "&7Skill Point tersedia: &d" + progression.availableSkillPoints(party),
                player.getUniqueId().equals(parties.owner(party)) ? "&aKlik node terkunci untuk unlock." : "&7Hanya Owner yang dapat unlock."));

        int slot = 9;
        for (String node : progression.skillNodeIds()) {
            if (slot >= 45) break;
            boolean unlocked = progression.hasSkill(party, node);
            String base = "progression.skill-tree.nodes." + node;
            String name = plugin.getConfig().getString(base + ".display-name", node);
            String branch = plugin.getConfig().getString(base + ".branch", "CORE");
            int minLevel = Math.max(1, plugin.getConfig().getInt(base + ".min-level", 1));
            String requires = plugin.getConfig().getString(base + ".requires", "");
            String status = unlocked ? "&aUNLOCKED" : "&8LOCKED";
            inv.setItem(slot++, tagged(skillMaterial(branch, unlocked), "&d" + name, "skill-unlock:" + node,
                    "&7Branch: &f" + branch,
                    "&7Status: " + status,
                    "&7Min Level: &f" + minLevel,
                    requires == null || requires.isBlank() ? "&7Requires: &8-" : "&7Requires: &f" + requires,
                    "&7Effect: &f" + skillEffectSummary(node),
                    unlocked ? "&8Sudah terbuka." : "&aKlik untuk mencoba unlock."));
        }
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openDivisions(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        if (!progression.moduleEnabled("divisions")) { moduleDisabled(player, "Party Divisions"); return; }

        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + DIVISIONS));
        String current = progression.divisionOf(player.getUniqueId());
        boolean selfSelect = plugin.getConfig().getBoolean("progression.divisions.allow-self-select", true);
        inv.setItem(4, Util.item(Material.SHIELD, "&b&lPARTY DIVISIONS",
                "&7Divisimu: &f" + progression.divisionDisplay(current),
                selfSelect ? "&aKlik salah satu divisi untuk memilih." : "&7Self-select dinonaktifkan server."));

        int slot = 10;
        for (String id : progression.divisionIds()) {
            if (slot >= 44) break;
            int count = 0;
            for (UUID member : parties.members(party)) if (progression.divisionOf(member).equalsIgnoreCase(id)) count++;
            boolean selected = current.equalsIgnoreCase(id);
            inv.setItem(slot++, tagged(Material.SHIELD, "&b" + progression.divisionDisplay(id), "division-join:" + id,
                    "&7Member: &f" + count,
                    "&7Bonus: &f" + divisionEffectSummary(id),
                    selected ? "&aDivisimu saat ini." : (selfSelect ? "&aKlik untuk bergabung." : "&8Tidak dapat dipilih sendiri.")));
        }
        if (!current.equalsIgnoreCase("none")) {
            inv.setItem(40, tagged(Material.GRAY_DYE, "&7Keluar dari Division", "division-join:none", "&7Kembali tanpa spesialisasi."));
        }
        if (parties.canManage(player.getUniqueId())) {
            inv.setItem(48, tagged(Material.PLAYER_HEAD, "&eKelola Division Member", "division-members",
                    "&7Owner/Officer dapat mengatur divisi member."));
        }
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openIdentity(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        if (!progression.moduleEnabled("identity")) { moduleDisabled(player, "Dynamic Identity"); return; }

        Map<String, Integer> totals = progression.activityTotals(party);
        int total = totals.values().stream().mapToInt(Integer::intValue).sum();
        int window = Math.max(1, plugin.getConfig().getInt("progression.identity.window-days", 30));
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + IDENTITY));
        inv.setItem(4, Util.item(Material.COMPASS, "&e&l" + progression.identityDisplay(progression.identity(party)),
                "&7Aktivitas dihitung dari &f" + window + " hari &7terakhir.",
                "&7Total activity score: &f" + total,
                "&8Identity berubah otomatis, bukan dipilih manual."));
        inv.setItem(10, Util.item(Material.IRON_SWORD, "&cCombat", "&7Activity score: &f" + totals.getOrDefault("combat", 0)));
        inv.setItem(12, Util.item(Material.DIAMOND_PICKAXE, "&bMining", "&7Activity score: &f" + totals.getOrDefault("mining", 0)));
        inv.setItem(14, Util.item(Material.WHEAT, "&aFarming", "&7Activity score: &f" + totals.getOrDefault("farming", 0)));
        inv.setItem(16, Util.item(Material.BEACON, "&6Projects", "&7Activity score: &f" + totals.getOrDefault("projects", 0)));
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openDivisionMembers(Player actor) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!parties.canManage(actor.getUniqueId())) {
            actor.sendMessage(parties.prefix() + Util.color(" &cHanya Owner/Officer yang dapat mengatur divisi member."));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + DIVISION_MEMBERS));
        int slot = 0;
        for (UUID uuid : parties.members(party)) {
            if (slot >= 45) break;
            inv.setItem(slot++, tagged(Material.PLAYER_HEAD, "&b" + memberName(uuid), "division-member:" + uuid,
                    "&7Role: &f" + parties.role(uuid),
                    "&7Division: &f" + progression.divisionDisplay(progression.divisionOf(uuid)),
                    "&aKlik untuk mengatur."));
        }
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "divisions", "&7Kembali ke menu Division."));
        actor.openInventory(inv);
    }

    public void openMemberDivisionMenu(Player actor, UUID target) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!parties.canManage(actor.getUniqueId()) || !party.equals(parties.partyOf(target))) {
            actor.sendMessage(parties.prefix() + Util.color(" &cAkses pengaturan divisi tidak valid."));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + SET_DIVISION));
        inv.setItem(4, Util.item(Material.PLAYER_HEAD, "&b" + memberName(target),
                "&7Division saat ini: &f" + progression.divisionDisplay(progression.divisionOf(target))));
        int slot = 10;
        for (String id : progression.divisionIds()) {
            if (slot >= 17) break;
            inv.setItem(slot++, tagged(Material.SHIELD, "&b" + progression.divisionDisplay(id), "division-assign:" + target + ":" + id,
                    "&7Bonus: &f" + divisionEffectSummary(id), "&aKlik untuk assign."));
        }
        inv.setItem(22, tagged(Material.GRAY_DYE, "&7No Division", "division-assign:" + target + ":none", "&7Hapus spesialisasi member."));
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "division-members", "&7Kembali ke daftar member."));
        actor.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = cleanTitle(event.getView().getTitle());
        if (!isOurTitle(title)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        String action = action(event.getCurrentItem());
        if (action == null) return;

        if (action.equals("back-root")) { parties.openMenu(player); return; }
        if (action.equals("hub")) { openHub(player); return; }
        if (action.equals("profile")) { openProfile(player); return; }
        if (action.equals("projects")) { openProjects(player); return; }
        if (action.equals("skills")) { openSkills(player); return; }
        if (action.equals("divisions")) { openDivisions(player); return; }
        if (action.equals("identity")) { openIdentity(player); return; }
        if (action.equals("project-cancel")) { progression.cancelProject(player); openProjects(player); return; }
        if (action.startsWith("project-start:")) {
            progression.startProject(player, action.substring("project-start:".length()));
            openProjects(player); return;
        }
        if (action.startsWith("skill-unlock:")) {
            progression.unlockSkill(player, action.substring("skill-unlock:".length()));
            openSkills(player); return;
        }
        if (action.startsWith("division-join:")) {
            progression.chooseDivision(player, action.substring("division-join:".length()));
            openDivisions(player); return;
        }
        if (action.equals("division-members")) { openDivisionMembers(player); return; }
        if (action.startsWith("division-member:")) {
            try { openMemberDivisionMenu(player, UUID.fromString(action.substring("division-member:".length()))); }
            catch (IllegalArgumentException ignored) { openDivisionMembers(player); }
            return;
        }
        if (action.startsWith("division-assign:")) {
            String payload = action.substring("division-assign:".length());
            int cut = payload.lastIndexOf(':');
            if (cut > 0) {
                try {
                    UUID target = UUID.fromString(payload.substring(0, cut));
                    String id = payload.substring(cut + 1);
                    progression.assignDivision(player, Bukkit.getOfflinePlayer(target), id);
                    openMemberDivisionMenu(player, target);
                } catch (IllegalArgumentException ignored) { openDivisionMembers(player); }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isOurTitle(cleanTitle(event.getView().getTitle()))) event.setCancelled(true);
    }

    private ItemStack moduleButton(String module, Material material, String name, String action, String description) {
        if (!progression.moduleEnabled(module)) {
            return Util.item(Material.BARRIER, name, description, "&cModule dinonaktifkan server." );
        }
        return tagged(material, name, action, description, "&aKlik untuk buka.");
    }

    private Material skillMaterial(String branch, boolean unlocked) {
        if (unlocked) return Material.ENCHANTED_BOOK;
        return switch (branch == null ? "" : branch.toUpperCase(Locale.ROOT)) {
            case "COMBAT" -> Material.IRON_SWORD;
            case "LABOR" -> Material.IRON_PICKAXE;
            case "COMMAND" -> Material.BEACON;
            default -> Material.BOOK;
        };
    }

    private String skillEffectSummary(String node) {
        String base = "progression.skill-tree.nodes." + node + ".effects.";
        List<String> effects = new ArrayList<>();
        int hunter = plugin.getConfig().getInt(base + "hunter-progress-bonus-percent", 0);
        int resource = plugin.getConfig().getInt(base + "resource-progress-bonus-percent", 0);
        int all = plugin.getConfig().getInt(base + "all-project-progress-bonus-percent", 0);
        int xp = plugin.getConfig().getInt(base + "project-xp-bonus-percent", 0);
        if (hunter != 0) effects.add("Hunter +" + hunter + "%");
        if (resource != 0) effects.add("Resource +" + resource + "%");
        if (all != 0) effects.add("All Project +" + all + "%");
        if (xp != 0) effects.add("Project XP +" + xp + "%");
        return effects.isEmpty() ? "No effect" : String.join(", ", effects);
    }

    private String divisionEffectSummary(String id) {
        String base = "progression.divisions.types." + id + ".effects.";
        List<String> effects = new ArrayList<>();
        int hunter = plugin.getConfig().getInt(base + "hunter-progress-bonus-percent", 0);
        int resource = plugin.getConfig().getInt(base + "resource-progress-bonus-percent", 0);
        int all = plugin.getConfig().getInt(base + "all-project-progress-bonus-percent", 0);
        if (hunter != 0) effects.add("Hunter +" + hunter + "%");
        if (resource != 0) effects.add("Resource +" + resource + "%");
        if (all != 0) effects.add("All Project +" + all + "%");
        return effects.isEmpty() ? "No bonus" : String.join(", ", effects);
    }

    private ItemStack tagged(Material material, String name, String action, String... lore) {
        String[] out = Arrays.copyOf(lore == null ? new String[0] : lore, (lore == null ? 0 : lore.length) + 1);
        out[out.length - 1] = "&0" + TAG_PREFIX + action;
        return Util.item(material, name, out);
    }

    private String action(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        var meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return null;
        for (String line : meta.getLore()) {
            String clean = Util.strip(line);
            if (clean != null && clean.startsWith(TAG_PREFIX)) return clean.substring(TAG_PREFIX.length());
        }
        return null;
    }

    private boolean isOurTitle(String title) {
        return title.equalsIgnoreCase(HUB) || title.equalsIgnoreCase(PROFILE) || title.equalsIgnoreCase(PROJECTS)
                || title.equalsIgnoreCase(SKILLS) || title.equalsIgnoreCase(DIVISIONS) || title.equalsIgnoreCase(IDENTITY)
                || title.equalsIgnoreCase(DIVISION_MEMBERS) || title.equalsIgnoreCase(SET_DIVISION);
    }

    private String cleanTitle(String title) {
        String clean = Util.strip(title);
        return clean == null ? "" : clean;
    }

    private String requireParty(Player player) {
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) player.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party."));
        return party;
    }

    private String memberName(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    private void moduleDisabled(Player player, String module) {
        player.sendMessage(parties.prefix() + Util.color(" &c" + module + " dinonaktifkan di server ini."));
    }
}
