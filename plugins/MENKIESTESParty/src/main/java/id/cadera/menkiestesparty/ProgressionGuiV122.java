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
 * v1.2.2 inventory UI polish for the progression layer.
 *
 * Adds pagination, confirmation screens, visual progress bars and granular
 * Bukkit permission nodes while keeping the command paths untouched.
 */
public final class ProgressionGuiV122 implements Listener {
    private static final String HUB = "Party Progression";
    private static final String PROFILE = "Party Profile";
    private static final String PROJECTS = "Party Projects";
    private static final String SKILLS = "Party Skills";
    private static final String DIVISIONS = "Party Divisions";
    private static final String IDENTITY = "Party Identity";
    private static final String LEVELS = "Party Levels";
    private static final String DIVISION_MEMBERS = "Division Members";
    private static final String SET_DIVISION = "Set Division";
    private static final String CONFIRM = "Confirm Action";
    private static final String TAG_PREFIX = "MENKI:";
    private static final int PAGE_SIZE = 36;

    private static final String PERM_GUI = "menkiestesparty.gui.progression";
    private static final String PERM_PROJECTS = "menkiestesparty.gui.projects";
    private static final String PERM_PROJECTS_MANAGE = "menkiestesparty.gui.projects.manage";
    private static final String PERM_SKILLS = "menkiestesparty.gui.skills";
    private static final String PERM_SKILLS_UNLOCK = "menkiestesparty.gui.skills.unlock";
    private static final String PERM_DIVISIONS = "menkiestesparty.gui.divisions";
    private static final String PERM_DIVISIONS_SELF = "menkiestesparty.gui.divisions.self";
    private static final String PERM_DIVISIONS_MANAGE = "menkiestesparty.gui.divisions.manage";
    private static final String PERM_IDENTITY = "menkiestesparty.gui.identity";

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final ProgressionManager progression;

    public ProgressionGuiV122(MENKIESTESPartyPlugin plugin, PartyService parties, ProgressionManager progression) {
        this.plugin = plugin;
        this.parties = parties;
        this.progression = progression;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("gui.progression.enabled", true);
    }

    public boolean canView(Player player) {
        return allowed(player, PERM_GUI);
    }

    public boolean canManageDivisions(Player player) {
        return allowed(player, PERM_DIVISIONS_MANAGE) && parties.canManage(player.getUniqueId());
    }

    public void openHub(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        if (!enabled()) {
            player.sendMessage(parties.prefix() + Util.color(" &cProgression GUI dinonaktifkan. Gunakan /party profile."));
            return;
        }
        if (!canView(player)) {
            noPermission(player);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + HUB));
        inv.setItem(10, tagged(Material.NAME_TAG, "&bParty Profile", "profile",
                "&7Ringkasan Party, level, XP dan progression.", "&aKlik untuk buka."));
        inv.setItem(12, moduleButton(player, "projects", PERM_PROJECTS, Material.DIAMOND_PICKAXE,
                "&6Party Projects", "projects", "&7Objective besar yang dikerjakan bersama Party."));
        inv.setItem(14, moduleButton(player, "skill-tree", PERM_SKILLS, Material.NETHER_STAR,
                "&dParty Skill Tree", "skills", "&7Gunakan Skill Point untuk perkembangan Party."));
        inv.setItem(16, moduleButton(player, "divisions", PERM_DIVISIONS, Material.SHIELD,
                "&bParty Divisions", "divisions", "&7Spesialisasi tambahan untuk setiap member."));
        inv.setItem(22, moduleButton(player, "identity", PERM_IDENTITY, Material.COMPASS,
                "&eDynamic Identity", "identity", "&7Identitas otomatis dari aktivitas Party."));
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "back-root", "&7Kembali ke menu Party."));
        player.openInventory(inv);
    }

    public void openProfile(Player player) {
        String party = requireParty(player);
        if (party == null || !guardGui(player)) return;
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + PROFILE));
        int level = parties.level(party);
        int currentRep = parties.rep(party);
        int next = parties.nextLevelRep(party);
        List<String> levelLore = new ArrayList<>();
        levelLore.add("&7Party XP: &f" + currentRep);
        levelLore.add(next < 0 ? "&aMAX LEVEL" : "&7Next Level: &f" + next + " XP");
        if (next < 0) levelLore.add(progressBar(1, 1));
        else {
            int currentReq = plugin.getConfig().getInt("levels." + level + ".reputation", 0);
            levelLore.add(progressBar(Math.max(0, currentRep - currentReq), Math.max(1, next - currentReq)));
        }
        levelLore.add("&7Member: &f" + parties.memberCount(party) + "/" + parties.memberLimit(party));
        levelLore.add("&aKlik untuk lihat Level Progression.");
        inv.setItem(10, tagged(Material.EXPERIENCE_BOTTLE, "&bLevel " + level, "levels", levelLore.toArray(String[]::new)));

        if (progression.moduleEnabled("projects") && allowed(player, PERM_PROJECTS)) {
            String id = progression.currentProject(party);
            if (id == null) inv.setItem(12, tagged(Material.DIAMOND_PICKAXE, "&6Project", "projects",
                    "&7Tidak ada Project aktif.", "&aKlik untuk memilih Project."));
            else {
                double value = progression.projectProgress(party);
                int goal = progression.projectGoal(id);
                inv.setItem(12, tagged(Material.DIAMOND_PICKAXE, "&6" + progression.projectName(id), "projects",
                        "&7Progress: &f" + (int)Math.floor(value) + "/" + goal,
                        progressBar(value, goal), "&7Type: &f" + progression.projectType(id), "&aKlik untuk detail."));
            }
        }
        if (progression.moduleEnabled("skill-tree") && allowed(player, PERM_SKILLS)) {
            inv.setItem(14, tagged(Material.NETHER_STAR, "&dSkill Tree", "skills",
                    "&7Skill Point tersedia: &d" + progression.availableSkillPoints(party), "&aKlik untuk buka."));
        }
        if (progression.moduleEnabled("divisions") && allowed(player, PERM_DIVISIONS)) {
            inv.setItem(16, tagged(Material.SHIELD, "&bDivision", "divisions",
                    "&7Divisimu: &f" + progression.divisionDisplay(progression.divisionOf(player.getUniqueId())), "&aKlik untuk buka."));
        }
        if (progression.moduleEnabled("identity") && allowed(player, PERM_IDENTITY)) {
            inv.setItem(22, tagged(Material.COMPASS, "&e" + progression.identityDisplay(progression.identity(party)), "identity",
                    "&7Dynamic Party Identity", "&aKlik untuk lihat aktivitas."));
        }
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openLevels(Player player) {
        String party = requireParty(player);
        if (party == null || !guardGui(player)) return;
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + LEVELS));
        int current = parties.level(party);
        int rep = parties.rep(party);
        int[] slots = {11, 12, 13, 14, 15};
        for (int level = 1; level <= 5; level++) {
            int required = plugin.getConfig().getInt("levels." + level + ".reputation", 0);
            int memberSlots = plugin.getConfig().getInt("levels." + level + ".slots", 5);
            boolean reached = current >= level;
            Material material = current == level ? Material.NETHER_STAR : reached ? Material.LIME_DYE : Material.GRAY_DYE;
            String status = current == level ? "&bCURRENT" : reached ? "&aREACHED" : "&8LOCKED";
            inv.setItem(slots[level - 1], Util.item(material, "&bParty Level " + level,
                    "&7Status: " + status, "&7Required XP: &f" + required, "&7Member slots: &f" + memberSlots,
                    reached ? "&7Party XP sekarang: &f" + rep : "&7Kurang: &f" + Math.max(0, required - rep) + " XP"));
        }
        inv.setItem(22, tagged(Material.ARROW, "&eKembali", "profile", "&7Kembali ke Party Profile."));
        player.openInventory(inv);
    }

    public void openProjects(Player player) { openProjects(player, 0); }

    public void openProjects(Player player, int requestedPage) {
        String party = requireParty(player);
        if (party == null || !guardModule(player, "projects", PERM_PROJECTS, "Party Projects")) return;
        List<String> ids = progression.projectIds();
        int page = clampPage(requestedPage, ids.size());
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + PROJECTS + " " + pageLabel(page, ids.size())));
        String active = progression.currentProject(party);
        boolean manage = canManageProjects(player);

        if (active != null) {
            int goal = progression.projectGoal(active);
            double raw = progression.projectProgress(party);
            int value = (int)Math.floor(raw);
            int percent = goal <= 0 ? 0 : Math.min(100, (int)Math.floor(raw * 100.0 / goal));
            inv.setItem(4, Util.item(Material.BEACON, "&6&lACTIVE: " + progression.projectName(active),
                    "&7Type: &f" + progression.projectType(active),
                    "&7Progress: &f" + value + "/" + goal + " &8(&b" + percent + "%&8)",
                    progressBar(raw, goal), "&7Base reward: &b+" + progression.projectReward(active) + " Party XP"));
            if (manage) inv.setItem(47, tagged(Material.BARRIER, "&cCancel Project", "project-cancel:" + page,
                    "&7Batalkan Project aktif.", "&cProgress saat ini akan hilang.",
                    confirmCancelProject() ? "&eKlik untuk membuka konfirmasi." : "&cKlik untuk batalkan."));
        } else {
            inv.setItem(4, Util.item(Material.PAPER, "&7Belum ada Project aktif",
                    manage ? "&aPilih Project di bawah untuk memulai." : "&7Owner/Officer dengan izin dapat memulai Project."));
        }

        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            int slot = 9 + (i - start);
            boolean isActive = id.equalsIgnoreCase(active);
            List<String> lore = new ArrayList<>();
            lore.add("&7ID: &f" + id);
            lore.add("&7Type: &f" + progression.projectType(id));
            lore.add("&7Goal: &f" + progression.projectGoal(id));
            lore.add("&7Reward: &b+" + progression.projectReward(id) + " Party XP");
            if (isActive) lore.add("&6Sedang aktif.");
            else if (active != null) lore.add("&8Selesaikan/cancel Project aktif dulu.");
            else if (manage) lore.add("&aKlik untuk mulai.");
            else lore.add("&8Butuh role Owner/Officer dan permission manage.");
            inv.setItem(slot, tagged(Material.CHEST, "&6" + progression.projectName(id), "project-start:" + page + ":" + id, lore.toArray(String[]::new)));
        }
        addPagination(inv, "projects-page:", page, ids.size());
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openSkills(Player player) { openSkills(player, 0); }

    public void openSkills(Player player, int requestedPage) {
        String party = requireParty(player);
        if (party == null || !guardModule(player, "skill-tree", PERM_SKILLS, "Party Skill Tree")) return;
        List<String> nodes = progression.skillNodeIds();
        int page = clampPage(requestedPage, nodes.size());
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + SKILLS + " " + pageLabel(page, nodes.size())));
        boolean canUnlock = canUnlockSkills(player);
        inv.setItem(4, Util.item(Material.NETHER_STAR, "&d&lPARTY SKILL TREE",
                "&7Party Level: &f" + parties.level(party), "&7Skill Point tersedia: &d" + progression.availableSkillPoints(party),
                canUnlock ? "&aKlik node terkunci untuk unlock." : "&7Hanya Owner dengan izin unlock yang dapat memakai Skill Point."));

        int start = page * PAGE_SIZE;
        int end = Math.min(nodes.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            String node = nodes.get(i);
            int slot = 9 + (i - start);
            boolean unlocked = progression.hasSkill(party, node);
            String base = "progression.skill-tree.nodes." + node;
            String name = plugin.getConfig().getString(base + ".display-name", node);
            String branch = plugin.getConfig().getString(base + ".branch", "CORE");
            int minLevel = Math.max(1, plugin.getConfig().getInt(base + ".min-level", 1));
            String requires = plugin.getConfig().getString(base + ".requires", "");
            String status = unlocked ? "&aUNLOCKED" : "&8LOCKED";
            String click = unlocked ? "&8Sudah terbuka."
                    : canUnlock ? (confirmSkillUnlock() ? "&eKlik untuk konfirmasi unlock." : "&aKlik untuk unlock.")
                    : "&8Tidak dapat di-unlock oleh akun ini.";
            inv.setItem(slot, tagged(skillMaterial(branch, unlocked), "&d" + name, "skill-unlock:" + page + ":" + node,
                    "&7Branch: &f" + branch, "&7Status: " + status, "&7Min Level: &f" + minLevel,
                    requires == null || requires.isBlank() ? "&7Requires: &8-" : "&7Requires: &f" + requires,
                    "&7Effect: &f" + skillEffectSummary(node), click));
        }
        addPagination(inv, "skills-page:", page, nodes.size());
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openDivisions(Player player) { openDivisions(player, 0); }

    public void openDivisions(Player player, int requestedPage) {
        String party = requireParty(player);
        if (party == null || !guardModule(player, "divisions", PERM_DIVISIONS, "Party Divisions")) return;
        List<String> ids = progression.divisionIds();
        int page = clampPage(requestedPage, ids.size());
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + DIVISIONS + " " + pageLabel(page, ids.size())));
        String current = progression.divisionOf(player.getUniqueId());
        boolean selfSelectConfig = plugin.getConfig().getBoolean("progression.divisions.allow-self-select", true);
        boolean selfSelect = selfSelectConfig && allowed(player, PERM_DIVISIONS_SELF);
        inv.setItem(4, Util.item(Material.SHIELD, "&b&lPARTY DIVISIONS", "&7Divisimu: &f" + progression.divisionDisplay(current),
                selfSelect ? "&aKlik salah satu divisi untuk memilih."
                        : selfSelectConfig ? "&cKamu tidak punya permission self-select." : "&7Self-select dinonaktifkan server."));

        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            int slot = 9 + (i - start);
            int count = 0;
            for (UUID member : parties.members(party)) if (progression.divisionOf(member).equalsIgnoreCase(id)) count++;
            boolean selected = current.equalsIgnoreCase(id);
            inv.setItem(slot, tagged(Material.SHIELD, "&b" + progression.divisionDisplay(id), "division-join:" + page + ":" + id,
                    "&7Member: &f" + count, "&7Bonus: &f" + divisionEffectSummary(id),
                    selected ? "&aDivisimu saat ini." : (selfSelect ? "&aKlik untuk bergabung." : "&8Tidak dapat dipilih sendiri.")));
        }
        if (!current.equalsIgnoreCase("none") && selfSelect) {
            inv.setItem(48, tagged(Material.GRAY_DYE, "&7Keluar dari Division", "division-join:" + page + ":none", "&7Kembali tanpa spesialisasi."));
        }
        if (canManageDivisions(player)) {
            inv.setItem(47, tagged(Material.PLAYER_HEAD, "&eKelola Division Member", "division-members:0", "&7Owner/Officer dapat mengatur divisi member."));
        }
        addPagination(inv, "divisions-page:", page, ids.size());
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openIdentity(Player player) {
        String party = requireParty(player);
        if (party == null || !guardModule(player, "identity", PERM_IDENTITY, "Dynamic Identity")) return;
        Map<String, Integer> totals = progression.activityTotals(party);
        int total = totals.values().stream().mapToInt(Integer::intValue).sum();
        int window = Math.max(1, plugin.getConfig().getInt("progression.identity.window-days", 30));
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + IDENTITY));
        inv.setItem(4, Util.item(Material.COMPASS, "&e&l" + progression.identityDisplay(progression.identity(party)),
                "&7Aktivitas dihitung dari &f" + window + " hari &7terakhir.", "&7Total activity score: &f" + total,
                "&8Identity berubah otomatis, bukan dipilih manual."));
        inv.setItem(10, activityItem(Material.IRON_SWORD, "&cCombat", totals.getOrDefault("combat", 0), total));
        inv.setItem(12, activityItem(Material.DIAMOND_PICKAXE, "&bMining", totals.getOrDefault("mining", 0), total));
        inv.setItem(14, activityItem(Material.WHEAT, "&aFarming", totals.getOrDefault("farming", 0), total));
        inv.setItem(16, activityItem(Material.BEACON, "&6Projects", totals.getOrDefault("projects", 0), total));
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Progression Hub."));
        player.openInventory(inv);
    }

    public void openDivisionMembers(Player actor) { openDivisionMembers(actor, 0); }

    public void openDivisionMembers(Player actor, int requestedPage) {
        String party = requireParty(actor);
        if (party == null || !guardModule(actor, "divisions", PERM_DIVISIONS, "Party Divisions")) return;
        if (!canManageDivisions(actor)) {
            actor.sendMessage(parties.prefix() + Util.color(" &cHanya Owner/Officer dengan permission Division Manage yang dapat mengatur member."));
            return;
        }
        List<UUID> members = new ArrayList<>(parties.members(party));
        members.sort((a, b) -> memberName(a).compareToIgnoreCase(memberName(b)));
        int page = clampPage(requestedPage, members.size());
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + DIVISION_MEMBERS + " " + pageLabel(page, members.size())));
        inv.setItem(4, Util.item(Material.PLAYER_HEAD, "&e&lDIVISION MEMBER MANAGER", "&7Pilih member untuk mengatur spesialisasinya."));
        int start = page * PAGE_SIZE;
        int end = Math.min(members.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            UUID uuid = members.get(i);
            int slot = 9 + (i - start);
            inv.setItem(slot, tagged(Material.PLAYER_HEAD, "&b" + memberName(uuid), "division-member:" + page + ":" + uuid,
                    "&7Role: &f" + parties.role(uuid), "&7Division: &f" + progression.divisionDisplay(progression.divisionOf(uuid)), "&aKlik untuk mengatur."));
        }
        addPagination(inv, "division-members-page:", page, members.size());
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "divisions", "&7Kembali ke menu Division."));
        actor.openInventory(inv);
    }

    public void openMemberDivisionMenu(Player actor, UUID target) { openMemberDivisionMenu(actor, target, 0, 0); }

    private void openMemberDivisionMenu(Player actor, UUID target, int requestedPage, int membersPage) {
        String party = requireParty(actor);
        if (party == null || !guardModule(actor, "divisions", PERM_DIVISIONS, "Party Divisions")) return;
        if (!canManageDivisions(actor) || !party.equals(parties.partyOf(target))) {
            actor.sendMessage(parties.prefix() + Util.color(" &cAkses pengaturan divisi tidak valid."));
            return;
        }
        List<String> ids = progression.divisionIds();
        int page = clampPage(requestedPage, ids.size());
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + SET_DIVISION + " " + pageLabel(page, ids.size())));
        inv.setItem(4, Util.item(Material.PLAYER_HEAD, "&b" + memberName(target),
                "&7Division saat ini: &f" + progression.divisionDisplay(progression.divisionOf(target))));
        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            int slot = 9 + (i - start);
            inv.setItem(slot, tagged(Material.SHIELD, "&b" + progression.divisionDisplay(id),
                    "division-assign:" + page + ":" + membersPage + ":" + target + ":" + id,
                    "&7Bonus: &f" + divisionEffectSummary(id), "&aKlik untuk assign."));
        }
        inv.setItem(48, tagged(Material.GRAY_DYE, "&7No Division", "division-assign:" + page + ":" + membersPage + ":" + target + ":none",
                "&7Hapus spesialisasi member."));
        addPaginationForTarget(inv, page, ids.size(), membersPage, target);
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "division-members:" + membersPage, "&7Kembali ke daftar member."));
        actor.openInventory(inv);
    }

    private void openConfirm(Player player, String title, String detail, String yesAction, String noAction) {
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + CONFIRM));
        inv.setItem(4, Util.item(Material.PAPER, "&e&l" + title, detail, "&7Pastikan pilihanmu sudah benar."));
        inv.setItem(11, tagged(Material.LIME_DYE, "&a&lKONFIRMASI", yesAction, "&7Klik untuk melanjutkan."));
        inv.setItem(15, tagged(Material.RED_DYE, "&c&lBATAL", noAction, "&7Kembali tanpa perubahan."));
        player.openInventory(inv);
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
        if (action.equals("levels")) { openLevels(player); return; }
        if (action.equals("projects")) { openProjects(player); return; }
        if (action.equals("skills")) { openSkills(player); return; }
        if (action.equals("divisions")) { openDivisions(player); return; }
        if (action.equals("identity")) { openIdentity(player); return; }
        if (action.startsWith("projects-page:")) { openProjects(player, parseInt(action.substring(14), 0)); return; }
        if (action.startsWith("skills-page:")) { openSkills(player, parseInt(action.substring(12), 0)); return; }
        if (action.startsWith("divisions-page:")) { openDivisions(player, parseInt(action.substring(15), 0)); return; }
        if (action.startsWith("division-members-page:")) { openDivisionMembers(player, parseInt(action.substring(22), 0)); return; }
        if (action.startsWith("division-members:")) { openDivisionMembers(player, parseInt(action.substring(17), 0)); return; }

        if (action.startsWith("project-cancel:")) {
            int page = parseInt(action.substring(15), 0);
            if (!canManageProjects(player)) { noPermission(player); openProjects(player, page); return; }
            if (confirmCancelProject()) openConfirm(player, "Cancel Project?", "&cProgress Project aktif akan hilang.",
                    "project-cancel-confirm:" + page, "projects-page:" + page);
            else { progression.cancelProject(player); openProjects(player, page); }
            return;
        }
        if (action.startsWith("project-cancel-confirm:")) {
            int page = parseInt(action.substring(23), 0);
            if (canManageProjects(player)) progression.cancelProject(player); else noPermission(player);
            openProjects(player, page); return;
        }
        if (action.startsWith("project-start:")) {
            String payload = action.substring(14);
            int cut = payload.indexOf(':');
            if (cut > 0) {
                int page = parseInt(payload.substring(0, cut), 0);
                String id = payload.substring(cut + 1);
                if (canManageProjects(player)) progression.startProject(player, id); else noPermission(player);
                openProjects(player, page);
            }
            return;
        }

        if (action.startsWith("skill-unlock:")) {
            String payload = action.substring(13);
            int cut = payload.indexOf(':');
            if (cut > 0) {
                int page = parseInt(payload.substring(0, cut), 0);
                String node = payload.substring(cut + 1);
                String party = parties.partyOf(player.getUniqueId());
                if (!canUnlockSkills(player)) { noPermission(player); openSkills(player, page); return; }
                if (party != null && progression.hasSkill(party, node)) { openSkills(player, page); return; }
                if (confirmSkillUnlock()) {
                    String name = plugin.getConfig().getString("progression.skill-tree.nodes." + node + ".display-name", node);
                    openConfirm(player, "Unlock " + name + "?", "&7Aksi ini menggunakan &d1 Skill Point&7.",
                            "skill-unlock-confirm:" + page + ":" + node, "skills-page:" + page);
                } else { progression.unlockSkill(player, node); openSkills(player, page); }
            }
            return;
        }
        if (action.startsWith("skill-unlock-confirm:")) {
            String payload = action.substring(21);
            int cut = payload.indexOf(':');
            if (cut > 0) {
                int page = parseInt(payload.substring(0, cut), 0);
                String node = payload.substring(cut + 1);
                if (canUnlockSkills(player)) progression.unlockSkill(player, node); else noPermission(player);
                openSkills(player, page);
            }
            return;
        }

        if (action.startsWith("division-join:")) {
            String payload = action.substring(14);
            int cut = payload.indexOf(':');
            if (cut > 0) {
                int page = parseInt(payload.substring(0, cut), 0);
                String id = payload.substring(cut + 1);
                if (allowed(player, PERM_DIVISIONS_SELF)) progression.chooseDivision(player, id); else noPermission(player);
                openDivisions(player, page);
            }
            return;
        }
        if (action.startsWith("division-member:")) {
            String payload = action.substring(16);
            int cut = payload.indexOf(':');
            if (cut > 0) {
                int membersPage = parseInt(payload.substring(0, cut), 0);
                try { openMemberDivisionMenu(player, UUID.fromString(payload.substring(cut + 1)), 0, membersPage); }
                catch (IllegalArgumentException ignored) { openDivisionMembers(player, membersPage); }
            }
            return;
        }
        if (action.startsWith("set-division-page:")) {
            String[] parts = action.substring(18).split(":", 3);
            if (parts.length == 3) {
                int page = parseInt(parts[0], 0);
                int membersPage = parseInt(parts[1], 0);
                try { openMemberDivisionMenu(player, UUID.fromString(parts[2]), page, membersPage); }
                catch (IllegalArgumentException ignored) { openDivisionMembers(player, membersPage); }
            }
            return;
        }
        if (action.startsWith("division-assign:")) {
            String[] parts = action.substring(16).split(":", 4);
            if (parts.length == 4) {
                int page = parseInt(parts[0], 0);
                int membersPage = parseInt(parts[1], 0);
                try {
                    UUID target = UUID.fromString(parts[2]);
                    if (canManageDivisions(player)) {
                        progression.assignDivision(player, Bukkit.getOfflinePlayer(target), parts[3]);
                        openMemberDivisionMenu(player, target, page, membersPage);
                    } else { noPermission(player); openDivisionMembers(player, membersPage); }
                } catch (IllegalArgumentException ignored) { openDivisionMembers(player, membersPage); }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isOurTitle(cleanTitle(event.getView().getTitle()))) event.setCancelled(true);
    }

    private boolean canManageProjects(Player player) {
        return allowed(player, PERM_PROJECTS_MANAGE) && parties.canManage(player.getUniqueId());
    }

    private boolean canUnlockSkills(Player player) {
        String party = parties.partyOf(player.getUniqueId());
        return party != null && allowed(player, PERM_SKILLS_UNLOCK) && player.getUniqueId().equals(parties.owner(party));
    }

    private boolean guardGui(Player player) {
        if (!enabled()) { player.sendMessage(parties.prefix() + Util.color(" &cProgression GUI dinonaktifkan.")); return false; }
        if (!canView(player)) { noPermission(player); return false; }
        return true;
    }

    private boolean guardModule(Player player, String module, String permission, String display) {
        if (!guardGui(player)) return false;
        if (!progression.moduleEnabled(module)) { moduleDisabled(player, display); return false; }
        if (!allowed(player, permission)) { noPermission(player); return false; }
        return true;
    }

    private boolean allowed(Player player, String permission) {
        return player.hasPermission("menkiestesparty.admin") || player.hasPermission(permission);
    }

    private void noPermission(Player player) {
        player.sendMessage(parties.prefix() + Util.color(" &cKamu tidak punya permission untuk aksi GUI ini."));
    }

    private boolean confirmCancelProject() { return plugin.getConfig().getBoolean("gui.progression.confirmations.project-cancel", true); }
    private boolean confirmSkillUnlock() { return plugin.getConfig().getBoolean("gui.progression.confirmations.skill-unlock", true); }

    private ItemStack moduleButton(Player player, String module, String permission, Material material, String name, String action, String description) {
        if (!progression.moduleEnabled(module)) return Util.item(Material.BARRIER, name, description, "&cModule dinonaktifkan server.");
        if (!allowed(player, permission)) return Util.item(Material.BARRIER, name, description, "&cKamu tidak punya permission GUI ini.");
        return tagged(material, name, action, description, "&aKlik untuk buka.");
    }

    private ItemStack activityItem(Material material, String name, int score, int total) {
        double percent = total <= 0 ? 0.0 : score * 100.0 / total;
        return Util.item(material, name, "&7Activity score: &f" + score,
                "&7Share: &f" + String.format(Locale.US, "%.1f", percent) + "%", progressBar(score, Math.max(1, total)));
    }

    private void addPagination(Inventory inv, String prefix, int page, int totalItems) {
        int pages = pageCount(totalItems);
        if (page > 0) inv.setItem(45, tagged(Material.ARROW, "&ePrevious Page", prefix + (page - 1), "&7Halaman " + page + "/" + pages));
        if (page + 1 < pages) inv.setItem(53, tagged(Material.ARROW, "&eNext Page", prefix + (page + 1), "&7Halaman " + (page + 2) + "/" + pages));
    }

    private void addPaginationForTarget(Inventory inv, int page, int totalItems, int membersPage, UUID target) {
        int pages = pageCount(totalItems);
        if (page > 0) inv.setItem(45, tagged(Material.ARROW, "&ePrevious Page",
                "set-division-page:" + (page - 1) + ":" + membersPage + ":" + target, "&7Halaman " + page + "/" + pages));
        if (page + 1 < pages) inv.setItem(53, tagged(Material.ARROW, "&eNext Page",
                "set-division-page:" + (page + 1) + ":" + membersPage + ":" + target, "&7Halaman " + (page + 2) + "/" + pages));
    }

    private int pageCount(int totalItems) { return Math.max(1, (totalItems + PAGE_SIZE - 1) / PAGE_SIZE); }
    private int clampPage(int requested, int totalItems) { return Math.max(0, Math.min(requested, pageCount(totalItems) - 1)); }
    private String pageLabel(int page, int totalItems) { return (page + 1) + "/" + pageCount(totalItems); }

    private String progressBar(double current, double max) {
        int width = Math.max(5, Math.min(40, plugin.getConfig().getInt("gui.progression.progress-bar-width", 20)));
        double ratio = max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, current / max));
        int filled = (int)Math.round(ratio * width);
        return "&a" + "|".repeat(filled) + "&8" + "|".repeat(width - filled) + " &f" + (int)Math.floor(ratio * 100.0) + "%";
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
        return title.equalsIgnoreCase(HUB) || title.equalsIgnoreCase(PROFILE) || title.equalsIgnoreCase(IDENTITY)
                || title.equalsIgnoreCase(LEVELS) || title.equalsIgnoreCase(CONFIRM)
                || title.startsWith(PROJECTS + " ") || title.startsWith(SKILLS + " ")
                || title.startsWith(DIVISIONS + " ") || title.startsWith(DIVISION_MEMBERS + " ")
                || title.startsWith(SET_DIVISION + " ");
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

    private int parseInt(String input, int fallback) {
        try { return Integer.parseInt(input); }
        catch (Exception ignored) { return fallback; }
    }
}
