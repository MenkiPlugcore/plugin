package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MENKIESTESParty v1.2 progression layer.
 *
 * Features are intentionally isolated from the Party core and can be toggled
 * independently. All persistent data stays in parties.yml so v1.1 servers can
 * upgrade without converting or resetting existing Party data.
 */
public final class ProgressionManager implements Listener, CommandExecutor, TabCompleter {
    private static final List<String> ACTIVITY_TYPES = List.of("combat", "mining", "farming", "projects");
    private static final Set<String> NESTED = Set.of("project", "projects", "skill", "skills", "skilltree", "division", "divisions", "identity", "profile", "progression");

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final StorageBundle db;
    private final Set<String> placedMining = ConcurrentHashMap.newKeySet();
    private int activityWrites;

    public ProgressionManager(MENKIESTESPartyPlugin plugin, PartyService parties, StorageBundle db) {
        this.plugin = plugin;
        this.parties = parties;
        this.db = db;
        for (String name : List.of("partyproject", "partyskill", "partydivision", "partyidentity", "partyprofile")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
    }

    public boolean moduleEnabled(String module) {
        return plugin.getConfig().getBoolean("modules." + module, true);
    }

    // ---------------------------------------------------------------------
    // Activity listeners
    // ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!moduleEnabled("projects") && !moduleEnabled("identity")) return;
        if (parties.isMiningMaterial(event.getBlockPlaced().getType())) {
            placedMining.add(locationKey(event.getBlockPlaced()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) return;

        Block block = event.getBlock();
        if (parties.isMiningMaterial(block.getType())) {
            String key = locationKey(block);
            if (!placedMining.remove(key)) {
                recordActivityForParty(party, "mining", 1);
                progressProject(party, "mining", 1.0, player.getUniqueId());
            }
        }

        if (isMatureCrop(block)) {
            recordActivityForParty(party, "farming", 1);
            progressProject(party, "farmer", 1.0, player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        String party = parties.partyOf(killer.getUniqueId());
        if (party == null) return;

        if (event.getEntity() instanceof Player) {
            recordActivityForParty(party, "combat", 5);
        } else {
            recordActivityForParty(party, "combat", 1);
            progressProject(party, "hunter", 1.0, killer.getUniqueId());
        }
    }

    // ---------------------------------------------------------------------
    // Commands: direct aliases + /party <progression-subcommand>
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "partyproject" -> commandProject(sender, args);
            case "partyskill" -> commandSkill(sender, args);
            case "partydivision" -> commandDivision(sender, args);
            case "partyidentity" -> commandIdentity(sender);
            case "partyprofile" -> commandProfile(sender);
            default -> false;
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onPartySubcommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] split = raw.substring(1).trim().split("\\s+");
        if (split.length < 2) return;
        String root = split[0].toLowerCase(Locale.ROOT);
        if (!root.equals("party") && !root.equals("p") && !root.equals("parties")) return;
        String sub = split[1].toLowerCase(Locale.ROOT);
        if (!NESTED.contains(sub)) return;

        event.setCancelled(true);
        String[] args = split.length <= 2 ? new String[0] : Arrays.copyOfRange(split, 2, split.length);
        switch (sub) {
            case "project", "projects" -> commandProject(event.getPlayer(), args);
            case "skill", "skills", "skilltree" -> commandSkill(event.getPlayer(), args);
            case "division", "divisions" -> commandDivision(event.getPlayer(), args);
            case "identity" -> commandIdentity(event.getPlayer());
            case "profile", "progression" -> commandProfile(event.getPlayer());
        }
    }

    @EventHandler
    public void onPartyTab(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) return;
        String raw = buffer.substring(1);
        boolean trailingSpace = raw.endsWith(" ");
        String[] split = raw.trim().isEmpty() ? new String[0] : raw.trim().split("\\s+");
        if (split.length == 0) return;
        String root = split[0].toLowerCase(Locale.ROOT);
        if (!root.equals("party") && !root.equals("p") && !root.equals("parties")) return;

        List<String> completions = new ArrayList<>(event.getCompletions());
        if (split.length == 1 || (split.length == 2 && !trailingSpace)) {
            String typed = split.length == 2 ? split[1].toLowerCase(Locale.ROOT) : "";
            for (String value : List.of("profile", "project", "skill", "division", "identity")) {
                if (value.startsWith(typed) && completions.stream().noneMatch(x -> x.equalsIgnoreCase(value))) completions.add(value);
            }
            event.setCompletions(completions);
            return;
        }

        if (split.length >= 2) {
            String sub = split[1].toLowerCase(Locale.ROOT);
            String typed = trailingSpace ? "" : split[split.length - 1].toLowerCase(Locale.ROOT);
            List<String> suggested = new ArrayList<>();
            if ((sub.equals("project") || sub.equals("projects"))) {
                if (split.length <= 3) suggested.addAll(List.of("list", "start", "cancel"));
                else if (split.length == 4 && split[2].equalsIgnoreCase("start")) suggested.addAll(projectIds());
            } else if (sub.equals("skill") || sub.equals("skills") || sub.equals("skilltree")) {
                if (split.length <= 3) suggested.addAll(List.of("list", "unlock"));
                else if (split.length == 4 && split[2].equalsIgnoreCase("unlock")) suggested.addAll(skillNodeIds());
            } else if (sub.equals("division") || sub.equals("divisions")) {
                if (split.length <= 3) suggested.addAll(List.of("list", "join", "set"));
                else if (split.length == 4 && split[2].equalsIgnoreCase("join")) {
                    suggested.add("none"); suggested.addAll(divisionIds());
                }
            }
            if (!suggested.isEmpty()) {
                event.setCompletions(suggested.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(typed)).distinct().toList());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("partyproject")) {
            if (args.length == 1) return filter(List.of("list", "start", "cancel"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("start")) return filter(projectIds(), args[1]);
        }
        if (name.equals("partyskill")) {
            if (args.length == 1) return filter(List.of("list", "unlock"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("unlock")) return filter(skillNodeIds(), args[1]);
        }
        if (name.equals("partydivision")) {
            if (args.length == 1) return filter(List.of("list", "join", "set"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
                List<String> out = new ArrayList<>(); out.add("none"); out.addAll(divisionIds()); return filter(out, args[1]);
            }
        }
        return List.of();
    }

    private boolean commandProject(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("status")) {
            showProject(player); return true;
        }
        if (args[0].equalsIgnoreCase("start")) {
            if (args.length < 2) { msg(player, " &c/party project start <id>"); return true; }
            startProject(player, args[1]); return true;
        }
        if (args[0].equalsIgnoreCase("cancel")) { cancelProject(player); return true; }
        msg(player, " &c/party project [list|start <id>|cancel]");
        return true;
    }

    private boolean commandSkill(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { showSkills(player); return true; }
        if (args[0].equalsIgnoreCase("unlock")) {
            if (args.length < 2) { msg(player, " &c/party skill unlock <node>"); return true; }
            unlockSkill(player, args[1]); return true;
        }
        msg(player, " &c/party skill [list|unlock <node>]");
        return true;
    }

    private boolean commandDivision(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { showDivisions(player); return true; }
        if (args[0].equalsIgnoreCase("join")) {
            if (args.length < 2) { msg(player, " &c/party division join <id|none>"); return true; }
            chooseDivision(player, args[1]); return true;
        }
        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 3) { msg(player, " &c/party division set <player> <id|none>"); return true; }
            assignDivision(player, Bukkit.getOfflinePlayer(args[1]), args[2]); return true;
        }
        msg(player, " &c/party division [list|join <id>|set <player> <id>]");
        return true;
    }

    private boolean commandIdentity(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        showIdentity(player); return true;
    }

    private boolean commandProfile(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        showProfile(player); return true;
    }

    // ---------------------------------------------------------------------
    // Party Projects
    // ---------------------------------------------------------------------

    public List<String> projectIds() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("progression.projects.definitions");
        if (section == null) return List.of();
        List<String> out = new ArrayList<>(section.getKeys(false));
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public void showProject(Player player) {
        if (!moduleEnabled("projects")) { msg(player, " &cParty Projects dinonaktifkan di server ini."); return; }
        String party = requireParty(player);
        if (party == null) return;
        String id = currentProject(party);

        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&6&lPARTY PROJECT &8- &f" + parties.display(party)));
        if (id == null) {
            player.sendMessage(Util.color("&7Belum ada project aktif."));
            for (String available : projectIds()) {
                player.sendMessage(Util.color("&e- &f" + available + " &8| &7" + projectName(available)
                        + " &8| &f" + projectGoal(available) + " " + projectType(available)
                        + " &8| &b+" + projectReward(available) + " Party XP"));
            }
            player.sendMessage(Util.color("&7Owner/Officer: &f/party project start <id>"));
            return;
        }

        double progress = projectProgress(party);
        int goal = projectGoal(id);
        double percent = goal <= 0 ? 0 : Math.min(100.0, progress * 100.0 / goal);
        player.sendMessage(Util.color("&7Project: &e" + projectName(id) + " &8(&f" + id + "&8)"));
        player.sendMessage(Util.color("&7Tipe: &f" + projectType(id)));
        player.sendMessage(Util.color("&7Progress: &f" + (int)Math.floor(progress) + "/" + goal + " &8(&b" + String.format(Locale.US, "%.1f", percent) + "%&8)"));
        player.sendMessage(Util.color("&7Reward: &b+" + effectiveProjectReward(party, id) + " Party XP"));
        player.sendMessage(Util.color("&7Selesai total: &f" + db.parties.getInt("parties." + party + ".progression.projects.completed", 0)));
    }

    public void startProject(Player player, String id) {
        if (!moduleEnabled("projects")) { msg(player, " &cParty Projects dinonaktifkan."); return; }
        String party = requireParty(player);
        if (party == null) return;
        if (!parties.canManage(player.getUniqueId())) { msg(player, " &cHanya Owner/Officer yang dapat memulai Project."); return; }
        id = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (!projectIds().contains(id)) { msg(player, " &cProject tidak ditemukan. Gunakan /party project list."); return; }
        if (currentProject(party) != null) { msg(player, " &cParty sudah memiliki Project aktif. Selesaikan atau cancel dulu."); return; }

        String root = "parties." + party + ".progression.projects.active";
        db.parties.set(root + ".id", id);
        db.parties.set(root + ".progress", 0.0);
        db.parties.set(root + ".started-at", System.currentTimeMillis());
        db.parties.set(root + ".started-by", player.getUniqueId().toString());
        plugin.saveDataSoon();
        parties.broadcastParty(party, parties.prefix() + " &6Project dimulai: &f" + projectName(id)
                + " &8| &7Target &f" + projectGoal(id) + " " + projectType(id));
    }

    public void cancelProject(Player player) {
        if (!moduleEnabled("projects")) { msg(player, " &cParty Projects dinonaktifkan."); return; }
        String party = requireParty(player);
        if (party == null) return;
        if (!parties.canManage(player.getUniqueId())) { msg(player, " &cHanya Owner/Officer yang dapat membatalkan Project."); return; }
        String id = currentProject(party);
        if (id == null) { msg(player, " &7Tidak ada Project aktif."); return; }
        db.parties.set("parties." + party + ".progression.projects.active", null);
        plugin.saveDataSoon();
        parties.broadcastParty(party, parties.prefix() + " &eProject &f" + projectName(id) + " &edibatalkan.");
    }

    private void progressProject(String party, String actionType, double amount, UUID contributor) {
        if (!moduleEnabled("projects")) return;
        String id = currentProject(party);
        if (id == null || !projectType(id).equalsIgnoreCase(actionType)) return;

        double multiplier = skillProjectProgressMultiplier(party, actionType) * divisionProjectProgressMultiplier(contributor, actionType);
        String root = "parties." + party + ".progression.projects.active";
        double next = db.parties.getDouble(root + ".progress", 0.0) + amount * multiplier;
        int goal = projectGoal(id);
        db.parties.set(root + ".progress", Math.min(goal, next));

        String memberPath = "parties." + party + ".members." + contributor + ".progression.project-contribution";
        db.parties.set(memberPath, db.parties.getDouble(memberPath, 0.0) + amount * multiplier);
        plugin.saveDataSoon();
        if (next < goal) return;

        int reward = effectiveProjectReward(party, id);
        db.parties.set("parties." + party + ".progression.projects.completed",
                db.parties.getInt("parties." + party + ".progression.projects.completed", 0) + 1);
        db.parties.set("parties." + party + ".progression.projects.last-completed", id);
        db.parties.set("parties." + party + ".progression.projects.last-completed-at", System.currentTimeMillis());
        db.parties.set(root, null);
        parties.addRep(party, reward);
        recordActivityForParty(party, "projects", plugin.getConfig().getInt("progression.identity.project-completion-weight", 50));
        parties.broadcastParty(party, parties.prefix() + " &a&lPROJECT SELESAI! &f" + projectName(id)
                + " &8| &b+" + reward + " Party XP");
    }

    public String currentProject(String party) {
        String id = db.parties.getString("parties." + party + ".progression.projects.active.id");
        return id == null || id.isBlank() ? null : id;
    }

    public double projectProgress(String party) {
        return db.parties.getDouble("parties." + party + ".progression.projects.active.progress", 0.0);
    }

    public String projectName(String id) {
        return plugin.getConfig().getString("progression.projects.definitions." + id + ".display-name", id);
    }

    public String projectType(String id) {
        return plugin.getConfig().getString("progression.projects.definitions." + id + ".type", "mining").toLowerCase(Locale.ROOT);
    }

    public int projectGoal(String id) {
        return Math.max(1, plugin.getConfig().getInt("progression.projects.definitions." + id + ".goal", 100));
    }

    public int projectReward(String id) {
        return Math.max(0, plugin.getConfig().getInt("progression.projects.definitions." + id + ".party-xp", 100));
    }

    private int effectiveProjectReward(String party, String id) {
        return (int)Math.round(projectReward(id) * projectXpMultiplier(party));
    }

    // ---------------------------------------------------------------------
    // Skill Tree
    // ---------------------------------------------------------------------

    public List<String> skillNodeIds() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("progression.skill-tree.nodes");
        if (section == null) return List.of();
        List<String> out = new ArrayList<>(section.getKeys(false));
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public int availableSkillPoints(String party) {
        int perLevel = Math.max(0, plugin.getConfig().getInt("progression.skill-tree.points-per-level", 1));
        int earned = Math.max(0, parties.level(party) - 1) * perLevel;
        int spent = 0;
        for (String node : skillNodeIds()) if (hasSkill(party, node)) spent++;
        return Math.max(0, earned - spent);
    }

    public boolean hasSkill(String party, String node) {
        return db.parties.getBoolean("parties." + party + ".progression.skills." + node, false);
    }

    public void showSkills(Player player) {
        if (!moduleEnabled("skill-tree")) { msg(player, " &cParty Skill Tree dinonaktifkan."); return; }
        String party = requireParty(player);
        if (party == null) return;
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&d&lPARTY SKILL TREE &8- &f" + parties.display(party)));
        player.sendMessage(Util.color("&7Skill Point tersedia: &d" + availableSkillPoints(party)));
        for (String node : skillNodeIds()) {
            boolean unlocked = hasSkill(party, node);
            String branch = plugin.getConfig().getString("progression.skill-tree.nodes." + node + ".branch", "CORE");
            int minLevel = Math.max(1, plugin.getConfig().getInt("progression.skill-tree.nodes." + node + ".min-level", 1));
            String requires = plugin.getConfig().getString("progression.skill-tree.nodes." + node + ".requires", "");
            player.sendMessage(Util.color("&7- &f" + skillName(node) + " &8[&b" + branch + "&8] " + (unlocked ? "&aUNLOCKED" : "&8LOCKED")));
            player.sendMessage(Util.color("  &8" + node + " &7| Lv." + minLevel
                    + (requires == null || requires.isBlank() ? "" : " | requires " + requires)
                    + " | " + skillEffectSummary(node)));
        }
        player.sendMessage(Util.color("&7Owner: &f/party skill unlock <node>"));
    }

    public void unlockSkill(Player player, String node) {
        if (!moduleEnabled("skill-tree")) { msg(player, " &cParty Skill Tree dinonaktifkan."); return; }
        String party = requireParty(player);
        if (party == null) return;
        if (!player.getUniqueId().equals(parties.owner(party))) { msg(player, " &cHanya Owner yang dapat menggunakan Skill Point."); return; }
        node = node == null ? "" : node.toLowerCase(Locale.ROOT);
        if (!skillNodeIds().contains(node)) { msg(player, " &cSkill node tidak ditemukan."); return; }
        if (hasSkill(party, node)) { msg(player, " &eSkill tersebut sudah terbuka."); return; }
        int minLevel = Math.max(1, plugin.getConfig().getInt("progression.skill-tree.nodes." + node + ".min-level", 1));
        if (parties.level(party) < minLevel) { msg(player, " &cButuh Party Level " + minLevel + "."); return; }
        String requires = plugin.getConfig().getString("progression.skill-tree.nodes." + node + ".requires", "");
        if (requires != null && !requires.isBlank() && !hasSkill(party, requires.toLowerCase(Locale.ROOT))) {
            msg(player, " &cButuh skill &f" + requires + " &cterlebih dahulu."); return;
        }
        if (availableSkillPoints(party) <= 0) { msg(player, " &cTidak ada Skill Point tersedia."); return; }

        db.parties.set("parties." + party + ".progression.skills." + node, true);
        db.parties.set("parties." + party + ".progression.skills-unlocked-at." + node, System.currentTimeMillis());
        plugin.saveDataSoon();
        parties.broadcastParty(party, parties.prefix() + " &dSkill Party terbuka: &f" + skillName(node)
                + " &8| &7" + skillEffectSummary(node));
    }

    private double skillProjectProgressMultiplier(String party, String actionType) {
        if (!moduleEnabled("skill-tree")) return 1.0;
        int bonus = 0;
        for (String node : skillNodeIds()) {
            if (!hasSkill(party, node)) continue;
            String base = "progression.skill-tree.nodes." + node + ".effects.";
            bonus += plugin.getConfig().getInt(base + "all-project-progress-bonus-percent", 0);
            if (actionType.equalsIgnoreCase("hunter")) bonus += plugin.getConfig().getInt(base + "hunter-progress-bonus-percent", 0);
            if (actionType.equalsIgnoreCase("mining") || actionType.equalsIgnoreCase("farmer")) {
                bonus += plugin.getConfig().getInt(base + "resource-progress-bonus-percent", 0);
            }
        }
        return 1.0 + Math.max(0, bonus) / 100.0;
    }

    private double projectXpMultiplier(String party) {
        if (!moduleEnabled("skill-tree")) return 1.0;
        int bonus = 0;
        for (String node : skillNodeIds()) {
            if (hasSkill(party, node)) bonus += plugin.getConfig().getInt("progression.skill-tree.nodes." + node + ".effects.project-xp-bonus-percent", 0);
        }
        return 1.0 + Math.max(0, bonus) / 100.0;
    }

    private String skillName(String node) {
        return plugin.getConfig().getString("progression.skill-tree.nodes." + node + ".display-name", node);
    }

    private String skillEffectSummary(String node) {
        String base = "progression.skill-tree.nodes." + node + ".effects.";
        List<String> effects = new ArrayList<>();
        int hunter = plugin.getConfig().getInt(base + "hunter-progress-bonus-percent", 0);
        int resource = plugin.getConfig().getInt(base + "resource-progress-bonus-percent", 0);
        int all = plugin.getConfig().getInt(base + "all-project-progress-bonus-percent", 0);
        int xp = plugin.getConfig().getInt(base + "project-xp-bonus-percent", 0);
        if (hunter != 0) effects.add("Hunter Project +" + hunter + "%");
        if (resource != 0) effects.add("Resource Project +" + resource + "%");
        if (all != 0) effects.add("Semua Project +" + all + "%");
        if (xp != 0) effects.add("Project XP +" + xp + "%");
        return effects.isEmpty() ? "No effect" : String.join(", ", effects);
    }

    // ---------------------------------------------------------------------
    // Divisions
    // ---------------------------------------------------------------------

    public List<String> divisionIds() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("progression.divisions.types");
        if (section == null) return List.of();
        List<String> out = new ArrayList<>(section.getKeys(false));
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public String divisionOf(UUID uuid) {
        String party = parties.partyOf(uuid);
        if (party == null) return "none";
        return db.parties.getString("parties." + party + ".members." + uuid + ".division", "none").toLowerCase(Locale.ROOT);
    }

    public String divisionDisplay(String id) {
        if (id == null || id.equalsIgnoreCase("none")) return "None";
        return plugin.getConfig().getString("progression.divisions.types." + id + ".display-name", id);
    }

    public void showDivisions(Player player) {
        if (!moduleEnabled("divisions")) { msg(player, " &cParty Divisions dinonaktifkan."); return; }
        String party = requireParty(player);
        if (party == null) return;
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&b&lPARTY DIVISIONS &8- &f" + parties.display(party)));
        for (String id : divisionIds()) {
            int count = 0;
            for (UUID member : parties.members(party)) if (divisionOf(member).equalsIgnoreCase(id)) count++;
            player.sendMessage(Util.color("&7- &b" + divisionDisplay(id) + " &8(&f" + id + "&8) &7: &f" + count + " member &8| &7" + divisionEffectSummary(id)));
        }
        player.sendMessage(Util.color("&7Divisimu: &f" + divisionDisplay(divisionOf(player.getUniqueId()))));
        if (plugin.getConfig().getBoolean("progression.divisions.allow-self-select", true)) {
            player.sendMessage(Util.color("&7Pilih: &f/party division join <id|none>"));
        }
    }

    public void chooseDivision(Player player, String id) {
        if (!moduleEnabled("divisions")) { msg(player, " &cParty Divisions dinonaktifkan."); return; }
        String party = requireParty(player);
        if (party == null) return;
        if (!plugin.getConfig().getBoolean("progression.divisions.allow-self-select", true)) {
            msg(player, " &cPemilihan divisi mandiri dinonaktifkan. Minta Owner/Officer."); return;
        }
        setDivision(party, player.getUniqueId(), id, player);
    }

    public void assignDivision(Player actor, OfflinePlayer target, String id) {
        if (!moduleEnabled("divisions")) { msg(actor, " &cParty Divisions dinonaktifkan."); return; }
        String party = requireParty(actor);
        if (party == null) return;
        if (!parties.canManage(actor.getUniqueId())) { msg(actor, " &cHanya Owner/Officer yang dapat mengatur divisi member."); return; }
        if (!party.equals(parties.partyOf(target.getUniqueId()))) { msg(actor, " &cTarget bukan anggota Party-mu."); return; }
        setDivision(party, target.getUniqueId(), id, actor);
    }

    private void setDivision(String party, UUID target, String id, Player actor) {
        id = id == null ? "none" : id.toLowerCase(Locale.ROOT);
        if (!id.equals("none") && !divisionIds().contains(id)) { msg(actor, " &cDivisi tidak ditemukan."); return; }
        db.parties.set("parties." + party + ".members." + target + ".division", id.equals("none") ? null : id);
        plugin.saveDataSoon();
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(target);
        String targetName = targetPlayer.getName() == null ? target.toString().substring(0, 8) : targetPlayer.getName();
        parties.broadcastParty(party, parties.prefix() + " &f" + targetName + " &7sekarang berada di divisi &b" + divisionDisplay(id) + "&7.");
    }

    private double divisionProjectProgressMultiplier(UUID contributor, String actionType) {
        if (!moduleEnabled("divisions") || contributor == null) return 1.0;
        String division = divisionOf(contributor);
        if (division.equals("none")) return 1.0;
        String base = "progression.divisions.types." + division + ".effects.";
        int bonus = plugin.getConfig().getInt(base + "all-project-progress-bonus-percent", 0);
        if (actionType.equalsIgnoreCase("hunter")) bonus += plugin.getConfig().getInt(base + "hunter-progress-bonus-percent", 0);
        if (actionType.equalsIgnoreCase("mining") || actionType.equalsIgnoreCase("farmer")) {
            bonus += plugin.getConfig().getInt(base + "resource-progress-bonus-percent", 0);
        }
        return 1.0 + Math.max(0, bonus) / 100.0;
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

    // ---------------------------------------------------------------------
    // Dynamic Party Identity
    // ---------------------------------------------------------------------

    public void recordActivityForParty(String party, String type, int amount) {
        if (!moduleEnabled("identity") || !parties.exists(party) || !ACTIVITY_TYPES.contains(type) || amount <= 0) return;
        String date = LocalDate.now().toString();
        String path = "parties." + party + ".progression.activity." + date + "." + type;
        db.parties.set(path, db.parties.getInt(path, 0) + amount);
        activityWrites++;
        if (activityWrites % 200 == 0) pruneOldActivity(party);
        updateIdentity(party);
        plugin.saveDataSoon();
    }

    public String identity(String party) {
        Map<String, Integer> totals = activityTotals(party);
        int total = totals.values().stream().mapToInt(Integer::intValue).sum();
        int minimum = Math.max(0, plugin.getConfig().getInt("progression.identity.min-activity", 50));
        if (total < minimum) return "DEVELOPING";

        Map.Entry<String, Integer> best = totals.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        if (best == null || best.getValue() <= 0) return "DEVELOPING";
        double threshold = Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble("progression.identity.dominance-percent", 45.0) / 100.0));
        if ((double)best.getValue() / total < threshold) return "BALANCED";
        return switch (best.getKey()) {
            case "combat" -> "WARLIKE";
            case "mining" -> "INDUSTRIAL";
            case "farming" -> "AGRARIAN";
            case "projects" -> "PROJECT_FOCUSED";
            default -> "BALANCED";
        };
    }

    public String identityDisplay(String identity) {
        String fallback = identity.replace('_', ' ');
        return plugin.getConfig().getString("progression.identity.labels." + identity, fallback);
    }

    public void showIdentity(Player player) {
        if (!moduleEnabled("identity")) { msg(player, " &cDynamic Party Identity dinonaktifkan."); return; }
        String party = requireParty(player);
        if (party == null) return;
        Map<String, Integer> totals = activityTotals(party);
        int window = Math.max(1, plugin.getConfig().getInt("progression.identity.window-days", 30));
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&e&lPARTY IDENTITY &8- &f" + parties.display(party)));
        player.sendMessage(Util.color("&7Identity: &e" + identityDisplay(identity(party)) + " &8| &7Window: &f" + window + " hari"));
        player.sendMessage(Util.color("&cCombat &f" + totals.get("combat") + " &8| &bMining &f" + totals.get("mining")
                + " &8| &aFarming &f" + totals.get("farming") + " &8| &6Projects &f" + totals.get("projects")));
        player.sendMessage(Util.color("&8Identity dibentuk otomatis dari aktivitas Party, bukan dipilih manual."));
    }

    public Map<String, Integer> activityTotals(String party) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (String type : ACTIVITY_TYPES) totals.put(type, 0);
        ConfigurationSection section = db.parties.getConfigurationSection("parties." + party + ".progression.activity");
        if (section == null) return totals;
        LocalDate cutoff = LocalDate.now().minusDays(Math.max(1, plugin.getConfig().getInt("progression.identity.window-days", 30)) - 1L);
        for (String dateKey : section.getKeys(false)) {
            LocalDate date;
            try { date = LocalDate.parse(dateKey); } catch (Exception ignored) { continue; }
            if (date.isBefore(cutoff)) continue;
            for (String type : ACTIVITY_TYPES) totals.merge(type, section.getInt(dateKey + "." + type, 0), Integer::sum);
        }
        return totals;
    }

    private void updateIdentity(String party) {
        String path = "parties." + party + ".progression.identity.current";
        String next = identity(party);
        String old = db.parties.getString(path, "DEVELOPING");
        if (next.equals(old)) return;
        db.parties.set(path, next);
        db.parties.set("parties." + party + ".progression.identity.changed-at", System.currentTimeMillis());
        if (plugin.getConfig().getBoolean("progression.identity.announce-change", true)) {
            parties.broadcastParty(party, parties.prefix() + " &eParty Identity berubah: &f" + identityDisplay(old)
                    + " &8→ &e" + identityDisplay(next));
        }
    }

    private void pruneOldActivity(String party) {
        ConfigurationSection section = db.parties.getConfigurationSection("parties." + party + ".progression.activity");
        if (section == null) return;
        LocalDate cutoff = LocalDate.now().minusDays(Math.max(1, plugin.getConfig().getInt("progression.identity.window-days", 30)) + 7L);
        for (String dateKey : new ArrayList<>(section.getKeys(false))) {
            try {
                if (LocalDate.parse(dateKey).isBefore(cutoff)) db.parties.set("parties." + party + ".progression.activity." + dateKey, null);
            } catch (Exception ignored) { }
        }
    }

    // ---------------------------------------------------------------------
    // Combined profile
    // ---------------------------------------------------------------------

    public void showProfile(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        player.sendMessage(Util.color("&8&m================================"));
        player.sendMessage(Util.color("&b&lPARTY PROFILE &8- &f" + parties.display(party)));
        player.sendMessage(Util.color("&7Level: &b" + parties.level(party) + " &8| &7Party XP: &f" + parties.rep(party)
                + " &8| &7Member: &f" + parties.memberCount(party) + "/" + parties.memberLimit(party)));
        if (moduleEnabled("identity")) player.sendMessage(Util.color("&7Identity: &e" + identityDisplay(identity(party))));
        if (moduleEnabled("skill-tree")) player.sendMessage(Util.color("&7Skill Point: &d" + availableSkillPoints(party)));
        if (moduleEnabled("divisions")) player.sendMessage(Util.color("&7Divisimu: &b" + divisionDisplay(divisionOf(player.getUniqueId()))));
        if (moduleEnabled("projects")) {
            String project = currentProject(party);
            if (project == null) player.sendMessage(Util.color("&7Project: &8Tidak aktif"));
            else player.sendMessage(Util.color("&7Project: &6" + projectName(project) + " &8| &f" + (int)Math.floor(projectProgress(party)) + "/" + projectGoal(project)));
        }
        player.sendMessage(Util.color("&8/party project | /party skill | /party division | /party identity"));
    }

    private String requireParty(Player player) {
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) msg(player, " &cKamu belum punya Party.");
        return party;
    }

    private void msg(Player player, String text) {
        player.sendMessage(Util.color(parties.prefix() + text));
    }

    private List<String> filter(Collection<String> values, String input) {
        String q = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return values.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(q)).toList();
    }

    private boolean isMatureCrop(Block block) {
        String name = block.getType().name();
        boolean crop = name.equals("WHEAT") || name.equals("CARROTS") || name.equals("POTATOES")
                || name.equals("BEETROOTS") || name.equals("NETHER_WART") || name.equals("COCOA");
        if (!crop) return false;
        Object data = block.getBlockData();
        return !(data instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge();
    }

    private String locationKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
