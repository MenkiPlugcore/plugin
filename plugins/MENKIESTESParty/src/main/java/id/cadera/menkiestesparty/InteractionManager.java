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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MENKIESTESParty v1.3.0 interaction layer.
 *
 * Keeps cross-Party gameplay independent from the core Party/progression files.
 * Persistent interaction state lives in interactions.yml and every module can
 * be disabled independently.
 */
public final class InteractionManager implements Listener, CommandExecutor, TabCompleter {
    private static final Set<String> CONTRACT_TYPES = Set.of("mining", "hunter", "farmer");
    private static final Set<String> NESTED = Set.of(
            "contract", "contracts",
            "diplomacy", "diplo",
            "apply", "applications", "apps",
            "recruitment", "recruit",
            "browse", "rankperms", "ranks",
            "interaction", "interactions"
    );

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final StorageBundle db;
    private final Map<String, Set<String>> activeContractsByTarget = new HashMap<>();
    private final Set<String> placedMining = ConcurrentHashMap.newKeySet();

    public InteractionManager(MENKIESTESPartyPlugin plugin, PartyService parties, StorageBundle db) {
        this.plugin = plugin;
        this.parties = parties;
        this.db = db;
        rebuildContractIndex();
        for (String name : List.of(
                "partycontract", "partydiplomacy", "partyapply", "partyapplications",
                "partyrecruitment", "partybrowse", "partyrankperms", "partyinteraction")) {
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
    // Rank capability layer for v1.3 interactions.
    // Owner is always allowed to prevent accidental lockout. Officer/Member
    // grants are fully configurable in config.yml.
    // ---------------------------------------------------------------------

    public boolean rankAllows(UUID uuid, String capability) {
        PartyService.Role role = parties.role(uuid);
        if (role == null) return false;
        if (role == PartyService.Role.OWNER) return true;
        if (!plugin.getConfig().getBoolean("rank-permissions.enabled", true)) {
            return role == PartyService.Role.OFFICER;
        }
        String key = role.name().toLowerCase(Locale.ROOT);
        List<String> grants = plugin.getConfig().getStringList("rank-permissions." + key);
        for (String grant : grants) {
            if (grant == null) continue;
            String g = grant.trim().toLowerCase(Locale.ROOT);
            String c = capability.toLowerCase(Locale.ROOT);
            if (g.equals("*") || g.equals(c)) return true;
            if (g.endsWith(".*") && c.startsWith(g.substring(0, g.length() - 1))) return true;
        }
        return false;
    }

    public List<String> grantsFor(PartyService.Role role) {
        if (role == PartyService.Role.OWNER) return List.of("*");
        return plugin.getConfig().getStringList("rank-permissions." + role.name().toLowerCase(Locale.ROOT));
    }

    public void showRankPermissions(Player player) {
        PartyService.Role role = parties.role(player.getUniqueId());
        if (role == null) {
            msg(player, " &cKamu belum punya Party.");
            return;
        }
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&d&lPARTY RANK PERMISSIONS"));
        player.sendMessage(Util.color("&7Role-mu: &f" + role.name()));
        if (!plugin.getConfig().getBoolean("rank-permissions.enabled", true)) {
            player.sendMessage(Util.color("&7Mode: &eLEGACY &8- &7Owner/Officer mendapat akses management."));
            return;
        }
        List<String> grants = grantsFor(role);
        if (grants.isEmpty()) player.sendMessage(Util.color("&8Tidak ada capability management untuk role ini."));
        else for (String grant : grants) player.sendMessage(Util.color("&7- &f" + grant));
        player.sendMessage(Util.color("&8Server owner mengatur daftar ini dari config.yml."));
    }

    // ---------------------------------------------------------------------
    // Contract activity listeners
    // ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!moduleEnabled("contracts")) return;
        if (!parties.isMiningMaterial(event.getBlockPlaced().getType())) return;
        placedMining.add(locationKey(event.getBlockPlaced()));
        int cap = Math.max(100, plugin.getConfig().getInt("interaction.contracts.placed-block-memory-cap", 5000));
        if (placedMining.size() > cap) {
            var it = placedMining.iterator();
            if (it.hasNext()) { it.next(); it.remove(); }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!moduleEnabled("contracts")) return;
        Player player = event.getPlayer();
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) return;
        Block block = event.getBlock();
        if (parties.isMiningMaterial(block.getType())) {
            String key = locationKey(block);
            if (!placedMining.remove(key)) progressContracts(party, "mining", 1, player.getUniqueId());
        }
        if (isMatureCrop(block)) progressContracts(party, "farmer", 1, player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!moduleEnabled("contracts") || event.getEntity() instanceof Player) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        String party = parties.partyOf(killer.getUniqueId());
        if (party == null) return;
        progressContracts(party, "hunter", 1, killer.getUniqueId());
    }

    // ---------------------------------------------------------------------
    // Commands and nested /party routing
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "partycontract" -> commandContract(sender, args);
            case "partydiplomacy" -> commandDiplomacy(sender, args);
            case "partyapply" -> commandApply(sender, args);
            case "partyapplications" -> commandApplications(sender, args);
            case "partyrecruitment" -> commandRecruitment(sender, args);
            case "partybrowse" -> commandBrowse(sender, args);
            case "partyrankperms" -> commandRankPerms(sender);
            case "partyinteraction" -> commandInteraction(sender);
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
        String[] args = split.length <= 2 ? new String[0] : java.util.Arrays.copyOfRange(split, 2, split.length);
        switch (sub) {
            case "contract", "contracts" -> commandContract(event.getPlayer(), args);
            case "diplomacy", "diplo" -> commandDiplomacy(event.getPlayer(), args);
            case "apply" -> commandApply(event.getPlayer(), args);
            case "applications", "apps" -> commandApplications(event.getPlayer(), args);
            case "recruitment", "recruit" -> commandRecruitment(event.getPlayer(), args);
            case "browse" -> commandBrowse(event.getPlayer(), args);
            case "rankperms", "ranks" -> commandRankPerms(event.getPlayer());
            case "interaction", "interactions" -> commandInteraction(event.getPlayer());
        }
    }

    @EventHandler
    public void onPartyTab(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) return;
        String raw = buffer.substring(1);
        boolean trailing = raw.endsWith(" ");
        String[] split = raw.trim().isEmpty() ? new String[0] : raw.trim().split("\\s+");
        if (split.length == 0) return;
        String root = split[0].toLowerCase(Locale.ROOT);
        if (!root.equals("party") && !root.equals("p") && !root.equals("parties")) return;

        if (split.length == 1 || (split.length == 2 && !trailing)) {
            String typed = split.length == 2 ? split[1].toLowerCase(Locale.ROOT) : "";
            List<String> out = new ArrayList<>(event.getCompletions());
            for (String option : List.of("interaction", "contract", "diplomacy", "browse", "apply", "applications", "recruitment", "rankperms")) {
                if (option.startsWith(typed) && out.stream().noneMatch(x -> x.equalsIgnoreCase(option))) out.add(option);
            }
            event.setCompletions(out);
            return;
        }

        if (split.length < 2) return;
        String sub = split[1].toLowerCase(Locale.ROOT);
        String[] args = split.length <= 2 ? new String[0] : java.util.Arrays.copyOfRange(split, 2, split.length);
        if (trailing) args = java.util.Arrays.copyOf(args, args.length + 1);
        List<String> suggestions = complete(sub, event.getSender(), args);
        if (!suggestions.isEmpty()) event.setCompletions(suggestions);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "partycontract" -> complete("contract", sender, args);
            case "partydiplomacy" -> complete("diplomacy", sender, args);
            case "partyapply" -> complete("apply", sender, args);
            case "partyapplications" -> complete("applications", sender, args);
            case "partyrecruitment" -> complete("recruitment", sender, args);
            default -> List.of();
        };
    }

    private List<String> complete(String sub, CommandSender sender, String[] args) {
        String typed = args.length == 0 ? "" : args[args.length - 1];
        if (sub.equals("contract") || sub.equals("contracts")) {
            if (args.length <= 1) return filter(List.of("list", "info", "create", "accept", "deny", "cancel", "abandon"), typed);
            if (args.length == 2 && List.of("info", "accept", "deny", "cancel", "abandon").contains(args[0].toLowerCase(Locale.ROOT))) {
                return filter(relevantContractIds(sender), typed);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("create")) return filter(partyKeys(), typed);
            if (args.length == 3 && args[0].equalsIgnoreCase("create")) return filter(CONTRACT_TYPES, typed);
        }
        if (sub.equals("diplomacy") || sub.equals("diplo")) {
            if (args.length <= 1) return filter(List.of("list", "status", "request", "accept", "deny", "neutral", "rival"), typed);
            if (args.length == 2 && List.of("status", "request", "accept", "deny", "neutral", "rival").contains(args[0].toLowerCase(Locale.ROOT))) {
                return filter(partyKeys(), typed);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("request")) return filter(List.of("ally"), typed);
        }
        if (sub.equals("apply")) {
            if (args.length <= 1) {
                List<String> values = new ArrayList<>(recruitablePartyKeys());
                values.add("cancel");
                return filter(values, typed);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) return filter(applicationPartyKeys(sender), typed);
        }
        if (sub.equals("applications") || sub.equals("apps")) {
            if (args.length <= 1) return filter(List.of("list", "accept", "deny"), typed);
            if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
                return filter(pendingApplicantNames(sender), typed);
            }
        }
        if (sub.equals("recruitment") || sub.equals("recruit")) {
            if (args.length <= 1) return filter(List.of("open", "application", "closed"), typed);
        }
        return List.of();
    }

    private boolean commandInteraction(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        showOverview(player);
        return true;
    }

    private boolean commandRankPerms(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        showRankPermissions(player);
        return true;
    }

    private boolean commandContract(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (!moduleEnabled("contracts")) { msg(player, " &cParty Contracts dinonaktifkan."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { showContracts(player); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> {
                if (args.length < 2) msg(player, " &c/party contract info <id>");
                else showContractInfo(player, args[1]);
            }
            case "create" -> {
                if (args.length < 4) {
                    msg(player, " &c/party contract create <party> <mining|hunter|farmer> <goal>");
                } else {
                    int min = Math.max(1, plugin.getConfig().getInt("interaction.contracts.min-goal", 10));
                    int max = Math.max(min, plugin.getConfig().getInt("interaction.contracts.max-goal", 10000));
                    int goal;
                    try { goal = Integer.parseInt(args[3]); }
                    catch (Exception e) { msg(player, " &cGoal harus berupa angka."); return true; }
                    goal = Math.max(min, Math.min(max, goal));
                    createContract(player, args[1], args[2], goal);
                }
            }
            case "accept" -> {
                if (args.length < 2) msg(player, " &c/party contract accept <id>");
                else acceptContract(player, args[1]);
            }
            case "deny" -> {
                if (args.length < 2) msg(player, " &c/party contract deny <id>");
                else denyContract(player, args[1]);
            }
            case "cancel" -> {
                if (args.length < 2) msg(player, " &c/party contract cancel <id>");
                else cancelContract(player, args[1]);
            }
            case "abandon" -> {
                if (args.length < 2) msg(player, " &c/party contract abandon <id>");
                else abandonContract(player, args[1]);
            }
            default -> msg(player, " &c/party contract [list|info|create|accept|deny|cancel|abandon]");
        }
        return true;
    }

    private boolean commandDiplomacy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (!moduleEnabled("diplomacy")) { msg(player, " &cParty Diplomacy dinonaktifkan."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { showDiplomacy(player); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                if (args.length < 2) msg(player, " &c/party diplomacy status <party>");
                else showDiplomacyStatus(player, args[1]);
            }
            case "request" -> {
                if (args.length < 2) msg(player, " &c/party diplomacy request <party> ally");
                else if (args.length >= 3 && !args[2].equalsIgnoreCase("ally")) msg(player, " &cSaat ini request diplomacy hanya mendukung ALLY.");
                else requestAlliance(player, args[1]);
            }
            case "accept" -> {
                if (args.length < 2) msg(player, " &c/party diplomacy accept <party>");
                else acceptAlliance(player, args[1]);
            }
            case "deny" -> {
                if (args.length < 2) msg(player, " &c/party diplomacy deny <party>");
                else denyAlliance(player, args[1]);
            }
            case "neutral" -> {
                if (args.length < 2) msg(player, " &c/party diplomacy neutral <party>");
                else setNeutral(player, args[1]);
            }
            case "rival" -> {
                if (args.length < 2) msg(player, " &c/party diplomacy rival <party>");
                else declareRival(player, args[1]);
            }
            default -> msg(player, " &c/party diplomacy [list|status|request|accept|deny|neutral|rival]");
        }
        return true;
    }

    private boolean commandApply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (!moduleEnabled("applications")) { msg(player, " &cParty Applications dinonaktifkan."); return true; }
        if (args.length == 0) { msg(player, " &7Gunakan &f/party browse &7lalu &f/party apply <party> [pesan]"); return true; }
        if (args[0].equalsIgnoreCase("cancel")) {
            if (args.length < 2) msg(player, " &c/party apply cancel <party>");
            else cancelApplication(player, args[1]);
            return true;
        }
        String note = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
        apply(player, args[0], note);
        return true;
    }

    private boolean commandApplications(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (!moduleEnabled("applications")) { msg(player, " &cParty Applications dinonaktifkan."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { showApplications(player); return true; }
        if (args[0].equalsIgnoreCase("accept")) {
            if (args.length < 2) msg(player, " &c/party applications accept <player>");
            else acceptApplication(player, args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("deny")) {
            if (args.length < 2) msg(player, " &c/party applications deny <player>");
            else denyApplication(player, args[1]);
            return true;
        }
        msg(player, " &c/party applications [list|accept <player>|deny <player>]");
        return true;
    }

    private boolean commandRecruitment(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (!moduleEnabled("applications")) { msg(player, " &cParty Applications dinonaktifkan."); return true; }
        String party = requireParty(player);
        if (party == null) return true;
        if (args.length == 0) {
            msg(player, " &7Recruitment: &f" + recruitmentMode(party) + " &8| &7/party recruitment <open|application|closed>");
            return true;
        }
        setRecruitment(player, args[0]);
        return true;
    }

    private boolean commandBrowse(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (!moduleEnabled("applications")) { msg(player, " &cParty Applications dinonaktifkan."); return true; }
        int page = 1;
        if (args.length > 0) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (Exception ignored) { }
        }
        browse(player, page);
        return true;
    }

    // ---------------------------------------------------------------------
    // Interaction overview
    // ---------------------------------------------------------------------

    public void showOverview(Player player) {
        player.sendMessage(Util.color("&8&m================================"));
        player.sendMessage(Util.color("&6&lPARTY INTERACTION &8- &fv1.3.0"));
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Util.color("&7Kamu belum punya Party."));
            if (moduleEnabled("applications")) {
                player.sendMessage(Util.color("&f/party browse &7- cari Party yang merekrut"));
                player.sendMessage(Util.color("&f/party apply <party> [pesan]"));
            }
            return;
        }
        player.sendMessage(Util.color("&7Party: &b" + parties.display(party) + " &8| &7Role: &f" + parties.role(player.getUniqueId())));
        if (moduleEnabled("contracts")) {
            player.sendMessage(Util.color("&7Contracts aktif: &6" + activeContractCount(party)
                    + " &8| &7Selesai: &a" + completedContractCount(party) + " &8| &f/party contract"));
        }
        if (moduleEnabled("diplomacy")) {
            player.sendMessage(Util.color("&7Diplomacy: &f/party diplomacy &8| &7Trust tersimpan per pasangan Party"));
        }
        if (moduleEnabled("applications")) {
            player.sendMessage(Util.color("&7Recruitment: &f" + recruitmentMode(party)
                    + " &8| &7Pending apps: &e" + pendingApplicationCount(party)));
        }
        player.sendMessage(Util.color("&7Rank capability: &f/party rankperms"));
    }

    // ---------------------------------------------------------------------
    // Party Contracts
    // ---------------------------------------------------------------------

    public int activeContractCount(String party) {
        return activeContractsByTarget.getOrDefault(party, Set.of()).size();
    }

    public int completedContractCount(String party) {
        return db.interactions.getInt("stats." + party + ".contracts.completed", 0);
    }

    public void showContracts(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&6&lPARTY CONTRACTS &8- &f" + parties.display(party)));
        List<String> ids = relevantContractIds(party);
        if (ids.isEmpty()) {
            player.sendMessage(Util.color("&7Belum ada kontrak terkait Party ini."));
        } else {
            ids.sort(Comparator.comparingLong(this::contractCreatedAt).reversed());
            int shown = 0;
            for (String id : ids) {
                if (shown++ >= 12) break;
                String source = contractString(id, "source");
                String target = contractString(id, "target");
                String type = contractString(id, "type");
                int goal = contractInt(id, "goal");
                int progress = contractInt(id, "progress");
                player.sendMessage(Util.color("&e" + id + " &8[&f" + contractStatus(id) + "&8] &7"
                        + displaySafe(source) + " &8→ &7" + displaySafe(target)
                        + " &8| &b" + type + " &f" + Math.min(progress, goal) + "/" + goal));
            }
        }
        player.sendMessage(Util.color("&7Buat: &f/party contract create <party> <type> <goal>"));
        player.sendMessage(Util.color("&7Detail: &f/party contract info <id>"));
    }

    public void showContractInfo(Player player, String idInput) {
        String party = requireParty(player);
        if (party == null) return;
        String id = normalizeContractId(idInput);
        if (!contractExists(id) || !contractRelatesTo(id, party)) { msg(player, " &cContract tidak ditemukan untuk Party-mu."); return; }
        String source = contractString(id, "source");
        String target = contractString(id, "target");
        int goal = contractInt(id, "goal");
        int progress = contractInt(id, "progress");
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&6&lCONTRACT " + id));
        player.sendMessage(Util.color("&7Status: &f" + contractStatus(id)));
        player.sendMessage(Util.color("&7Issuer: &f" + displaySafe(source) + " &8→ &7Assignee: &f" + displaySafe(target)));
        player.sendMessage(Util.color("&7Type: &b" + contractString(id, "type") + " &8| &7Progress: &f" + Math.min(progress, goal) + "/" + goal));
        int reward = Math.max(0, plugin.getConfig().getInt("interaction.contracts.party-xp-reward", 0));
        int trust = plugin.getConfig().getInt("interaction.contracts.trust-on-complete", 5);
        player.sendMessage(Util.color("&7Completion: &dTrust +" + trust + (reward > 0 ? " &8| &bParty XP +" + reward : " &8| &7No automatic XP reward")));
    }

    public void createContract(Player actor, String targetInput, String typeInput, int goal) {
        String source = requireParty(actor);
        if (source == null) return;
        if (!rankAllows(actor.getUniqueId(), "contracts.create")) { msg(actor, " &cRole-mu tidak punya capability contracts.create."); return; }
        String target = Util.key(targetInput);
        String type = typeInput == null ? "" : typeInput.toLowerCase(Locale.ROOT);
        if (!parties.exists(target)) { msg(actor, " &cParty target tidak ditemukan."); return; }
        if (source.equals(target)) { msg(actor, " &cTidak dapat membuat Contract untuk Party sendiri."); return; }
        if (!CONTRACT_TYPES.contains(type)) { msg(actor, " &cType harus mining, hunter, atau farmer."); return; }
        if (!plugin.getConfig().getBoolean("interaction.contracts.allow-rival-parties", false)
                && relation(source, target).equals("RIVAL")) {
            msg(actor, " &cContract tidak dapat dibuat dengan Party berstatus RIVAL."); return;
        }
        int maxOpen = Math.max(1, plugin.getConfig().getInt("interaction.contracts.max-open-per-party", 3));
        if (openContractCount(source) >= maxOpen) { msg(actor, " &cParty-mu sudah mencapai batas Contract terbuka: " + maxOpen + "."); return; }
        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(0L, plugin.getConfig().getLong("interaction.contracts.pair-cooldown-minutes", 60L)) * 60_000L;
        String pair = pairKey(source, target);
        long last = db.interactions.getLong("cooldowns.contract-pair." + pair, 0L);
        if (last > 0 && now - last < cooldownMs) {
            long mins = Math.max(1, (cooldownMs - (now - last) + 59_999L) / 60_000L);
            msg(actor, " &cCooldown Contract antar Party masih " + mins + " menit."); return;
        }

        String id = nextContractId();
        String base = "contracts." + id;
        long proposalHours = Math.max(1L, plugin.getConfig().getLong("interaction.contracts.proposal-expire-hours", 24L));
        db.interactions.set(base + ".source", source);
        db.interactions.set(base + ".target", target);
        db.interactions.set(base + ".creator", actor.getUniqueId().toString());
        db.interactions.set(base + ".type", type);
        db.interactions.set(base + ".goal", goal);
        db.interactions.set(base + ".progress", 0);
        db.interactions.set(base + ".status", "PENDING");
        db.interactions.set(base + ".created-at", now);
        db.interactions.set(base + ".proposal-expires-at", now + proposalHours * 3_600_000L);
        db.interactions.set("cooldowns.contract-pair." + pair, now);
        plugin.saveDataSoon();
        parties.broadcastParty(source, parties.prefix() + " &6Contract &f" + id + " &7dikirim ke &f" + parties.display(target)
                + " &8| &b" + type + " &f" + goal);
        parties.broadcastParty(target, parties.prefix() + " &eContract baru &f" + id + " &edari &f" + parties.display(source)
                + "&e. &7/party contract info " + id + " &8| &f/party contract accept " + id);
    }

    public void acceptContract(Player actor, String idInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "contracts.respond")) { msg(actor, " &cRole-mu tidak punya capability contracts.respond."); return; }
        String id = normalizeContractId(idInput);
        if (!contractExists(id) || !party.equals(contractString(id, "target"))) { msg(actor, " &cContract incoming tidak ditemukan."); return; }
        if (!contractStatus(id).equals("PENDING")) { msg(actor, " &cContract bukan dalam status PENDING."); return; }
        long now = System.currentTimeMillis();
        if (db.interactions.getLong("contracts." + id + ".proposal-expires-at", 0L) < now) {
            setContractEnded(id, "EXPIRED", now);
            msg(actor, " &cProposal Contract sudah expired."); return;
        }
        int maxActive = Math.max(1, plugin.getConfig().getInt("interaction.contracts.max-active-per-party", 2));
        if (activeContractCount(party) >= maxActive) { msg(actor, " &cParty-mu sudah memiliki " + maxActive + " Contract aktif."); return; }
        long activeHours = Math.max(1L, plugin.getConfig().getLong("interaction.contracts.active-expire-hours", 48L));
        db.interactions.set("contracts." + id + ".status", "ACTIVE");
        db.interactions.set("contracts." + id + ".accepted-at", now);
        db.interactions.set("contracts." + id + ".accepted-by", actor.getUniqueId().toString());
        db.interactions.set("contracts." + id + ".deadline", now + activeHours * 3_600_000L);
        indexActive(id, party);
        plugin.saveDataSoon();
        String source = contractString(id, "source");
        parties.broadcastParty(source, parties.prefix() + " &aContract &f" + id + " &aditerima oleh &f" + parties.display(party) + "&a.");
        parties.broadcastParty(party, parties.prefix() + " &aContract &f" + id + " &aaktif. &7Target: &f"
                + contractInt(id, "goal") + " " + contractString(id, "type"));
    }

    public void denyContract(Player actor, String idInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "contracts.respond")) { msg(actor, " &cRole-mu tidak punya capability contracts.respond."); return; }
        String id = normalizeContractId(idInput);
        if (!contractExists(id) || !party.equals(contractString(id, "target")) || !contractStatus(id).equals("PENDING")) {
            msg(actor, " &cContract PENDING tidak ditemukan."); return;
        }
        setContractEnded(id, "DENIED", System.currentTimeMillis());
        String source = contractString(id, "source");
        parties.broadcastParty(source, parties.prefix() + " &cContract &f" + id + " &cditolak oleh &f" + parties.display(party) + "&c.");
        msg(actor, " &aContract ditolak.");
    }

    public void cancelContract(Player actor, String idInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "contracts.create")) { msg(actor, " &cRole-mu tidak punya capability contracts.create."); return; }
        String id = normalizeContractId(idInput);
        if (!contractExists(id) || !party.equals(contractString(id, "source")) || !contractStatus(id).equals("PENDING")) {
            msg(actor, " &cHanya proposal Contract milik Party-mu yang masih PENDING dapat dibatalkan."); return;
        }
        String target = contractString(id, "target");
        setContractEnded(id, "CANCELLED", System.currentTimeMillis());
        parties.broadcastParty(target, parties.prefix() + " &eContract &f" + id + " &edibatalkan oleh issuer.");
        msg(actor, " &aContract dibatalkan.");
    }

    public void abandonContract(Player actor, String idInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "contracts.respond")) { msg(actor, " &cRole-mu tidak punya capability contracts.respond."); return; }
        String id = normalizeContractId(idInput);
        if (!contractExists(id) || !party.equals(contractString(id, "target")) || !contractStatus(id).equals("ACTIVE")) {
            msg(actor, " &cContract ACTIVE yang ditugaskan ke Party-mu tidak ditemukan."); return;
        }
        String source = contractString(id, "source");
        setContractEnded(id, "FAILED", System.currentTimeMillis());
        unindexActive(id, party);
        int penalty = Math.max(0, plugin.getConfig().getInt("interaction.contracts.trust-on-fail", 2));
        changeTrust(source, party, -penalty);
        parties.broadcastParty(source, parties.prefix() + " &cContract &f" + id + " &cgagal/ditinggalkan oleh &f" + parties.display(party) + "&c.");
        parties.broadcastParty(party, parties.prefix() + " &cContract &f" + id + " &cditinggalkan. &7Trust -" + penalty);
    }

    private void progressContracts(String party, String type, int amount, UUID contributor) {
        Set<String> ids = new LinkedHashSet<>(activeContractsByTarget.getOrDefault(party, Set.of()));
        if (ids.isEmpty()) return;
        for (String id : ids) {
            if (!contractExists(id) || !contractStatus(id).equals("ACTIVE")) {
                unindexActive(id, party);
                continue;
            }
            if (!type.equalsIgnoreCase(contractString(id, "type"))) continue;
            String base = "contracts." + id;
            int goal = contractInt(id, "goal");
            int next = Math.min(goal, contractInt(id, "progress") + amount);
            db.interactions.set(base + ".progress", next);
            String cpath = base + ".contributors." + contributor;
            db.interactions.set(cpath, db.interactions.getInt(cpath, 0) + amount);
            plugin.saveDataSoon();
            if (next >= goal) completeContract(id);
        }
    }

    private void completeContract(String id) {
        if (!contractStatus(id).equals("ACTIVE")) return;
        long now = System.currentTimeMillis();
        String source = contractString(id, "source");
        String target = contractString(id, "target");
        setContractEnded(id, "COMPLETED", now);
        unindexActive(id, target);
        db.interactions.set("stats." + target + ".contracts.completed", completedContractCount(target) + 1);
        int trustGain = Math.max(0, plugin.getConfig().getInt("interaction.contracts.trust-on-complete", 5));
        changeTrust(source, target, trustGain);
        int xp = Math.max(0, plugin.getConfig().getInt("interaction.contracts.party-xp-reward", 0));
        if (xp > 0 && parties.exists(target)) parties.addRep(target, xp);
        plugin.saveDataSoon();
        if (parties.exists(source)) parties.broadcastParty(source, parties.prefix() + " &aContract &f" + id + " &aselesai oleh &f" + displaySafe(target) + "&a. &dTrust +" + trustGain);
        if (parties.exists(target)) parties.broadcastParty(target, parties.prefix() + " &a&lCONTRACT SELESAI! &f" + id
                + " &8| &dTrust +" + trustGain + (xp > 0 ? " &8| &b+" + xp + " Party XP" : ""));
    }

    private void rebuildContractIndex() {
        activeContractsByTarget.clear();
        ConfigurationSection sec = db.interactions.getConfigurationSection("contracts");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            if (contractStatus(id).equals("ACTIVE")) {
                String target = contractString(id, "target");
                if (target != null) indexActive(id, target);
            }
        }
    }

    private void indexActive(String id, String party) {
        activeContractsByTarget.computeIfAbsent(party, k -> new LinkedHashSet<>()).add(id);
    }

    private void unindexActive(String id, String party) {
        Set<String> set = activeContractsByTarget.get(party);
        if (set == null) return;
        set.remove(id);
        if (set.isEmpty()) activeContractsByTarget.remove(party);
    }

    private int openContractCount(String party) {
        int count = 0;
        ConfigurationSection sec = db.interactions.getConfigurationSection("contracts");
        if (sec == null) return 0;
        for (String id : sec.getKeys(false)) {
            if (!contractRelatesTo(id, party)) continue;
            String status = contractStatus(id);
            if (status.equals("PENDING") || status.equals("ACTIVE")) count++;
        }
        return count;
    }

    private List<String> relevantContractIds(String party) {
        List<String> out = new ArrayList<>();
        ConfigurationSection sec = db.interactions.getConfigurationSection("contracts");
        if (sec == null) return out;
        for (String id : sec.getKeys(false)) if (contractRelatesTo(id, party)) out.add(id);
        return out;
    }

    private List<String> relevantContractIds(CommandSender sender) {
        if (!(sender instanceof Player player)) return List.of();
        String party = parties.partyOf(player.getUniqueId());
        return party == null ? List.of() : relevantContractIds(party);
    }

    private boolean contractRelatesTo(String id, String party) {
        return party.equals(contractString(id, "source")) || party.equals(contractString(id, "target"));
    }

    private boolean contractExists(String id) {
        return db.interactions.contains("contracts." + id + ".status");
    }

    private String contractStatus(String id) {
        return db.interactions.getString("contracts." + id + ".status", "UNKNOWN").toUpperCase(Locale.ROOT);
    }

    private String contractString(String id, String key) {
        return db.interactions.getString("contracts." + id + "." + key);
    }

    private int contractInt(String id, String key) {
        return db.interactions.getInt("contracts." + id + "." + key, 0);
    }

    private long contractCreatedAt(String id) {
        return db.interactions.getLong("contracts." + id + ".created-at", 0L);
    }

    private void setContractEnded(String id, String status, long now) {
        db.interactions.set("contracts." + id + ".status", status);
        db.interactions.set("contracts." + id + ".ended-at", now);
        plugin.saveDataSoon();
    }

    private String normalizeContractId(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }

    private String nextContractId() {
        int next = Math.max(1, db.interactions.getInt("meta.next-contract-id", 1));
        db.interactions.set("meta.next-contract-id", next + 1);
        return String.format(Locale.ROOT, "C%06d", next);
    }

    // ---------------------------------------------------------------------
    // Diplomacy + Trust
    // ---------------------------------------------------------------------

    public String relation(String first, String second) {
        if (first == null || second == null) return "NEUTRAL";
        return db.interactions.getString("diplomacy.pairs." + pairKey(first, second) + ".relation", "NEUTRAL").toUpperCase(Locale.ROOT);
    }

    public int trust(String first, String second) {
        return db.interactions.getInt("diplomacy.pairs." + pairKey(first, second) + ".trust", 0);
    }

    public void showDiplomacy(Player player) {
        String party = requireParty(player);
        if (party == null) return;
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&d&lPARTY DIPLOMACY &8- &f" + parties.display(party)));
        ConfigurationSection sec = db.interactions.getConfigurationSection("diplomacy.pairs");
        int shown = 0;
        if (sec != null) {
            for (String pair : sec.getKeys(false)) {
                String a = sec.getString(pair + ".a");
                String b = sec.getString(pair + ".b");
                if (!party.equals(a) && !party.equals(b)) continue;
                String other = party.equals(a) ? b : a;
                String rel = sec.getString(pair + ".relation", "NEUTRAL");
                int score = sec.getInt(pair + ".trust", 0);
                if ("NEUTRAL".equalsIgnoreCase(rel) && score == 0) continue;
                player.sendMessage(Util.color("&7- &f" + displaySafe(other) + " &8| &e" + rel.toUpperCase(Locale.ROOT) + " &8| &dTrust " + score));
                shown++;
            }
        }
        if (shown == 0) player.sendMessage(Util.color("&7Belum ada hubungan Diplomacy yang tercatat."));
        List<String> incoming = incomingAllianceRequests(party);
        if (!incoming.isEmpty()) player.sendMessage(Util.color("&7Incoming Alliance: &f" + String.join(", ", incoming.stream().map(this::displaySafe).toList())));
        player.sendMessage(Util.color("&7/party diplomacy request <party> ally"));
    }

    public void showDiplomacyStatus(Player player, String targetInput) {
        String party = requireParty(player);
        if (party == null) return;
        String target = Util.key(targetInput);
        if (!parties.exists(target) || party.equals(target)) { msg(player, " &cParty target tidak valid."); return; }
        msg(player, " &7" + parties.display(party) + " &8↔ &f" + parties.display(target)
                + " &8| &e" + relation(party, target) + " &8| &dTrust " + trust(party, target));
    }

    public void requestAlliance(Player actor, String targetInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "diplomacy.manage")) { msg(actor, " &cRole-mu tidak punya capability diplomacy.manage."); return; }
        String target = Util.key(targetInput);
        if (!parties.exists(target) || target.equals(party)) { msg(actor, " &cParty target tidak valid."); return; }
        if (relation(party, target).equals("ALLY")) { msg(actor, " &eKedua Party sudah ALLY."); return; }

        String reverse = "diplomacy.requests." + party + "." + target;
        long now = System.currentTimeMillis();
        if (db.interactions.contains(reverse + ".expires-at") && db.interactions.getLong(reverse + ".expires-at") >= now) {
            db.interactions.set(reverse, null);
            setRelation(party, target, "ALLY");
            setTrustAtLeast(party, target, 10);
            parties.broadcastParty(party, parties.prefix() + " &aAlliance terbentuk dengan &f" + parties.display(target) + "&a.");
            parties.broadcastParty(target, parties.prefix() + " &aAlliance terbentuk dengan &f" + parties.display(party) + "&a.");
            plugin.saveDataSoon();
            return;
        }

        long hours = Math.max(1L, plugin.getConfig().getLong("interaction.diplomacy.request-expire-hours", 24L));
        String base = "diplomacy.requests." + target + "." + party;
        db.interactions.set(base + ".from", party);
        db.interactions.set(base + ".to", target);
        db.interactions.set(base + ".type", "ALLY");
        db.interactions.set(base + ".created-at", now);
        db.interactions.set(base + ".expires-at", now + hours * 3_600_000L);
        plugin.saveDataSoon();
        msg(actor, " &aAlliance request dikirim ke &f" + parties.display(target) + "&a.");
        parties.broadcastParty(target, parties.prefix() + " &eAlliance request dari &f" + parties.display(party)
                + "&e. &7/party diplomacy accept " + parties.display(party));
    }

    public void acceptAlliance(Player actor, String sourceInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "diplomacy.manage")) { msg(actor, " &cRole-mu tidak punya capability diplomacy.manage."); return; }
        String source = Util.key(sourceInput);
        String base = "diplomacy.requests." + party + "." + source;
        long now = System.currentTimeMillis();
        if (!db.interactions.contains(base + ".expires-at") || db.interactions.getLong(base + ".expires-at") < now) {
            db.interactions.set(base, null);
            msg(actor, " &cAlliance request tidak ditemukan atau sudah expired."); return;
        }
        db.interactions.set(base, null);
        setRelation(party, source, "ALLY");
        setTrustAtLeast(party, source, 10);
        plugin.saveDataSoon();
        parties.broadcastParty(party, parties.prefix() + " &aAlliance terbentuk dengan &f" + displaySafe(source) + "&a.");
        if (parties.exists(source)) parties.broadcastParty(source, parties.prefix() + " &aAlliance request diterima oleh &f" + parties.display(party) + "&a.");
    }

    public void denyAlliance(Player actor, String sourceInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "diplomacy.manage")) { msg(actor, " &cRole-mu tidak punya capability diplomacy.manage."); return; }
        String source = Util.key(sourceInput);
        String base = "diplomacy.requests." + party + "." + source;
        if (!db.interactions.contains(base)) { msg(actor, " &cAlliance request tidak ditemukan."); return; }
        db.interactions.set(base, null);
        plugin.saveDataSoon();
        msg(actor, " &aAlliance request ditolak.");
        if (parties.exists(source)) parties.broadcastParty(source, parties.prefix() + " &cAlliance request ditolak oleh &f" + parties.display(party) + "&c.");
    }

    public void setNeutral(Player actor, String targetInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "diplomacy.manage")) { msg(actor, " &cRole-mu tidak punya capability diplomacy.manage."); return; }
        String target = Util.key(targetInput);
        if (!parties.exists(target) || target.equals(party)) { msg(actor, " &cParty target tidak valid."); return; }
        setRelation(party, target, "NEUTRAL");
        plugin.saveDataSoon();
        parties.broadcastParty(party, parties.prefix() + " &7Hubungan dengan &f" + parties.display(target) + " &7diubah menjadi NEUTRAL.");
        parties.broadcastParty(target, parties.prefix() + " &7Hubungan dengan &f" + parties.display(party) + " &7diubah menjadi NEUTRAL.");
    }

    public void declareRival(Player actor, String targetInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "diplomacy.manage")) { msg(actor, " &cRole-mu tidak punya capability diplomacy.manage."); return; }
        String target = Util.key(targetInput);
        if (!parties.exists(target) || target.equals(party)) { msg(actor, " &cParty target tidak valid."); return; }
        setRelation(party, target, "RIVAL");
        setTrustAtMost(party, target, -25);
        plugin.saveDataSoon();
        parties.broadcastParty(party, parties.prefix() + " &cParty &f" + parties.display(target) + " &cditetapkan sebagai RIVAL.");
        parties.broadcastParty(target, parties.prefix() + " &cParty &f" + parties.display(party) + " &cmenetapkan kalian sebagai RIVAL.");
    }

    private void setRelation(String first, String second, String relation) {
        String pair = pairKey(first, second);
        String base = "diplomacy.pairs." + pair;
        List<String> ordered = new ArrayList<>(List.of(first, second));
        ordered.sort(String.CASE_INSENSITIVE_ORDER);
        db.interactions.set(base + ".a", ordered.get(0));
        db.interactions.set(base + ".b", ordered.get(1));
        db.interactions.set(base + ".relation", relation.toUpperCase(Locale.ROOT));
        db.interactions.set(base + ".updated-at", System.currentTimeMillis());
    }

    private void changeTrust(String first, String second, int delta) {
        if (!moduleEnabled("diplomacy")) return;
        String pair = pairKey(first, second);
        String base = "diplomacy.pairs." + pair;
        if (!db.interactions.contains(base + ".a")) setRelation(first, second, "NEUTRAL");
        int next = Math.max(-100, Math.min(100, db.interactions.getInt(base + ".trust", 0) + delta));
        db.interactions.set(base + ".trust", next);
        db.interactions.set(base + ".updated-at", System.currentTimeMillis());
        plugin.saveDataSoon();
    }

    private void setTrustAtLeast(String first, String second, int minimum) {
        int current = trust(first, second);
        if (current < minimum) changeTrust(first, second, minimum - current);
    }

    private void setTrustAtMost(String first, String second, int maximum) {
        int current = trust(first, second);
        if (current > maximum) changeTrust(first, second, maximum - current);
    }

    private List<String> incomingAllianceRequests(String party) {
        List<String> out = new ArrayList<>();
        ConfigurationSection sec = db.interactions.getConfigurationSection("diplomacy.requests." + party);
        if (sec == null) return out;
        long now = System.currentTimeMillis();
        for (String source : sec.getKeys(false)) {
            if (sec.getLong(source + ".expires-at", 0L) >= now) out.add(source);
        }
        return out;
    }

    private String pairKey(String first, String second) {
        if (first.compareToIgnoreCase(second) <= 0) return first + "~" + second;
        return second + "~" + first;
    }

    // ---------------------------------------------------------------------
    // Recruitment + Applications
    // ---------------------------------------------------------------------

    public String recruitmentMode(String party) {
        String fallback = plugin.getConfig().getString("interaction.applications.default-recruitment-mode", "APPLICATION");
        String value = db.parties.getString("parties." + party + ".recruitment.mode", fallback == null ? "APPLICATION" : fallback);
        value = value == null ? "APPLICATION" : value.toUpperCase(Locale.ROOT);
        if (!Set.of("OPEN", "APPLICATION", "CLOSED").contains(value)) return "APPLICATION";
        return value;
    }

    public int pendingApplicationCount(String party) {
        ConfigurationSection sec = db.interactions.getConfigurationSection("applications." + party);
        if (sec == null) return 0;
        int count = 0;
        long now = System.currentTimeMillis();
        for (String uuid : sec.getKeys(false)) if (sec.getLong(uuid + ".expires-at", 0L) >= now) count++;
        return count;
    }

    public void setRecruitment(Player actor, String modeInput) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "recruitment.manage")) { msg(actor, " &cRole-mu tidak punya capability recruitment.manage."); return; }
        String mode = modeInput == null ? "" : modeInput.toUpperCase(Locale.ROOT);
        if (!Set.of("OPEN", "APPLICATION", "CLOSED").contains(mode)) {
            msg(actor, " &cMode recruitment: open, application, atau closed."); return;
        }
        db.parties.set("parties." + party + ".recruitment.mode", mode);
        plugin.saveDataSoon();
        parties.broadcastParty(party, parties.prefix() + " &eRecruitment Party sekarang &f" + mode + "&e.");
    }

    public void browse(Player player, int page) {
        List<String> keys = recruitablePartyKeys();
        int perPage = 8;
        int pages = Math.max(1, (keys.size() + perPage - 1) / perPage);
        page = Math.max(1, Math.min(pages, page));
        int start = (page - 1) * perPage;
        int end = Math.min(keys.size(), start + perPage);
        player.sendMessage(Util.color("&8&m--------------------------------"));
        player.sendMessage(Util.color("&b&lPARTY BROWSER &8- &fPage " + page + "/" + pages));
        if (keys.isEmpty()) {
            player.sendMessage(Util.color("&7Tidak ada Party yang sedang membuka recruitment."));
            return;
        }
        for (int i = start; i < end; i++) {
            String party = keys.get(i);
            player.sendMessage(Util.color("&e" + parties.display(party) + " &8| &f" + recruitmentMode(party)
                    + " &8| &7Lv.&f" + parties.level(party)
                    + " &8| &7Member &f" + parties.memberCount(party) + "/" + parties.memberLimit(party)));
        }
        player.sendMessage(Util.color("&7Apply: &f/party apply <party> [pesan]"));
        if (pages > 1) player.sendMessage(Util.color("&7Page lain: &f/party browse <page>"));
    }

    public void apply(Player player, String partyInput, String note) {
        if (parties.inParty(player.getUniqueId())) { msg(player, " &cKamu sudah memiliki Party."); return; }
        String target = Util.key(partyInput);
        if (!parties.exists(target)) { msg(player, " &cParty tidak ditemukan."); return; }
        if (parties.memberCount(target) >= parties.memberLimit(target)) { msg(player, " &cParty tersebut penuh."); return; }
        String mode = recruitmentMode(target);
        if (mode.equals("CLOSED")) { msg(player, " &cRecruitment Party tersebut sedang CLOSED."); return; }
        if (plugin.war().membershipLocked()) { msg(player, " &cMembership sedang dikunci karena Party War."); return; }

        if (mode.equals("OPEN")) {
            if (!addMember(target, player.getUniqueId(), player.getName())) { msg(player, " &cTidak dapat bergabung ke Party tersebut."); return; }
            removeAllApplications(player.getUniqueId());
            parties.broadcastParty(target, parties.prefix() + " &f" + player.getName() + " &abergabung melalui OPEN recruitment.");
            msg(player, " &aKamu bergabung ke Party &f" + parties.display(target) + "&a.");
            return;
        }

        int max = Math.max(1, plugin.getConfig().getInt("interaction.applications.max-active-per-player", 3));
        if (activeApplicationCount(player.getUniqueId()) >= max) { msg(player, " &cBatas application aktif: " + max + "."); return; }
        String base = "applications." + target + "." + player.getUniqueId();
        long now = System.currentTimeMillis();
        if (db.interactions.contains(base + ".expires-at") && db.interactions.getLong(base + ".expires-at") >= now) {
            msg(player, " &eKamu sudah memiliki application aktif ke Party tersebut."); return;
        }
        long hours = Math.max(1L, plugin.getConfig().getLong("interaction.applications.expire-hours", 48L));
        int maxNote = Math.max(0, plugin.getConfig().getInt("interaction.applications.max-note-length", 80));
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() > maxNote) cleanNote = cleanNote.substring(0, maxNote);
        db.interactions.set(base + ".name", player.getName());
        db.interactions.set(base + ".note", cleanNote);
        db.interactions.set(base + ".created-at", now);
        db.interactions.set(base + ".expires-at", now + hours * 3_600_000L);
        plugin.saveDataSoon();
        msg(player, " &aApplication dikirim ke &f" + parties.display(target) + "&a.");
        parties.broadcastParty(target, parties.prefix() + " &eApplication baru dari &f" + player.getName()
                + "&e. &7/party applications");
    }

    public void cancelApplication(Player player, String targetInput) {
        String target = Util.key(targetInput);
        String path = "applications." + target + "." + player.getUniqueId();
        if (!db.interactions.contains(path)) { msg(player, " &cApplication tidak ditemukan."); return; }
        db.interactions.set(path, null);
        plugin.saveDataSoon();
        msg(player, " &aApplication ke &f" + displaySafe(target) + " &adibatalkan.");
    }

    public void showApplications(Player actor) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "applications.manage")) { msg(actor, " &cRole-mu tidak punya capability applications.manage."); return; }
        ConfigurationSection sec = db.interactions.getConfigurationSection("applications." + party);
        actor.sendMessage(Util.color("&8&m--------------------------------"));
        actor.sendMessage(Util.color("&e&lPARTY APPLICATIONS &8- &f" + parties.display(party)));
        if (sec == null || sec.getKeys(false).isEmpty()) {
            actor.sendMessage(Util.color("&7Tidak ada application aktif.")); return;
        }
        long now = System.currentTimeMillis();
        int shown = 0;
        for (String uuid : sec.getKeys(false)) {
            if (sec.getLong(uuid + ".expires-at", 0L) < now) continue;
            String name = sec.getString(uuid + ".name", uuid.substring(0, Math.min(8, uuid.length())));
            String note = sec.getString(uuid + ".note", "");
            actor.sendMessage(Util.color("&e- &f" + name + (note == null || note.isBlank() ? "" : " &8| &7" + note)));
            shown++;
        }
        if (shown == 0) actor.sendMessage(Util.color("&7Tidak ada application aktif."));
        else actor.sendMessage(Util.color("&7/party applications accept <player> &8| &7deny <player>"));
    }

    public void acceptApplication(Player actor, String applicantName) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "applications.manage")) { msg(actor, " &cRole-mu tidak punya capability applications.manage."); return; }
        if (plugin.war().membershipLocked()) { msg(actor, " &cMembership sedang dikunci karena Party War."); return; }
        UUID applicant = findApplicant(party, applicantName);
        if (applicant == null) { msg(actor, " &cApplication player tidak ditemukan."); return; }
        String path = "applications." + party + "." + applicant;
        if (db.interactions.getLong(path + ".expires-at", 0L) < System.currentTimeMillis()) {
            db.interactions.set(path, null); plugin.saveDataSoon(); msg(actor, " &cApplication sudah expired."); return;
        }
        if (parties.inParty(applicant)) {
            db.interactions.set(path, null); plugin.saveDataSoon(); msg(actor, " &cPlayer tersebut sudah bergabung ke Party lain."); return;
        }
        if (parties.memberCount(party) >= parties.memberLimit(party)) { msg(actor, " &cSlot Party penuh."); return; }
        String storedName = db.interactions.getString(path + ".name", applicantName);
        if (!addMember(party, applicant, storedName)) { msg(actor, " &cGagal menerima applicant."); return; }
        removeAllApplications(applicant);
        plugin.saveDataSoon();
        parties.broadcastParty(party, parties.prefix() + " &f" + storedName + " &abergabung melalui application.");
        Player online = Bukkit.getPlayer(applicant);
        if (online != null) msg(online, " &aApplication diterima. Kamu sekarang member &f" + parties.display(party) + "&a.");
    }

    public void denyApplication(Player actor, String applicantName) {
        String party = requireParty(actor);
        if (party == null) return;
        if (!rankAllows(actor.getUniqueId(), "applications.manage")) { msg(actor, " &cRole-mu tidak punya capability applications.manage."); return; }
        UUID applicant = findApplicant(party, applicantName);
        if (applicant == null) { msg(actor, " &cApplication player tidak ditemukan."); return; }
        String path = "applications." + party + "." + applicant;
        String name = db.interactions.getString(path + ".name", applicantName);
        db.interactions.set(path, null);
        plugin.saveDataSoon();
        msg(actor, " &aApplication &f" + name + " &aditolak.");
        Player online = Bukkit.getPlayer(applicant);
        if (online != null) msg(online, " &cApplication ke &f" + parties.display(party) + " &cditolak.");
    }

    private boolean addMember(String party, UUID uuid, String name) {
        if (!parties.exists(party) || parties.inParty(uuid) || parties.memberCount(party) >= parties.memberLimit(party)) return false;
        String base = "parties." + party + ".members." + uuid;
        db.parties.set(base + ".name", name == null ? uuid.toString() : name);
        db.parties.set(base + ".role", PartyService.Role.MEMBER.name());
        db.parties.set(base + ".joined-at", System.currentTimeMillis());
        db.parties.set("players." + uuid + ".party", party);
        plugin.saveDataSoon();
        return true;
    }

    private UUID findApplicant(String party, String input) {
        ConfigurationSection sec = db.interactions.getConfigurationSection("applications." + party);
        if (sec == null) return null;
        for (String uuid : sec.getKeys(false)) {
            String name = sec.getString(uuid + ".name", "");
            if (name.equalsIgnoreCase(input) || uuid.equalsIgnoreCase(input)) {
                try { return UUID.fromString(uuid); } catch (Exception ignored) { return null; }
            }
        }
        return null;
    }

    private int activeApplicationCount(UUID uuid) {
        ConfigurationSection root = db.interactions.getConfigurationSection("applications");
        if (root == null) return 0;
        int count = 0;
        long now = System.currentTimeMillis();
        for (String party : root.getKeys(false)) {
            String base = party + "." + uuid;
            if (root.contains(base) && root.getLong(base + ".expires-at", 0L) >= now) count++;
        }
        return count;
    }

    private void removeAllApplications(UUID uuid) {
        ConfigurationSection root = db.interactions.getConfigurationSection("applications");
        if (root == null) return;
        for (String party : new ArrayList<>(root.getKeys(false))) {
            db.interactions.set("applications." + party + "." + uuid, null);
        }
    }

    private List<String> pendingApplicantNames(CommandSender sender) {
        if (!(sender instanceof Player player)) return List.of();
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) return List.of();
        ConfigurationSection sec = db.interactions.getConfigurationSection("applications." + party);
        if (sec == null) return List.of();
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String uuid : sec.getKeys(false)) {
            if (sec.getLong(uuid + ".expires-at", 0L) < now) continue;
            out.add(sec.getString(uuid + ".name", uuid));
        }
        return out;
    }

    private List<String> applicationPartyKeys(CommandSender sender) {
        if (!(sender instanceof Player player)) return List.of();
        ConfigurationSection root = db.interactions.getConfigurationSection("applications");
        if (root == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String party : root.getKeys(false)) if (root.contains(party + "." + player.getUniqueId())) out.add(party);
        return out;
    }

    public List<String> recruitablePartyKeys() {
        List<String> out = new ArrayList<>();
        for (String party : partyKeys()) {
            if (recruitmentMode(party).equals("CLOSED")) continue;
            if (parties.memberCount(party) >= parties.memberLimit(party)) continue;
            out.add(party);
        }
        out.sort(Comparator.comparing(parties::display, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ---------------------------------------------------------------------
    // Periodic expiry / cleanup
    // ---------------------------------------------------------------------

    public void tickMinute() {
        long now = System.currentTimeMillis();
        boolean changed = expireContracts(now);
        changed |= expireDiplomacyRequests(now);
        changed |= expireApplications(now);
        changed |= pruneHistory(now);
        if (changed) plugin.saveDataSoon();
    }

    private boolean expireContracts(long now) {
        ConfigurationSection sec = db.interactions.getConfigurationSection("contracts");
        if (sec == null) return false;
        boolean changed = false;
        for (String id : new ArrayList<>(sec.getKeys(false))) {
            String status = contractStatus(id);
            String source = contractString(id, "source");
            String target = contractString(id, "target");
            if (!parties.exists(source) || !parties.exists(target)) {
                if (status.equals("ACTIVE")) unindexActive(id, target);
                if (status.equals("PENDING") || status.equals("ACTIVE")) {
                    setContractEnded(id, "CANCELLED", now); changed = true;
                }
                continue;
            }
            if (status.equals("PENDING") && db.interactions.getLong("contracts." + id + ".proposal-expires-at", 0L) < now) {
                setContractEnded(id, "EXPIRED", now); changed = true;
            } else if (status.equals("ACTIVE") && db.interactions.getLong("contracts." + id + ".deadline", Long.MAX_VALUE) < now) {
                setContractEnded(id, "FAILED", now);
                unindexActive(id, target);
                int penalty = Math.max(0, plugin.getConfig().getInt("interaction.contracts.trust-on-fail", 2));
                changeTrust(source, target, -penalty);
                parties.broadcastParty(source, parties.prefix() + " &cContract &f" + id + " &cexpired/gagal.");
                parties.broadcastParty(target, parties.prefix() + " &cContract &f" + id + " &cexpired/gagal. &7Trust -" + penalty);
                changed = true;
            }
        }
        return changed;
    }

    private boolean expireDiplomacyRequests(long now) {
        ConfigurationSection root = db.interactions.getConfigurationSection("diplomacy.requests");
        if (root == null) return false;
        boolean changed = false;
        for (String target : new ArrayList<>(root.getKeys(false))) {
            ConfigurationSection sec = root.getConfigurationSection(target);
            if (sec == null) continue;
            for (String source : new ArrayList<>(sec.getKeys(false))) {
                if (sec.getLong(source + ".expires-at", 0L) < now || !parties.exists(target) || !parties.exists(source)) {
                    db.interactions.set("diplomacy.requests." + target + "." + source, null);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean expireApplications(long now) {
        ConfigurationSection root = db.interactions.getConfigurationSection("applications");
        if (root == null) return false;
        boolean changed = false;
        for (String party : new ArrayList<>(root.getKeys(false))) {
            ConfigurationSection sec = root.getConfigurationSection(party);
            if (sec == null) continue;
            for (String uuidText : new ArrayList<>(sec.getKeys(false))) {
                boolean remove = sec.getLong(uuidText + ".expires-at", 0L) < now || !parties.exists(party);
                if (!remove) {
                    try { remove = parties.inParty(UUID.fromString(uuidText)); } catch (Exception e) { remove = true; }
                }
                if (remove) {
                    db.interactions.set("applications." + party + "." + uuidText, null);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean pruneHistory(long now) {
        long days = Math.max(1L, plugin.getConfig().getLong("interaction.contracts.history-retention-days", 30L));
        long cutoff = now - days * 86_400_000L;
        ConfigurationSection sec = db.interactions.getConfigurationSection("contracts");
        if (sec == null) return false;
        boolean changed = false;
        for (String id : new ArrayList<>(sec.getKeys(false))) {
            String status = contractStatus(id);
            if (status.equals("PENDING") || status.equals("ACTIVE")) continue;
            long ended = db.interactions.getLong("contracts." + id + ".ended-at", contractCreatedAt(id));
            if (ended > 0 && ended < cutoff) {
                db.interactions.set("contracts." + id, null);
                changed = true;
            }
        }
        return changed;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String requireParty(Player player) {
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) msg(player, " &cKamu belum punya Party.");
        return party;
    }

    private List<String> partyKeys() {
        ConfigurationSection sec = db.parties.getConfigurationSection("parties");
        if (sec == null) return new ArrayList<>();
        List<String> out = new ArrayList<>(sec.getKeys(false));
        out.sort(Comparator.comparing(parties::display, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private String displaySafe(String party) {
        return party != null && parties.exists(party) ? parties.display(party) : (party == null ? "?" : party);
    }

    private List<String> filter(Collection<String> values, String input) {
        String q = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return values.stream().filter(x -> x != null && x.toLowerCase(Locale.ROOT).startsWith(q)).distinct().toList();
    }

    private void msg(Player player, String text) {
        player.sendMessage(Util.color(parties.prefix() + text));
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
