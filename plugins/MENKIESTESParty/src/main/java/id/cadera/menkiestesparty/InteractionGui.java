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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * v1.3.1 inventory UX for the v1.3 interaction layer.
 *
 * This class intentionally delegates every mutation to InteractionManager via
 * the existing public commands. The GUI therefore cannot bypass rank
 * capabilities, Contract limits/cooldowns, application expiry, Party slot
 * limits or Party War membership locks.
 */
public final class InteractionGui implements Listener, CommandExecutor, TabCompleter {
    private static final String HUB = "Party Interaction";
    private static final String BROWSER = "Party Browser";
    private static final String PARTY_DETAIL = "Party Overview";
    private static final String CONTRACTS = "Party Contracts";
    private static final String CONTRACT_DETAIL = "Contract Detail";
    private static final String CONTRACT_TARGET = "Contract Target";
    private static final String CONTRACT_TYPE = "Contract Type";
    private static final String CONTRACT_GOAL = "Contract Goal";
    private static final String DIPLOMACY = "Party Diplomacy";
    private static final String DIPLOMACY_DETAIL = "Diplomacy Detail";
    private static final String RECRUITMENT = "Party Recruitment";
    private static final String APPLICATIONS = "Party Applications";
    private static final String APPLICATION_DETAIL = "Application Detail";
    private static final String MY_APPLICATIONS = "My Applications";
    private static final String RANKS = "Rank Capabilities";
    private static final String CONFIRM = "Confirm Interaction";
    private static final String TAG = "MENKI_INTERACT:";

    private static final List<String> DIRECT = List.of(
            "partyinteraction", "partycontract", "partydiplomacy", "partybrowse",
            "partyapply", "partyapplications", "partyrecruitment", "partyrankperms"
    );

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final StorageBundle db;
    private final InteractionManager interactions;

    public InteractionGui(MENKIESTESPartyPlugin plugin, PartyService parties,
                          StorageBundle db, InteractionManager interactions) {
        this.plugin = plugin;
        this.parties = parties;
        this.db = db;
        this.interactions = interactions;

        // InteractionManager registers these commands first. We wrap only the
        // zero-argument UX; commands with arguments delegate straight back.
        for (String name : DIRECT) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("gui.interaction.enabled", true);
    }

    public boolean canView(Player p) {
        return p.hasPermission("menkiestesparty.gui.interaction");
    }

    private boolean perm(Player p, String suffix) {
        return canView(p) && p.hasPermission("menkiestesparty.gui.interaction." + suffix);
    }

    private boolean manage(Player p, String suffix, String capability) {
        return perm(p, suffix) && p.hasPermission("menkiestesparty.gui.interaction." + suffix + ".manage")
                && interactions.rankAllows(p.getUniqueId(), capability);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length > 0) {
            return interactions.onCommand(sender, command, label, args);
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "partyinteraction" -> openHub(player);
            case "partycontract" -> openContracts(player, 1);
            case "partydiplomacy" -> openDiplomacy(player, 1);
            case "partybrowse", "partyapply" -> openBrowser(player, 1);
            case "partyapplications" -> openApplications(player, 1);
            case "partyrecruitment" -> openRecruitment(player);
            case "partyrankperms" -> openRanks(player);
            default -> { return interactions.onCommand(sender, command, label, args); }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return interactions.onTabComplete(sender, command, alias, args);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNestedZeroArgument(PlayerCommandPreprocessEvent event) {
        if (!enabled()) return;
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] split = raw.substring(1).trim().split("\\s+");
        if (split.length != 2) return;
        String root = split[0].toLowerCase(Locale.ROOT);
        if (!root.equals("party") && !root.equals("p") && !root.equals("parties")) return;

        switch (split[1].toLowerCase(Locale.ROOT)) {
            case "interaction", "interactions" -> { event.setCancelled(true); openHub(event.getPlayer()); }
            case "contract", "contracts" -> { event.setCancelled(true); openContracts(event.getPlayer(), 1); }
            case "diplomacy", "diplo" -> { event.setCancelled(true); openDiplomacy(event.getPlayer(), 1); }
            case "browse", "apply" -> { event.setCancelled(true); openBrowser(event.getPlayer(), 1); }
            case "applications", "apps" -> { event.setCancelled(true); openApplications(event.getPlayer(), 1); }
            case "recruitment", "recruit" -> { event.setCancelled(true); openRecruitment(event.getPlayer()); }
            case "rankperms", "ranks" -> { event.setCancelled(true); openRanks(event.getPlayer()); }
        }
    }

    /** Adds Interaction to the existing /party menu without modifying the old GUI class. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMainMenuOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!"MENKIESTES Party".equalsIgnoreCase(clean(event.getView().getTitle()))) return;
        if (!enabled() || !canView(player)) return;

        String party = parties.partyOf(player.getUniqueId());
        if (party == null) {
            if (interactions.moduleEnabled("applications") && perm(player, "browser")) {
                event.getInventory().setItem(20, tagged(Material.SPYGLASS, "&bParty Browser", "browser:1",
                        "&7Cari Party yang sedang recruitment.", "&aKlik untuk buka."));
            }
        } else {
            event.getInventory().setItem(20, tagged(Material.ENDER_EYE, "&6Party Interaction", "hub",
                    "&7Contracts, Diplomacy, Recruitment dan Applications.", "&aKlik untuk buka."));
        }
    }

    public void openHub(Player player) {
        if (!root(player)) return;
        String party = parties.partyOf(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + HUB));

        if (party == null) {
            inv.setItem(11, section(player, "browser", interactions.moduleEnabled("applications"), Material.SPYGLASS,
                    "&bParty Browser", "browser:1", "&7Cari Party OPEN/APPLICATION."));
            inv.setItem(15, section(player, "applications", interactions.moduleEnabled("applications"), Material.WRITABLE_BOOK,
                    "&eMy Applications", "myapps:1", "&7Lihat application yang masih aktif."));
            inv.setItem(22, Util.item(Material.PAPER, "&7Belum punya Party",
                    "&7Gunakan Browser untuk mencari Party.", "&7Atau &f/party create <nama>"));
        } else {
            inv.setItem(4, Util.item(Material.PLAYER_HEAD, "&b" + parties.display(party),
                    "&7Role: &f" + parties.role(player.getUniqueId()),
                    "&7Member: &f" + parties.memberCount(party) + "/" + parties.memberLimit(party)));
            inv.setItem(10, section(player, "browser", interactions.moduleEnabled("applications"), Material.SPYGLASS,
                    "&bParty Browser", "browser:1", "&7Lihat Party lain."));
            inv.setItem(12, section(player, "contracts", interactions.moduleEnabled("contracts"), Material.WRITABLE_BOOK,
                    "&6Contracts", "contracts:1", "&7Aktif: &f" + interactions.activeContractCount(party),
                    "&7Selesai: &a" + interactions.completedContractCount(party)));
            inv.setItem(14, section(player, "diplomacy", interactions.moduleEnabled("diplomacy"), Material.TOTEM_OF_UNDYING,
                    "&dDiplomacy", "diplomacy:1", "&7Relation & Trust antar Party."));
            inv.setItem(16, section(player, "applications", interactions.moduleEnabled("applications"), Material.BOOK,
                    "&eApplications", "applications:1", "&7Incoming: &f" + interactions.pendingApplicationCount(party)));
            inv.setItem(20, section(player, "recruitment", interactions.moduleEnabled("applications"), Material.OAK_SIGN,
                    "&aRecruitment", "recruitment", "&7Mode: &f" + interactions.recruitmentMode(party)));
            inv.setItem(24, tagged(Material.NAME_TAG, "&dRank Capabilities", "ranks",
                    "&7Lihat capability role-mu."));
        }
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "back-root", "&7Kembali ke menu Party."));
        player.openInventory(inv);
    }

    public void openBrowser(Player player, int page) {
        if (!root(player)) return;
        if (!interactions.moduleEnabled("applications")) { disabled(player, "Party Browser"); return; }
        if (!perm(player, "browser")) { denied(player, "Party Browser"); return; }

        String own = parties.partyOf(player.getUniqueId());
        List<String> keys = partyKeys();
        if (own != null) keys.removeIf(x -> x.equalsIgnoreCase(own));
        else keys.removeIf(x -> interactions.recruitmentMode(x).equals("CLOSED"));
        keys.sort((a, b) -> {
            int m = Integer.compare(recruitOrder(interactions.recruitmentMode(a)), recruitOrder(interactions.recruitmentMode(b)));
            return m != 0 ? m : parties.display(a).compareToIgnoreCase(parties.display(b));
        });

        Page<String> slice = page(keys, page, 45);
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + BROWSER));
        int slot = 0;
        for (String target : slice.items()) {
            String mode = interactions.recruitmentMode(target);
            List<String> lore = new ArrayList<>();
            lore.add("&7Recruitment: " + recruitColor(mode) + mode);
            lore.add("&7Level: &f" + parties.level(target));
            lore.add("&7Member: &f" + parties.memberCount(target) + "/" + parties.memberLimit(target));
            if (own != null) {
                lore.add("&7Relation: " + relationColor(interactions.relation(own, target)) + interactions.relation(own, target));
                lore.add("&7Trust: &d" + interactions.trust(own, target));
            } else if (hasApplication(player.getUniqueId(), target)) {
                lore.add("&eApplication aktif.");
            }
            lore.add("&aKlik untuk detail.");
            inv.setItem(slot++, tagged(partyMaterial(mode), "&b" + parties.display(target),
                    "party:" + target + ":" + slice.page(), lore.toArray(String[]::new)));
        }
        if (slice.items().isEmpty()) inv.setItem(22, Util.item(Material.BARRIER, "&cTidak ada Party tersedia"));
        nav(inv, "browser", slice);
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Interaction Hub."));
        player.openInventory(inv);
    }

    public void openParty(Player player, String target, int browserPage) {
        if (!root(player)) return;
        if (!parties.exists(target)) { msg(player, " &cParty sudah tidak tersedia."); openBrowser(player, browserPage); return; }
        String own = parties.partyOf(player.getUniqueId());
        String mode = interactions.recruitmentMode(target);
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + PARTY_DETAIL));
        List<String> info = new ArrayList<>(List.of(
                "&7Level: &f" + parties.level(target),
                "&7Member: &f" + parties.memberCount(target) + "/" + parties.memberLimit(target),
                "&7Recruitment: " + recruitColor(mode) + mode));
        if (own != null) {
            info.add("&7Relation: " + relationColor(interactions.relation(own, target)) + interactions.relation(own, target));
            info.add("&7Trust: &d" + interactions.trust(own, target));
        }
        inv.setItem(4, Util.item(Material.PLAYER_HEAD, "&b&l" + parties.display(target), info.toArray(String[]::new)));

        if (own == null) {
            boolean applied = hasApplication(player.getUniqueId(), target);
            if (applied) {
                inv.setItem(12, tagged(Material.RED_DYE, "&cCancel Application", "appcancel-confirm:" + target + ":" + browserPage,
                        "&7Batalkan application aktif."));
            } else if (!mode.equals("CLOSED")) {
                inv.setItem(12, tagged(mode.equals("OPEN") ? Material.LIME_DYE : Material.WRITABLE_BOOK,
                        mode.equals("OPEN") ? "&aJoin Party" : "&eSend Application",
                        "apply-confirm:" + target + ":" + browserPage,
                        mode.equals("OPEN") ? "&7Langsung join jika slot tersedia." : "&7GUI mengirim tanpa catatan.",
                        mode.equals("APPLICATION") ? "&8Untuk pesan: /party apply <party> <pesan>" : ""));
            }
        } else {
            if (interactions.moduleEnabled("diplomacy") && perm(player, "diplomacy")) {
                inv.setItem(11, tagged(Material.TOTEM_OF_UNDYING, "&dDiplomacy", "diplo-party:" + target + ":" + browserPage,
                        "&7Kelola relation dan Trust."));
            }
            if (interactions.moduleEnabled("contracts") && manage(player, "contracts", "contracts.create")) {
                inv.setItem(15, tagged(Material.WRITABLE_BOOK, "&6Create Contract", "contract-type:" + target + ":" + browserPage,
                        "&7Buat Contract untuk Party ini."));
            }
        }
        inv.setItem(22, tagged(Material.ARROW, "&eKembali", "browser:" + browserPage, "&7Kembali ke Party Browser."));
        player.openInventory(inv);
    }

    public void openContracts(Player player, int requestedPage) {
        String party = requireParty(player);
        if (party == null || !root(player)) return;
        if (!interactions.moduleEnabled("contracts")) { disabled(player, "Contracts"); return; }
        if (!perm(player, "contracts")) { denied(player, "Contracts"); return; }

        List<String> ids = relevantContracts(party);
        ids.sort((a, b) -> {
            int s = Integer.compare(statusOrder(contractStatus(a)), statusOrder(contractStatus(b)));
            return s != 0 ? s : Long.compare(contractCreated(b), contractCreated(a));
        });
        Page<String> slice = page(ids, requestedPage, 45);
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + CONTRACTS));
        int slot = 0;
        for (String id : slice.items()) {
            String status = contractStatus(id), source = cstr(id, "source"), target = cstr(id, "target"), type = cstr(id, "type");
            int goal = cint(id, "goal"), progress = cint(id, "progress");
            inv.setItem(slot++, tagged(contractMaterial(status, type), statusColor(status) + id,
                    "contract:" + id + ":" + slice.page(),
                    "&7Status: " + statusColor(status) + status,
                    "&7" + displaySafe(source) + " &8→ &f" + displaySafe(target),
                    "&7Type: &f" + type,
                    "&7Progress: &f" + Math.min(progress, goal) + "/" + goal,
                    bar(progress, goal),
                    expiry(id, status),
                    "&aKlik untuk detail."));
        }
        if (slice.items().isEmpty()) inv.setItem(22, Util.item(Material.PAPER, "&7Belum ada Contract"));
        if (manage(player, "contracts", "contracts.create")) {
            inv.setItem(48, tagged(Material.LIME_DYE, "&aCreate Contract", "contract-target:1", "&7Mulai wizard Contract."));
        }
        nav(inv, "contracts", slice);
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Interaction Hub."));
        player.openInventory(inv);
    }

    public void openContract(Player player, String id, int listPage) {
        String party = requireParty(player);
        if (party == null || !root(player)) return;
        id = id.toUpperCase(Locale.ROOT);
        if (!contractExists(id) || !contractRelates(id, party)) { msg(player, " &cContract tidak ditemukan."); openContracts(player, listPage); return; }
        String status = contractStatus(id), source = cstr(id, "source"), target = cstr(id, "target"), type = cstr(id, "type");
        int goal = cint(id, "goal"), progress = cint(id, "progress");
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + CONTRACT_DETAIL));
        inv.setItem(4, Util.item(contractMaterial(status, type), statusColor(status) + "&l" + id,
                "&7Status: " + statusColor(status) + status,
                "&7Issuer: &f" + displaySafe(source),
                "&7Assignee: &f" + displaySafe(target),
                "&7Type: &b" + type,
                "&7Progress: &f" + Math.min(progress, goal) + "/" + goal,
                bar(progress, goal), expiry(id, status)));

        boolean contractManage = perm(player, "contracts") && player.hasPermission("menkiestesparty.gui.interaction.contracts.manage");
        if (status.equals("PENDING") && party.equals(target) && contractManage && interactions.rankAllows(player.getUniqueId(), "contracts.respond")) {
            inv.setItem(11, tagged(Material.LIME_DYE, "&aAccept", "contract-confirm:accept:" + id + ":" + listPage, "&7Aktifkan Contract."));
            inv.setItem(15, tagged(Material.RED_DYE, "&cDeny", "contract-confirm:deny:" + id + ":" + listPage, "&7Tolak proposal."));
        } else if (status.equals("PENDING") && party.equals(source) && contractManage && interactions.rankAllows(player.getUniqueId(), "contracts.create")) {
            inv.setItem(13, tagged(Material.RED_DYE, "&cCancel Proposal", "contract-confirm:cancel:" + id + ":" + listPage, "&7Batalkan proposal."));
        } else if (status.equals("ACTIVE") && party.equals(target) && contractManage && interactions.rankAllows(player.getUniqueId(), "contracts.respond")) {
            inv.setItem(13, tagged(Material.BARRIER, "&cAbandon Contract", "contract-confirm:abandon:" + id + ":" + listPage,
                    "&cContract gagal dan Trust dapat berkurang."));
        }
        inv.setItem(22, tagged(Material.ARROW, "&eKembali", "contracts:" + listPage, "&7Kembali ke daftar Contract."));
        player.openInventory(inv);
    }

    public void openContractTargets(Player player, int requestedPage) {
        String own = requireParty(player);
        if (own == null || !root(player)) return;
        if (!manage(player, "contracts", "contracts.create")) { denied(player, "Contract Management"); return; }
        List<String> keys = partyKeys();
        keys.removeIf(x -> x.equalsIgnoreCase(own));
        if (!plugin.getConfig().getBoolean("interaction.contracts.allow-rival-parties", false)) {
            keys.removeIf(x -> interactions.relation(own, x).equals("RIVAL"));
        }
        keys.sort(Comparator.comparing(parties::display, String.CASE_INSENSITIVE_ORDER));
        Page<String> slice = page(keys, requestedPage, 45);
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + CONTRACT_TARGET));
        int slot = 0;
        for (String target : slice.items()) {
            inv.setItem(slot++, tagged(Material.PLAYER_HEAD, "&b" + parties.display(target),
                    "contract-type:" + target + ":" + slice.page(),
                    "&7Relation: &f" + interactions.relation(own, target),
                    "&7Trust: &d" + interactions.trust(own, target),
                    "&aKlik untuk pilih."));
        }
        nav(inv, "contract-target", slice);
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "contracts:1", "&7Kembali ke Contracts."));
        player.openInventory(inv);
    }

    public void openContractType(Player player, String target, int targetPage) {
        if (requireParty(player) == null || !root(player)) return;
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + CONTRACT_TYPE));
        inv.setItem(4, Util.item(Material.PLAYER_HEAD, "&b" + displaySafe(target), "&7Pilih objective Contract."));
        inv.setItem(10, tagged(Material.DIAMOND_PICKAXE, "&bMining", "contract-goal:" + target + ":mining:" + targetPage, "&7Progress dari mining."));
        inv.setItem(13, tagged(Material.IRON_SWORD, "&cHunter", "contract-goal:" + target + ":hunter:" + targetPage, "&7Progress dari membunuh mob."));
        inv.setItem(16, tagged(Material.WHEAT, "&aFarmer", "contract-goal:" + target + ":farmer:" + targetPage, "&7Progress dari crop matang."));
        inv.setItem(22, tagged(Material.ARROW, "&eKembali", "contract-target:" + targetPage, "&7Kembali pilih Party."));
        player.openInventory(inv);
    }

    public void openContractGoal(Player player, String target, String type, int targetPage) {
        if (requireParty(player) == null || !root(player)) return;
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + CONTRACT_GOAL));
        inv.setItem(4, Util.item(typeMaterial(type), "&6" + displaySafe(target), "&7Type: &f" + type, "&7Pilih target goal."));
        List<Integer> goals = goals();
        int[] slots = {10, 11, 12, 14, 15, 16, 20, 21, 23, 24};
        for (int i = 0; i < goals.size() && i < slots.length; i++) {
            int goal = goals.get(i);
            inv.setItem(slots[i], tagged(Material.PAPER, "&eGoal " + goal,
                    "contract-create:" + target + ":" + type + ":" + goal + ":" + targetPage,
                    "&aKlik untuk pilih."));
        }
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "contract-type:" + target + ":" + targetPage, "&7Kembali pilih type."));
        player.openInventory(inv);
    }

    public void openDiplomacy(Player player, int requestedPage) {
        String own = requireParty(player);
        if (own == null || !root(player)) return;
        if (!interactions.moduleEnabled("diplomacy")) { disabled(player, "Diplomacy"); return; }
        if (!perm(player, "diplomacy")) { denied(player, "Diplomacy"); return; }
        List<String> keys = partyKeys();
        keys.removeIf(x -> x.equalsIgnoreCase(own));
        keys.sort((a, b) -> {
            int r = Integer.compare(relationOrder(interactions.relation(own, a)), relationOrder(interactions.relation(own, b)));
            return r != 0 ? r : parties.display(a).compareToIgnoreCase(parties.display(b));
        });
        Page<String> slice = page(keys, requestedPage, 45);
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + DIPLOMACY));
        int slot = 0;
        for (String target : slice.items()) {
            String relation = interactions.relation(own, target);
            List<String> lore = new ArrayList<>();
            lore.add("&7Relation: " + relationColor(relation) + relation);
            lore.add("&7Trust: &d" + interactions.trust(own, target));
            if (incomingAlliance(own, target)) lore.add("&eIncoming ALLY request.");
            if (outgoingAlliance(own, target)) lore.add("&6Outgoing ALLY request.");
            lore.add("&aKlik untuk detail.");
            inv.setItem(slot++, tagged(diplomacyMaterial(relation), "&b" + parties.display(target),
                    "diplo:" + target + ":" + slice.page(), lore.toArray(String[]::new)));
        }
        nav(inv, "diplomacy", slice);
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Interaction Hub."));
        player.openInventory(inv);
    }

    public void openDiplomacyParty(Player player, String target, int listPage) {
        String own = requireParty(player);
        if (own == null || !root(player)) return;
        if (!parties.exists(target)) { openDiplomacy(player, listPage); return; }
        String relation = interactions.relation(own, target);
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + DIPLOMACY_DETAIL));
        inv.setItem(4, Util.item(diplomacyMaterial(relation), "&d&l" + parties.display(target),
                "&7Relation: " + relationColor(relation) + relation,
                "&7Trust: &d" + interactions.trust(own, target),
                incomingAlliance(own, target) ? "&eIncoming ALLY request." : "&7Incoming request: &8-",
                outgoingAlliance(own, target) ? "&6Outgoing ALLY request." : "&7Outgoing request: &8-"));
        boolean canManage = manage(player, "diplomacy", "diplomacy.manage");
        if (canManage && incomingAlliance(own, target)) {
            inv.setItem(10, tagged(Material.LIME_DYE, "&aAccept Alliance", "diplo-confirm:accept:" + target + ":" + listPage, "&7Terima request."));
            inv.setItem(12, tagged(Material.RED_DYE, "&cDeny Alliance", "diplo-confirm:deny:" + target + ":" + listPage, "&7Tolak request."));
        }
        if (canManage && !relation.equals("ALLY") && !outgoingAlliance(own, target)) {
            inv.setItem(14, tagged(Material.EMERALD, "&aRequest Alliance", "diplo-confirm:ally:" + target + ":" + listPage, "&7Kirim request ALLY."));
        }
        if (canManage && !relation.equals("RIVAL")) {
            inv.setItem(16, tagged(Material.IRON_SWORD, "&cDeclare Rival", "diplo-confirm:rival:" + target + ":" + listPage, "&7Ubah relation langsung ke RIVAL."));
        }
        if (canManage && !relation.equals("NEUTRAL")) {
            inv.setItem(18, tagged(Material.GRAY_DYE, "&7Set Neutral", "diplo-confirm:neutral:" + target + ":" + listPage, "&7Reset relation ke NEUTRAL."));
        }
        inv.setItem(22, tagged(Material.ARROW, "&eKembali", "diplomacy:" + listPage, "&7Kembali ke Diplomacy."));
        player.openInventory(inv);
    }

    public void openRecruitment(Player player) {
        String party = requireParty(player);
        if (party == null || !root(player)) return;
        if (!interactions.moduleEnabled("applications")) { disabled(player, "Recruitment"); return; }
        if (!perm(player, "recruitment")) { denied(player, "Recruitment"); return; }
        String current = interactions.recruitmentMode(party);
        boolean canManage = manage(player, "recruitment", "recruitment.manage");
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + RECRUITMENT));
        inv.setItem(4, Util.item(Material.OAK_SIGN, "&a&lRECRUITMENT",
                "&7Mode saat ini: " + recruitColor(current) + current,
                canManage ? "&aPilih mode di bawah." : "&7Role/permission-mu view-only."));
        inv.setItem(10, recruitmentItem("OPEN", current, Material.LIME_DYE, canManage,
                "&7Player /party apply langsung join jika slot tersedia."));
        inv.setItem(13, recruitmentItem("APPLICATION", current, Material.WRITABLE_BOOK, canManage,
                "&7Player mengirim application untuk direview."));
        inv.setItem(16, recruitmentItem("CLOSED", current, Material.RED_DYE, canManage,
                "&7Tidak menerima join/application baru."));
        inv.setItem(22, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Interaction Hub."));
        player.openInventory(inv);
    }

    public void openApplications(Player player, int requestedPage) {
        if (!root(player)) return;
        if (!interactions.moduleEnabled("applications")) { disabled(player, "Applications"); return; }
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) { openMyApplications(player, requestedPage); return; }
        if (!perm(player, "applications") || !interactions.rankAllows(player.getUniqueId(), "applications.manage")) {
            denied(player, "Applications"); return;
        }
        ConfigurationSection sec = db.interactions.getConfigurationSection("applications." + party);
        List<UUID> applicants = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (sec != null) for (String raw : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(raw);
                if (sec.getLong(raw + ".expires-at", 0L) >= now) applicants.add(uuid);
            } catch (Exception ignored) { }
        }
        applicants.sort((a, b) -> Long.compare(appCreated(party, b), appCreated(party, a)));
        Page<UUID> slice = page(applicants, requestedPage, 45);
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + APPLICATIONS));
        int slot = 0;
        for (UUID uuid : slice.items()) {
            String base = "applications." + party + "." + uuid;
            String name = db.interactions.getString(base + ".name", uuid.toString().substring(0, 8));
            String note = db.interactions.getString(base + ".note", "");
            inv.setItem(slot++, tagged(Material.PLAYER_HEAD, "&e" + name, "app:" + uuid + ":" + slice.page(),
                    note == null || note.isBlank() ? "&7Pesan: &8-" : "&7Pesan: &f" + note,
                    "&7Expires: &f" + remaining(db.interactions.getLong(base + ".expires-at", 0L)),
                    "&aKlik untuk review."));
        }
        if (slice.items().isEmpty()) inv.setItem(22, Util.item(Material.PAPER, "&7Tidak ada application aktif"));
        nav(inv, "applications", slice);
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Interaction Hub."));
        player.openInventory(inv);
    }

    public void openApplication(Player player, UUID applicant, int listPage) {
        String party = requireParty(player);
        if (party == null || !root(player)) return;
        if (!perm(player, "applications") || !interactions.rankAllows(player.getUniqueId(), "applications.manage")) {
            denied(player, "Applications"); return;
        }
        String base = "applications." + party + "." + applicant;
        if (!db.interactions.contains(base)) { openApplications(player, listPage); return; }
        String name = db.interactions.getString(base + ".name", applicant.toString().substring(0, 8));
        String note = db.interactions.getString(base + ".note", "");
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + APPLICATION_DETAIL));
        inv.setItem(4, Util.item(Material.PLAYER_HEAD, "&e&l" + name,
                note == null || note.isBlank() ? "&7Pesan: &8-" : "&7Pesan: &f" + note,
                "&7Expires: &f" + remaining(db.interactions.getLong(base + ".expires-at", 0L))));
        if (manage(player, "applications", "applications.manage")) {
            inv.setItem(11, tagged(Material.LIME_DYE, "&aAccept", "app-confirm:accept:" + applicant + ":" + listPage, "&7Terima sebagai member."));
            inv.setItem(15, tagged(Material.RED_DYE, "&cDeny", "app-confirm:deny:" + applicant + ":" + listPage, "&7Tolak application."));
        }
        inv.setItem(22, tagged(Material.ARROW, "&eKembali", "applications:" + listPage, "&7Kembali ke inbox."));
        player.openInventory(inv);
    }

    public void openMyApplications(Player player, int requestedPage) {
        if (!root(player)) return;
        if (!perm(player, "applications")) { denied(player, "Applications"); return; }
        List<String> targets = activeApplicationParties(player.getUniqueId());
        targets.sort(Comparator.comparing(this::displaySafe, String.CASE_INSENSITIVE_ORDER));
        Page<String> slice = page(targets, requestedPage, 45);
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + MY_APPLICATIONS));
        int slot = 0;
        for (String target : slice.items()) {
            String base = "applications." + target + "." + player.getUniqueId();
            String note = db.interactions.getString(base + ".note", "");
            inv.setItem(slot++, tagged(Material.WRITABLE_BOOK, "&e" + displaySafe(target), "party:" + target + ":1",
                    note == null || note.isBlank() ? "&7Pesan: &8-" : "&7Pesan: &f" + note,
                    "&7Expires: &f" + remaining(db.interactions.getLong(base + ".expires-at", 0L)),
                    "&aKlik untuk Party detail."));
        }
        if (slice.items().isEmpty()) inv.setItem(22, Util.item(Material.PAPER, "&7Tidak ada application aktif"));
        nav(inv, "myapps", slice);
        if (perm(player, "browser")) inv.setItem(48, tagged(Material.SPYGLASS, "&bParty Browser", "browser:1", "&7Cari Party."));
        inv.setItem(49, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Interaction Hub."));
        player.openInventory(inv);
    }

    public void openRanks(Player player) {
        String party = requireParty(player);
        if (party == null || !root(player)) return;
        PartyService.Role role = parties.role(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + RANKS));
        inv.setItem(4, Util.item(Material.NAME_TAG, "&d&l" + role,
                plugin.getConfig().getBoolean("rank-permissions.enabled", true)
                        ? "&7Configurable capability mode: &aON" : "&7Legacy Owner/Officer mode: &eON"));
        String[] caps = {"contracts.create", "contracts.respond", "diplomacy.manage", "recruitment.manage", "applications.manage"};
        int[] slots = {10, 11, 13, 15, 16};
        for (int i = 0; i < caps.length; i++) {
            boolean allowed = interactions.rankAllows(player.getUniqueId(), caps[i]);
            inv.setItem(slots[i], Util.item(allowed ? Material.LIME_DYE : Material.RED_DYE,
                    (allowed ? "&a" : "&c") + caps[i],
                    allowed ? "&7Role-mu memiliki capability ini." : "&7Role-mu tidak memiliki capability ini."));
        }
        List<String> grants = interactions.grantsFor(role);
        inv.setItem(22, Util.item(Material.BOOK, "&fRaw Grants",
                grants.isEmpty() ? "&8Tidak ada explicit grant." : "&7" + String.join(", ", grants)));
        inv.setItem(26, tagged(Material.ARROW, "&eKembali", "hub", "&7Kembali ke Interaction Hub."));
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        String title = clean(event.getView().getTitle());
        if (title.equalsIgnoreCase("MENKIESTES Party") && event.getRawSlot() == 20) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                if (parties.inParty(p.getUniqueId())) openHub(p); else openBrowser(p, 1);
            }
            return;
        }
        if (!ourTitle(title)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        String action = action(event.getCurrentItem());
        if (action != null) dispatch(player, action);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (ourTitle(clean(event.getView().getTitle()))) event.setCancelled(true);
    }

    private void dispatch(Player p, String action) {
        try {
            if (action.equals("hub")) { openHub(p); return; }
            if (action.equals("back-root")) { parties.openMenu(p); return; }
            if (action.equals("recruitment")) { openRecruitment(p); return; }
            if (action.equals("ranks")) { openRanks(p); return; }
            String[] a = action.split(":");
            switch (a[0]) {
                case "browser" -> openBrowser(p, integer(a, 1, 1));
                case "party" -> openParty(p, a[1], integer(a, 2, 1));
                case "contracts" -> openContracts(p, integer(a, 1, 1));
                case "contract" -> openContract(p, a[1], integer(a, 2, 1));
                case "contract-target" -> openContractTargets(p, integer(a, 1, 1));
                case "contract-type" -> openContractType(p, a[1], integer(a, 2, 1));
                case "contract-goal" -> openContractGoal(p, a[1], a[2], integer(a, 3, 1));
                case "contract-create" -> confirmOrRun(p, "contract-create", "&6Create Contract?",
                        "&7" + displaySafe(a[1]) + " &8| &f" + a[2] + " " + a[3],
                        "do-contract-create:" + a[1] + ":" + a[2] + ":" + a[3],
                        "contract-goal:" + a[1] + ":" + a[2] + ":" + integer(a, 4, 1));
                case "do-contract-create" -> {
                    command(p, "partycontract create " + a[1] + " " + a[2] + " " + integer(a, 3, 10));
                    openContracts(p, 1);
                }
                case "contract-confirm" -> {
                    String key = a[1].equals("abandon") ? "contract-abandon" : a[1].equals("cancel") ? "contract-cancel" : "contract-response";
                    confirmOrRun(p, key, contractConfirmTitle(a[1]), contractConfirmLore(a[1]),
                            "do-contract:" + a[1] + ":" + a[2] + ":" + integer(a, 3, 1),
                            "contract:" + a[2] + ":" + integer(a, 3, 1));
                }
                case "do-contract" -> {
                    command(p, "partycontract " + a[1] + " " + a[2]);
                    openContracts(p, integer(a, 3, 1));
                }
                case "diplomacy" -> openDiplomacy(p, integer(a, 1, 1));
                case "diplo" -> openDiplomacyParty(p, a[1], integer(a, 2, 1));
                case "diplo-party" -> openDiplomacyParty(p, a[1], integer(a, 2, 1));
                case "diplo-confirm" -> confirmOrRun(p, "diplomacy-change", diplomacyConfirmTitle(a[1]),
                        "&7Party: &f" + displaySafe(a[2]),
                        "do-diplo:" + a[1] + ":" + a[2] + ":" + integer(a, 3, 1),
                        "diplo:" + a[2] + ":" + integer(a, 3, 1));
                case "do-diplo" -> {
                    String op = a[1].equals("ally") ? "request " + a[2] + " ally" : a[1] + " " + a[2];
                    command(p, "partydiplomacy " + op);
                    openDiplomacyParty(p, a[2], integer(a, 3, 1));
                }
                case "recruit" -> {
                    command(p, "partyrecruitment " + a[1]);
                    openRecruitment(p);
                }
                case "applications" -> openApplications(p, integer(a, 1, 1));
                case "app" -> openApplication(p, UUID.fromString(a[1]), integer(a, 2, 1));
                case "app-confirm" -> confirmOrRun(p, "application-decisions",
                        a[1].equals("accept") ? "&aAccept Application?" : "&cDeny Application?",
                        "&7Konfirmasi keputusan application.",
                        "do-app:" + a[1] + ":" + a[2] + ":" + integer(a, 3, 1),
                        "app:" + a[2] + ":" + integer(a, 3, 1));
                case "do-app" -> {
                    command(p, "partyapplications " + a[1] + " " + a[2]);
                    openApplications(p, integer(a, 3, 1));
                }
                case "myapps" -> openMyApplications(p, integer(a, 1, 1));
                case "apply-confirm" -> confirmOrRun(p, "application-submit", "&eApply / Join Party?",
                        "&7Party: &f" + displaySafe(a[1]), "do-apply:" + a[1] + ":" + integer(a, 2, 1),
                        "party:" + a[1] + ":" + integer(a, 2, 1));
                case "do-apply" -> {
                    command(p, "partyapply " + a[1]);
                    if (parties.inParty(p.getUniqueId())) openHub(p); else openBrowser(p, integer(a, 2, 1));
                }
                case "appcancel-confirm" -> confirmOrRun(p, "application-cancel", "&cCancel Application?",
                        "&7Party: &f" + displaySafe(a[1]), "do-appcancel:" + a[1] + ":" + integer(a, 2, 1),
                        "party:" + a[1] + ":" + integer(a, 2, 1));
                case "do-appcancel" -> {
                    command(p, "partyapply cancel " + a[1]);
                    openBrowser(p, integer(a, 2, 1));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Interaction GUI action gagal: " + action + " -> " + ex.getMessage());
            p.closeInventory();
            msg(p, " &cGUI action tidak valid. Buka ulang /party interaction.");
        }
    }

    private void confirmOrRun(Player p, String configKey, String title, String detail, String yes, String no) {
        if (!plugin.getConfig().getBoolean("gui.interaction.confirmations." + configKey, true)) {
            dispatch(p, yes);
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + CONFIRM));
        inv.setItem(4, Util.item(Material.PAPER, title, detail));
        inv.setItem(11, tagged(Material.LIME_DYE, "&a&lCONFIRM", yes, "&7Klik untuk lanjut."));
        inv.setItem(15, tagged(Material.RED_DYE, "&c&lCANCEL", no, "&7Kembali tanpa perubahan."));
        p.openInventory(inv);
    }

    private void command(Player p, String command) {
        Bukkit.dispatchCommand(p, command);
    }

    private List<String> partyKeys() {
        ConfigurationSection sec = db.parties.getConfigurationSection("parties");
        return sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
    }

    private List<String> relevantContracts(String party) {
        List<String> out = new ArrayList<>();
        ConfigurationSection sec = db.interactions.getConfigurationSection("contracts");
        if (sec != null) for (String id : sec.getKeys(false)) if (contractRelates(id, party)) out.add(id);
        return out;
    }

    private boolean contractRelates(String id, String party) {
        return party.equals(cstr(id, "source")) || party.equals(cstr(id, "target"));
    }

    private boolean contractExists(String id) { return db.interactions.contains("contracts." + id + ".status"); }
    private String contractStatus(String id) { return db.interactions.getString("contracts." + id + ".status", "UNKNOWN").toUpperCase(Locale.ROOT); }
    private String cstr(String id, String key) { return db.interactions.getString("contracts." + id + "." + key, ""); }
    private int cint(String id, String key) { return db.interactions.getInt("contracts." + id + "." + key, 0); }
    private long contractCreated(String id) { return db.interactions.getLong("contracts." + id + ".created-at", 0L); }

    private boolean incomingAlliance(String own, String source) {
        String path = "diplomacy.requests." + own + "." + source + ".expires-at";
        return db.interactions.getLong(path, 0L) >= System.currentTimeMillis();
    }

    private boolean outgoingAlliance(String own, String target) {
        String path = "diplomacy.requests." + target + "." + own + ".expires-at";
        return db.interactions.getLong(path, 0L) >= System.currentTimeMillis();
    }

    private boolean hasApplication(UUID player, String target) {
        return db.interactions.getLong("applications." + target + "." + player + ".expires-at", 0L) >= System.currentTimeMillis();
    }

    private List<String> activeApplicationParties(UUID player) {
        List<String> out = new ArrayList<>();
        ConfigurationSection root = db.interactions.getConfigurationSection("applications");
        if (root != null) for (String party : root.getKeys(false)) if (hasApplication(player, party)) out.add(party);
        return out;
    }

    private long appCreated(String party, UUID uuid) {
        return db.interactions.getLong("applications." + party + "." + uuid + ".created-at", 0L);
    }

    private String expiry(String id, String status) {
        long until = status.equals("PENDING")
                ? db.interactions.getLong("contracts." + id + ".proposal-expires-at", 0L)
                : status.equals("ACTIVE") ? db.interactions.getLong("contracts." + id + ".deadline", 0L) : 0L;
        return until <= 0 ? "&7Expiry: &8-" : "&7Sisa waktu: &f" + remaining(until);
    }

    private String remaining(long until) {
        long ms = until - System.currentTimeMillis();
        if (ms <= 0) return "expired";
        long minutes = Math.max(1, (ms + 59_999L) / 60_000L);
        long days = minutes / 1440, hours = (minutes % 1440) / 60, mins = minutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    private List<Integer> goals() {
        int min = Math.max(1, plugin.getConfig().getInt("interaction.contracts.min-goal", 10));
        int max = Math.max(min, plugin.getConfig().getInt("interaction.contracts.max-goal", 10000));
        List<Integer> configured = plugin.getConfig().getIntegerList("gui.interaction.contract-goals");
        if (configured.isEmpty()) configured = List.of(50, 100, 250, 500, 1000);
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        for (Integer value : configured) if (value != null) values.add(Math.max(min, Math.min(max, value)));
        if (values.isEmpty()) values.add(min);
        return new ArrayList<>(values);
    }

    private ItemStack recruitmentItem(String mode, String current, Material material, boolean manageable, String description) {
        List<String> lore = new ArrayList<>();
        lore.add(description);
        if (mode.equals(current)) lore.add("&aMode aktif saat ini.");
        else lore.add(manageable ? "&aKlik untuk ubah." : "&8View-only.");
        return manageable && !mode.equals(current)
                ? tagged(material, recruitColor(mode) + mode, "recruit:" + mode.toLowerCase(Locale.ROOT), lore.toArray(String[]::new))
                : Util.item(material, recruitColor(mode) + mode, lore.toArray(String[]::new));
    }

    private ItemStack section(Player p, String suffix, boolean module, Material material, String name, String action, String... lore) {
        if (!module) return Util.item(Material.BARRIER, name, lore.length == 0 ? "&cModule disabled." : lore[0], "&cModule disabled.");
        if (!perm(p, suffix)) return Util.item(Material.BARRIER, name, "&cTidak punya permission GUI.");
        return tagged(material, name, action, lore);
    }

    private ItemStack tagged(Material material, String name, String action, String... lore) {
        String[] out = new String[(lore == null ? 0 : lore.length) + 1];
        if (lore != null) System.arraycopy(lore, 0, out, 0, lore.length);
        out[out.length - 1] = "&0" + TAG + action;
        return Util.item(material, name, out);
    }

    private String action(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null || item.getItemMeta().getLore() == null) return null;
        for (String line : item.getItemMeta().getLore()) {
            String stripped = Util.strip(line);
            if (stripped != null && stripped.startsWith(TAG)) return stripped.substring(TAG.length());
        }
        return null;
    }

    private <T> void nav(Inventory inv, String base, Page<T> page) {
        if (page.page() > 1) inv.setItem(45, tagged(Material.ARROW, "&ePrevious", base + ":" + (page.page() - 1), "&7Page " + (page.page() - 1)));
        inv.setItem(50, Util.item(Material.PAPER, "&fPage " + page.page() + "/" + page.pages()));
        if (page.page() < page.pages()) inv.setItem(53, tagged(Material.ARROW, "&eNext", base + ":" + (page.page() + 1), "&7Page " + (page.page() + 1)));
    }

    private String bar(int value, int goal) {
        int width = Math.max(5, Math.min(40, plugin.getConfig().getInt("gui.interaction.progress-bar-width", 20)));
        double ratio = goal <= 0 ? 0 : Math.max(0, Math.min(1, value / (double) goal));
        int filled = (int)Math.round(width * ratio);
        return "&a" + "|".repeat(filled) + "&8" + "|".repeat(width - filled) + " &f" + (int)(ratio * 100) + "%";
    }

    private Material partyMaterial(String mode) {
        return mode.equals("OPEN") ? Material.LIME_DYE : mode.equals("APPLICATION") ? Material.WRITABLE_BOOK : Material.GRAY_DYE;
    }

    private Material contractMaterial(String status, String type) {
        if (status.equals("ACTIVE")) return typeMaterial(type);
        return switch (status) {
            case "PENDING" -> Material.WRITABLE_BOOK;
            case "COMPLETED" -> Material.EMERALD;
            case "FAILED", "DENIED" -> Material.RED_DYE;
            case "CANCELLED", "EXPIRED" -> Material.GRAY_DYE;
            default -> Material.PAPER;
        };
    }

    private Material typeMaterial(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "mining" -> Material.DIAMOND_PICKAXE;
            case "hunter" -> Material.IRON_SWORD;
            case "farmer" -> Material.WHEAT;
            default -> Material.PAPER;
        };
    }

    private Material diplomacyMaterial(String relation) {
        return relation.equals("ALLY") ? Material.EMERALD : relation.equals("RIVAL") ? Material.IRON_SWORD : Material.PAPER;
    }

    private String recruitColor(String mode) { return mode.equals("OPEN") ? "&a" : mode.equals("APPLICATION") ? "&e" : "&c"; }
    private String relationColor(String relation) { return relation.equals("ALLY") ? "&a" : relation.equals("RIVAL") ? "&c" : "&7"; }
    private String statusColor(String status) { return status.equals("ACTIVE") ? "&b" : status.equals("PENDING") ? "&e" : status.equals("COMPLETED") ? "&a" : (status.equals("FAILED") || status.equals("DENIED")) ? "&c" : "&7"; }
    private int recruitOrder(String mode) { return mode.equals("OPEN") ? 0 : mode.equals("APPLICATION") ? 1 : 2; }
    private int relationOrder(String relation) { return relation.equals("ALLY") ? 0 : relation.equals("RIVAL") ? 1 : 2; }
    private int statusOrder(String status) { return switch (status) { case "ACTIVE" -> 0; case "PENDING" -> 1; case "COMPLETED" -> 2; case "FAILED" -> 3; case "DENIED" -> 4; case "CANCELLED" -> 5; case "EXPIRED" -> 6; default -> 7; }; }

    private String contractConfirmTitle(String op) {
        return switch (op) { case "accept" -> "&aAccept Contract?"; case "deny" -> "&cDeny Contract?"; case "cancel" -> "&cCancel Proposal?"; case "abandon" -> "&cAbandon Contract?"; default -> "&eConfirm Contract?"; };
    }

    private String contractConfirmLore(String op) {
        return switch (op) { case "accept" -> "&7Contract akan menjadi ACTIVE."; case "deny" -> "&7Proposal akan ditolak."; case "cancel" -> "&7Proposal issuer akan dibatalkan."; case "abandon" -> "&cContract gagal dan Trust dapat berkurang."; default -> "&7Konfirmasi aksi."; };
    }

    private String diplomacyConfirmTitle(String op) {
        return switch (op) { case "ally" -> "&aRequest Alliance?"; case "accept" -> "&aAccept Alliance?"; case "deny" -> "&cDeny Alliance?"; case "rival" -> "&cDeclare Rival?"; case "neutral" -> "&7Set Neutral?"; default -> "&eConfirm Diplomacy?"; };
    }

    private String displaySafe(String party) { return party != null && parties.exists(party) ? parties.display(party) : (party == null ? "Unknown" : party); }

    private int integer(String[] parts, int index, int fallback) {
        if (index < 0 || index >= parts.length) return fallback;
        try { return Integer.parseInt(parts[index]); } catch (Exception ignored) { return fallback; }
    }

    private String clean(String title) {
        String value = Util.strip(title);
        return value == null ? "" : value;
    }

    private boolean ourTitle(String title) {
        return Set.of(HUB, BROWSER, PARTY_DETAIL, CONTRACTS, CONTRACT_DETAIL, CONTRACT_TARGET, CONTRACT_TYPE,
                CONTRACT_GOAL, DIPLOMACY, DIPLOMACY_DETAIL, RECRUITMENT, APPLICATIONS, APPLICATION_DETAIL,
                MY_APPLICATIONS, RANKS, CONFIRM).stream().anyMatch(x -> x.equalsIgnoreCase(title));
    }

    private boolean root(Player p) {
        if (!enabled()) { msg(p, " &cInteraction GUI dinonaktifkan. Command v1.3 tetap tersedia."); return false; }
        if (!canView(p)) { msg(p, " &cKamu tidak punya permission Interaction GUI."); return false; }
        return true;
    }

    private String requireParty(Player p) {
        String party = parties.partyOf(p.getUniqueId());
        if (party == null) msg(p, " &cKamu belum punya Party.");
        return party;
    }

    private void denied(Player p, String section) { msg(p, " &cKamu tidak punya akses GUI untuk " + section + "."); }
    private void disabled(Player p, String module) { msg(p, " &c" + module + " dinonaktifkan di server ini."); }
    private void msg(Player p, String message) { p.sendMessage(parties.prefix() + Util.color(message)); }

    private <T> Page<T> page(List<T> all, int requested, int size) {
        int pages = Math.max(1, (all.size() + size - 1) / size);
        int page = Math.max(1, Math.min(requested, pages));
        int from = Math.min(all.size(), (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        return new Page<>(new ArrayList<>(all.subList(from, to)), page, pages);
    }

    private record Page<T>(List<T> items, int page, int pages) { }
}
