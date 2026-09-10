package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MENKIESTESParty v1.6.0 administration/moderation layer.
 *
 * Staff tooling stays outside the public API. Mutations operate on the same
 * in-memory documents as the core and are persisted through StorageBundle, so
 * YAML/SQLite/MySQL behavior remains identical. Dangerous actions are staged
 * behind a short confirmation window and every mutation is written to a
 * bounded audit log in interactions.yml.
 */
public final class AdministrationManager implements Listener, CommandExecutor, TabCompleter {
    private static final String BROWSER_TITLE = "Party Admin Browser";
    private static final String INSPECT_TITLE = "Party Admin Inspect";
    private static final String GUI_TAG = "MENKI_ADMIN:";
    private static final String INTERACTION_TAG = "MENKI_INTERACT:";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Set<String> CONTRACT_MUTATIONS = Set.of("create", "accept", "deny", "cancel", "abandon");
    private static final Set<String> DIPLO_MUTATIONS = Set.of("request", "accept", "deny", "neutral", "rival");
    private static final Set<String> ROSTER_MUTATIONS = Set.of("invite", "accept", "leave", "disband", "kick", "promote", "demote");

    private record PendingAction(String description, long expiresAt, Runnable action) {}

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final ProgressionManager progression;
    private final InteractionManager interactions;
    private final StorageBundle db;
    private final YamlConfiguration settings;
    private final Map<String, PendingAction> pending = new HashMap<>();

    public AdministrationManager(MENKIESTESPartyPlugin plugin, PartyService parties,
                                 ProgressionManager progression, InteractionManager interactions,
                                 StorageBundle db) {
        this.plugin = plugin;
        this.parties = parties;
        this.progression = progression;
        this.interactions = interactions;
        this.db = db;
        File file = new File(plugin.getDataFolder(), "administration.yml");
        if (!file.isFile()) plugin.saveResource("administration.yml", false);
        this.settings = YamlConfiguration.loadConfiguration(file);

        PluginCommand command = plugin.getCommand("partyadmin");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        pruneAudit();
    }

    public boolean enabled() {
        return settings.getBoolean("administration.enabled", true);
    }

    public boolean isFrozen(String party) {
        return party != null && db.parties.getBoolean("parties." + party + ".moderation.frozen.enabled", false);
    }

    public boolean blocksRoster(String party) {
        return enabled() && settings.getBoolean("administration.freeze.block-roster", true) && isFrozen(party);
    }

    public boolean blocksInteractions(String party) {
        return enabled() && settings.getBoolean("administration.freeze.block-interactions", true) && isFrozen(party);
    }

    public String freezeReason(String party) {
        return db.parties.getString("parties." + party + ".moderation.frozen.reason", "Administrative freeze");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handle(sender, args);
    }

    private boolean handle(CommandSender sender, String[] args) {
        if (!enabled()) {
            msg(sender, "&cAdministration module is disabled.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player && can(sender, "gui")) openBrowser(player, 1);
            else usage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> usage(sender);
            case "list" -> {
                if (!require(sender, "inspect")) return true;
                int page = args.length > 1 ? parseInt(args[1], 1, 1, 100000) : 1;
                if (sender instanceof Player player && can(sender, "gui")) openBrowser(player, page);
                else listText(sender, page);
            }
            case "inspect" -> {
                if (!require(sender, "inspect")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin inspect <party>"); return true; }
                String party = resolveParty(args[1]);
                if (party == null) { notFound(sender); return true; }
                if (sender instanceof Player player && can(sender, "gui")) openInspect(player, party, 1);
                else inspectText(sender, party);
            }
            case "forcejoin" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 3) { msg(sender, "&c/partyadmin forcejoin <player> <party>"); return true; }
                forceJoin(sender, args[1], args[2]);
            }
            case "remove", "forceremove" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin remove <player>"); return true; }
                forceRemove(sender, args[1]);
            }
            case "transfer" -> {
                if (!require(sender, "dangerous")) return true;
                if (args.length < 3) { msg(sender, "&c/partyadmin transfer <party> <player>"); return true; }
                String party = resolveParty(args[1]);
                OfflinePlayer target = resolvePlayer(args[2]);
                if (party == null || target == null) { notFound(sender); return true; }
                requestDangerous(sender, "Transfer owner " + parties.display(party) + " -> " + playerName(target),
                        () -> transferOwner(sender, party, target));
            }
            case "rename" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 3) { msg(sender, "&c/partyadmin rename <party> <displayName>"); return true; }
                rename(sender, args[1], args[2]);
            }
            case "freeze" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin freeze <party> [reason]"); return true; }
                String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Administrative freeze";
                freeze(sender, args[1], reason);
            }
            case "unfreeze" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin unfreeze <party>"); return true; }
                unfreeze(sender, args[1]);
            }
            case "xp", "rep" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 4) { msg(sender, "&c/partyadmin xp <party> <set|add|remove> <amount>"); return true; }
                changeXp(sender, args[1], args[2], args[3]);
            }
            case "repair" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin repair <party>"); return true; }
                repair(sender, args[1]);
            }
            case "resetproject" -> {
                if (!require(sender, "dangerous")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin resetproject <party>"); return true; }
                String party = resolveParty(args[1]);
                if (party == null) { notFound(sender); return true; }
                requestDangerous(sender, "Reset active project progress for " + parties.display(party),
                        () -> resetProject(sender, party));
            }
            case "resetcontract" -> {
                if (!require(sender, "dangerous")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin resetcontract <id>"); return true; }
                String id = args[1].toUpperCase(Locale.ROOT);
                if (!db.interactions.contains("contracts." + id + ".status")) { msg(sender, "&cContract not found."); return true; }
                requestDangerous(sender, "Reset ACTIVE contract progress " + id,
                        () -> resetContract(sender, id));
            }
            case "export" -> {
                if (!require(sender, "inspect")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin export <party>"); return true; }
                String party = resolveParty(args[1]);
                if (party == null) { notFound(sender); return true; }
                export(sender, party, "admin-exports", "EXPORT");
            }
            case "archive" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin archive <party>"); return true; }
                String party = resolveParty(args[1]);
                if (party == null) { notFound(sender); return true; }
                export(sender, party, "archives", "ARCHIVE");
            }
            case "disband" -> {
                if (!require(sender, "dangerous")) return true;
                if (args.length < 2) { msg(sender, "&c/partyadmin disband <party>"); return true; }
                String party = resolveParty(args[1]);
                if (party == null) { notFound(sender); return true; }
                requestDangerous(sender, "Archive then permanently disband " + parties.display(party),
                        () -> disband(sender, party));
            }
            case "audit" -> {
                if (!require(sender, "audit")) return true;
                int page = args.length > 1 ? parseInt(args[1], 1, 1, 100000) : 1;
                showAudit(sender, page);
            }
            case "confirm" -> confirm(sender);
            case "cancel" -> cancelPending(sender);
            default -> usage(sender);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Moderation state and freeze guards
    // ---------------------------------------------------------------------

    private void freeze(CommandSender sender, String input, String reason) {
        String party = resolveParty(input);
        if (party == null) { notFound(sender); return; }
        String base = "parties." + party + ".moderation.frozen";
        if (!isFrozen(party)) {
            db.parties.set(base + ".previous-recruitment", interactions.recruitmentMode(party));
        }
        db.parties.set(base + ".enabled", true);
        db.parties.set(base + ".reason", trim(reason, 160));
        db.parties.set(base + ".by", actor(sender));
        db.parties.set(base + ".at", System.currentTimeMillis());
        if (settings.getBoolean("administration.freeze.force-recruitment-closed", true)) {
            db.parties.set("parties." + party + ".recruitment.mode", "CLOSED");
        }
        audit(sender, "FREEZE", party, trim(reason, 160));
        durable();
        parties.broadcastParty(party, parties.prefix() + " &cParty dibekukan sementara oleh staff. &7Reason: &f" + trim(reason, 160));
        msg(sender, "&aFrozen: &f" + parties.display(party));
    }

    private void unfreeze(CommandSender sender, String input) {
        String party = resolveParty(input);
        if (party == null) { notFound(sender); return; }
        if (!isFrozen(party)) { msg(sender, "&eParty is not frozen."); return; }
        String base = "parties." + party + ".moderation.frozen";
        String previous = db.parties.getString(base + ".previous-recruitment", "APPLICATION").toUpperCase(Locale.ROOT);
        if (!Set.of("OPEN", "APPLICATION", "CLOSED").contains(previous)) previous = "APPLICATION";
        db.parties.set(base, null);
        if (settings.getBoolean("administration.freeze.force-recruitment-closed", true)) {
            db.parties.set("parties." + party + ".recruitment.mode", previous);
        }
        audit(sender, "UNFREEZE", party, "restored recruitment=" + previous);
        durable();
        parties.broadcastParty(party, parties.prefix() + " &aAdministrative freeze has been lifted.");
        msg(sender, "&aUnfrozen: &f" + parties.display(party));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] split = raw.substring(1).trim().split("\\s+");
        if (split.length == 0) return;
        String root = split[0].toLowerCase(Locale.ROOT);

        if ((root.equals("party") || root.equals("p") || root.equals("parties"))
                && split.length >= 2 && split[1].equalsIgnoreCase("admin")) {
            event.setCancelled(true);
            String[] args = split.length <= 2 ? new String[0] : Arrays.copyOfRange(split, 2, split.length);
            handle(event.getPlayer(), args);
            return;
        }

        Player player = event.getPlayer();
        String own = parties.partyOf(player.getUniqueId());
        if (root.equals("partyapply") || root.equals("papply")) {
            if (split.length >= 2 && !split[1].equalsIgnoreCase("cancel")) {
                String target = resolveParty(split[1]);
                if (target != null && blocksRoster(target)) {
                    event.setCancelled(true);
                    frozenMessage(player, target);
                }
            }
            return;
        }
        if (own == null || !isFrozen(own)) return;

        boolean blocked = false;
        if (root.equals("partycontract") || root.equals("pcontract")) {
            blocked = split.length >= 2 && CONTRACT_MUTATIONS.contains(split[1].toLowerCase(Locale.ROOT)) && blocksInteractions(own);
        } else if (root.equals("partydiplomacy") || root.equals("pdiplomacy") || root.equals("pdiplo")) {
            blocked = split.length >= 2 && DIPLO_MUTATIONS.contains(split[1].toLowerCase(Locale.ROOT)) && blocksInteractions(own);
        } else if (root.equals("partyapplications") || root.equals("papps")) {
            blocked = split.length >= 2 && Set.of("accept", "deny").contains(split[1].toLowerCase(Locale.ROOT)) && blocksInteractions(own);
        } else if (root.equals("partyrecruitment") || root.equals("precruit")) {
            blocked = split.length >= 2 && blocksInteractions(own);
        } else if (root.equals("party") || root.equals("p") || root.equals("parties")) {
            if (split.length >= 2) {
                String sub = split[1].toLowerCase(Locale.ROOT);
                if (ROSTER_MUTATIONS.contains(sub) && blocksRoster(own)) blocked = true;
                else if ((sub.equals("contract") || sub.equals("contracts")) && split.length >= 3
                        && CONTRACT_MUTATIONS.contains(split[2].toLowerCase(Locale.ROOT)) && blocksInteractions(own)) blocked = true;
                else if ((sub.equals("diplomacy") || sub.equals("diplo")) && split.length >= 3
                        && DIPLO_MUTATIONS.contains(split[2].toLowerCase(Locale.ROOT)) && blocksInteractions(own)) blocked = true;
                else if ((sub.equals("applications") || sub.equals("apps")) && split.length >= 3
                        && Set.of("accept", "deny").contains(split[2].toLowerCase(Locale.ROOT)) && blocksInteractions(own)) blocked = true;
                else if ((sub.equals("recruitment") || sub.equals("recruit")) && split.length >= 3 && blocksInteractions(own)) blocked = true;
                else if (settings.getBoolean("administration.freeze.block-progression-management", false)
                        && (sub.equals("project") || sub.equals("projects") || sub.equals("skill") || sub.equals("skills")
                        || sub.equals("division") || sub.equals("divisions")) && split.length >= 3) blocked = true;
            }
        }
        if (blocked) {
            event.setCancelled(true);
            frozenMessage(player, own);
        }
    }

    /** Stops stale Interaction GUI mutation buttons when a Party becomes frozen after the GUI opened. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenInteractionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String action = taggedAction(event.getCurrentItem(), INTERACTION_TAG);
        if (action == null) return;
        String own = parties.partyOf(player.getUniqueId());

        boolean mutation = action.startsWith("contract-create:") || action.startsWith("do-contract-create:")
                || action.startsWith("contract-confirm:") || action.startsWith("do-contract:")
                || action.startsWith("diplo-confirm:") || action.startsWith("do-diplo:")
                || action.startsWith("recruit:") || action.startsWith("app-confirm:") || action.startsWith("do-app:");
        if (mutation && own != null && blocksInteractions(own)) {
            event.setCancelled(true);
            event.setCurrentItem(null);
            frozenMessage(player, own);
            return;
        }

        if (action.startsWith("apply-confirm:") || action.startsWith("do-apply:")) {
            String[] parts = action.split(":");
            if (parts.length >= 2) {
                String target = resolveParty(parts[1]);
                if (target != null && blocksRoster(target)) {
                    event.setCancelled(true);
                    event.setCurrentItem(null);
                    frozenMessage(player, target);
                }
            }
        }
    }

    private void frozenMessage(CommandSender sender, String party) {
        msg(sender, "&cParty &f" + parties.display(party) + " &cis administratively frozen. &7" + freezeReason(party));
    }

    // ---------------------------------------------------------------------
    // Staff mutations
    // ---------------------------------------------------------------------

    private void forceJoin(CommandSender sender, String playerInput, String partyInput) {
        if (plugin.war().membershipLocked()) { warLocked(sender); return; }
        String targetParty = resolveParty(partyInput);
        OfflinePlayer target = resolvePlayer(playerInput);
        if (targetParty == null || target == null) { notFound(sender); return; }
        UUID id = target.getUniqueId();
        String current = parties.partyOf(id);
        if (targetParty.equals(current)) { msg(sender, "&ePlayer is already in that Party."); return; }
        if (current != null && id.equals(parties.owner(current))) {
            msg(sender, "&cTarget owns another Party. Transfer/disband that Party first.");
            return;
        }
        if (parties.memberCount(targetParty) >= parties.memberLimit(targetParty)
                && !settings.getBoolean("administration.forcejoin.allow-over-cap", false)) {
            msg(sender, "&cTarget Party is full. Over-cap force join is disabled.");
            return;
        }
        if (current != null) {
            db.parties.set("parties." + current + ".members." + id, null);
            db.parties.set("players." + id, null);
        }
        String base = "parties." + targetParty + ".members." + id;
        db.parties.set(base + ".name", playerName(target));
        db.parties.set(base + ".role", "MEMBER");
        db.parties.set(base + ".joined-at", System.currentTimeMillis());
        db.parties.set("players." + id + ".party", targetParty);
        clearApplications(id);
        audit(sender, "FORCE_JOIN", targetParty, playerName(target) + (current == null ? "" : " from=" + current));
        durable();
        parties.broadcastParty(targetParty, parties.prefix() + " &f" + playerName(target) + " &ajoined by staff action.");
        msg(sender, "&aJoined &f" + playerName(target) + " &ato &f" + parties.display(targetParty));
    }

    private void forceRemove(CommandSender sender, String playerInput) {
        if (plugin.war().membershipLocked()) { warLocked(sender); return; }
        OfflinePlayer target = resolvePlayer(playerInput);
        if (target == null) { notFound(sender); return; }
        UUID id = target.getUniqueId();
        String party = parties.partyOf(id);
        if (party == null || !parties.exists(party)) { msg(sender, "&ePlayer is not in a Party."); return; }
        if (id.equals(parties.owner(party))) { msg(sender, "&cCannot remove Party owner. Transfer owner first."); return; }
        db.parties.set("parties." + party + ".members." + id, null);
        db.parties.set("players." + id, null);
        audit(sender, "FORCE_REMOVE", party, playerName(target));
        durable();
        parties.broadcastParty(party, parties.prefix() + " &f" + playerName(target) + " &cwas removed by staff.");
        msg(sender, "&aRemoved &f" + playerName(target) + " &afrom &f" + parties.display(party));
    }

    private void transferOwner(CommandSender sender, String party, OfflinePlayer target) {
        if (plugin.war().membershipLocked()) { warLocked(sender); return; }
        if (!parties.exists(party)) { notFound(sender); return; }
        UUID id = target.getUniqueId();
        if (!party.equals(parties.partyOf(id))) { msg(sender, "&cTarget must already be a member of the Party."); return; }
        UUID old = parties.owner(party);
        if (id.equals(old)) { msg(sender, "&eTarget is already owner."); return; }
        if (old != null) db.parties.set("parties." + party + ".members." + old + ".role", "OFFICER");
        db.parties.set("parties." + party + ".owner", id.toString());
        db.parties.set("parties." + party + ".members." + id + ".role", "OWNER");
        audit(sender, "TRANSFER_OWNER", party, "old=" + name(old) + " new=" + playerName(target));
        durable();
        parties.broadcastParty(party, parties.prefix() + " &eParty ownership transferred to &f" + playerName(target) + "&e by staff.");
        msg(sender, "&aOwner transferred.");
    }

    private void rename(CommandSender sender, String input, String newDisplay) {
        String party = resolveParty(input);
        if (party == null) { notFound(sender); return; }
        int min = plugin.getConfig().getInt("party.min-name-length", 3);
        int max = plugin.getConfig().getInt("party.max-name-length", 16);
        if (!Util.validPartyName(newDisplay, min, max)) {
            msg(sender, "&cDisplay name must use A-Z, 0-9, underscore and length " + min + "-" + max + ".");
            return;
        }
        for (String other : partyKeys()) {
            if (!other.equals(party) && parties.display(other).equalsIgnoreCase(newDisplay)) {
                msg(sender, "&cAnother Party already uses that display name.");
                return;
            }
        }
        String old = parties.display(party);
        db.parties.set("parties." + party + ".display", newDisplay);
        audit(sender, "RENAME", party, "old=" + old + " new=" + newDisplay + " key-stable=true");
        durable();
        parties.broadcastParty(party, parties.prefix() + " &eParty display renamed from &f" + old + " &eto &f" + newDisplay + "&e.");
        msg(sender, "&aRenamed. Internal key remains &f" + party + "&a.");
    }

    private void changeXp(CommandSender sender, String input, String modeInput, String amountInput) {
        String party = resolveParty(input);
        if (party == null) { notFound(sender); return; }
        String mode = modeInput.toLowerCase(Locale.ROOT);
        if (!Set.of("set", "add", "remove").contains(mode)) { msg(sender, "&cMode must be set, add, or remove."); return; }
        int amount;
        try { amount = Integer.parseInt(amountInput); }
        catch (Exception ex) { msg(sender, "&cAmount must be a number."); return; }
        amount = Math.max(0, Math.min(100_000_000, amount));
        int old = parties.rep(party);
        int next = switch (mode) {
            case "set" -> amount;
            case "remove" -> Math.max(0, old - amount);
            default -> Math.min(100_000_000, old + amount);
        };
        parties.addRep(party, next - old);
        audit(sender, "XP_" + mode.toUpperCase(Locale.ROOT), party, "old=" + old + " new=" + next);
        durable();
        msg(sender, "&aParty XP updated: &f" + old + " -> " + parties.rep(party));
    }

    private void repair(CommandSender sender, String input) {
        String party = resolveParty(input);
        if (party == null) { notFound(sender); return; }
        UUID owner = parties.owner(party);
        if (owner == null) { msg(sender, "&cRepair stopped: owner UUID is missing/invalid. No automatic guess was made."); return; }
        int repaired = 0;
        String ownerBase = "parties." + party + ".members." + owner;
        if (!db.parties.contains(ownerBase)) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
            db.parties.set(ownerBase + ".name", playerName(offline));
            db.parties.set(ownerBase + ".joined-at", System.currentTimeMillis());
            repaired++;
        }
        if (!"OWNER".equalsIgnoreCase(db.parties.getString(ownerBase + ".role", ""))) {
            db.parties.set(ownerBase + ".role", "OWNER");
            repaired++;
        }

        Set<UUID> members = new HashSet<>(parties.members(party));
        members.add(owner);
        for (UUID member : members) {
            String index = db.parties.getString("players." + member + ".party");
            if (!party.equals(index)) {
                db.parties.set("players." + member + ".party", party);
                repaired++;
            }
            if (!member.equals(owner)) {
                String rolePath = "parties." + party + ".members." + member + ".role";
                if ("OWNER".equalsIgnoreCase(db.parties.getString(rolePath, "MEMBER"))) {
                    db.parties.set(rolePath, "OFFICER");
                    repaired++;
                }
            }
        }

        ConfigurationSection players = db.parties.getConfigurationSection("players");
        if (players != null) {
            for (String uuidText : new ArrayList<>(players.getKeys(false))) {
                if (!party.equals(players.getString(uuidText + ".party"))) continue;
                try {
                    UUID uuid = UUID.fromString(uuidText);
                    if (!members.contains(uuid)) {
                        db.parties.set("players." + uuidText, null);
                        repaired++;
                    }
                } catch (Exception ignored) {
                    db.parties.set("players." + uuidText, null);
                    repaired++;
                }
            }
        }
        audit(sender, "REPAIR", party, "entries=" + repaired);
        durable();
        msg(sender, "&aRepair completed. Changes: &f" + repaired);
    }

    private void resetProject(CommandSender sender, String party) {
        if (!parties.exists(party)) { notFound(sender); return; }
        String id = progression.currentProject(party);
        if (id == null) { msg(sender, "&eParty has no active project."); return; }
        String root = "parties." + party + ".progression.projects.active";
        db.parties.set(root + ".progress", 0.0);
        for (UUID member : parties.members(party)) {
            db.parties.set("parties." + party + ".members." + member + ".progression.project-contribution", 0.0);
        }
        audit(sender, "RESET_PROJECT", party, "project=" + id);
        durable();
        parties.broadcastParty(party, parties.prefix() + " &eActive Project progress was reset by staff.");
        msg(sender, "&aProject progress reset: &f" + id);
    }

    private void resetContract(CommandSender sender, String id) {
        String base = "contracts." + id;
        String status = db.interactions.getString(base + ".status", "UNKNOWN").toUpperCase(Locale.ROOT);
        if (!status.equals("ACTIVE")) { msg(sender, "&cOnly ACTIVE contracts can have progress reset safely."); return; }
        String target = db.interactions.getString(base + ".target", "");
        db.interactions.set(base + ".progress", 0);
        db.interactions.set(base + ".contributors", null);
        db.interactions.set(base + ".admin-reset-at", System.currentTimeMillis());
        db.interactions.set(base + ".admin-reset-by", actor(sender));
        audit(sender, "RESET_CONTRACT", target, "contract=" + id);
        durable();
        if (parties.exists(target)) parties.broadcastParty(target, parties.prefix() + " &eContract &f" + id + " &eprogress reset by staff.");
        msg(sender, "&aContract progress reset: &f" + id);
    }

    private void disband(CommandSender sender, String party) {
        if (plugin.war().membershipLocked()) { warLocked(sender); return; }
        if (!parties.exists(party)) { notFound(sender); return; }
        String display = parties.display(party);
        Path archive;
        try {
            archive = exportParty(sender, party, "archives", false);
        } catch (Exception failure) {
            msg(sender, "&cDisband aborted because archive failed: &f" + safe(failure));
            return;
        }
        List<UUID> oldMembers = new ArrayList<>(parties.members(party));
        cancelPartyContracts(party, sender);
        cleanupDiplomacy(party);
        db.interactions.set("applications." + party, null);
        ConfigurationSection apps = db.interactions.getConfigurationSection("applications");
        if (apps != null) {
            for (String target : new ArrayList<>(apps.getKeys(false))) {
                for (UUID member : oldMembers) db.interactions.set("applications." + target + "." + member, null);
            }
        }
        db.interactions.set("activity." + party, null);
        db.interactions.set("stats." + party, null);
        for (UUID member : oldMembers) db.parties.set("players." + member, null);
        db.parties.set("parties." + party, null);
        audit(sender, "DISBAND", party, "display=" + display + " archive=" + archive.toAbsolutePath());
        durable();
        Bukkit.broadcastMessage(parties.prefix() + Util.color(" &cParty &f" + display + " &cwas disbanded by staff."));
        msg(sender, "&aDisband complete. Archive: &f" + archive.toAbsolutePath());
    }

    private void cancelPartyContracts(String party, CommandSender sender) {
        ConfigurationSection contracts = db.interactions.getConfigurationSection("contracts");
        if (contracts == null) return;
        long now = System.currentTimeMillis();
        for (String id : new ArrayList<>(contracts.getKeys(false))) {
            String base = "contracts." + id;
            String source = db.interactions.getString(base + ".source", "");
            String target = db.interactions.getString(base + ".target", "");
            if (!party.equals(source) && !party.equals(target)) continue;
            String status = db.interactions.getString(base + ".status", "UNKNOWN").toUpperCase(Locale.ROOT);
            if (status.equals("PENDING") || status.equals("ACTIVE")) {
                db.interactions.set(base + ".status", "CANCELLED");
                db.interactions.set(base + ".ended-at", now);
                db.interactions.set(base + ".admin-ended-by", actor(sender));
                db.interactions.set(base + ".admin-ended-reason", "PARTY_DISBAND");
            }
        }
    }

    private void cleanupDiplomacy(String party) {
        ConfigurationSection pairs = db.interactions.getConfigurationSection("diplomacy.pairs");
        if (pairs != null) {
            for (String key : new ArrayList<>(pairs.getKeys(false))) {
                String base = "diplomacy.pairs." + key;
                String a = db.interactions.getString(base + ".a", "");
                String b = db.interactions.getString(base + ".b", "");
                if (party.equals(a) || party.equals(b)) db.interactions.set(base, null);
            }
        }
        db.interactions.set("diplomacy.requests." + party, null);
        ConfigurationSection requests = db.interactions.getConfigurationSection("diplomacy.requests");
        if (requests != null) {
            for (String target : new ArrayList<>(requests.getKeys(false))) {
                db.interactions.set("diplomacy.requests." + target + "." + party, null);
            }
        }
        ConfigurationSection cooldowns = db.interactions.getConfigurationSection("cooldowns.contract-pair");
        if (cooldowns != null) {
            for (String pair : new ArrayList<>(cooldowns.getKeys(false))) {
                if (pair.equals(party) || pair.startsWith(party + "~") || pair.endsWith("~" + party)) {
                    db.interactions.set("cooldowns.contract-pair." + pair, null);
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Export/archive
    // ---------------------------------------------------------------------

    private void export(CommandSender sender, String party, String directory, String action) {
        try {
            Path path = exportParty(sender, party, directory, true);
            audit(sender, action, party, path.toAbsolutePath().toString());
            durable();
            msg(sender, "&a" + action + " created: &f" + path.toAbsolutePath());
        } catch (Exception failure) {
            msg(sender, "&c" + action + " failed: &f" + safe(failure));
        }
    }

    private Path exportParty(CommandSender sender, String party, String directory, boolean includeAudit) throws IOException {
        if (!parties.exists(party)) throw new IOException("Party not found");
        Path dir = plugin.getDataFolder().toPath().resolve(directory);
        Files.createDirectories(dir);
        Path path = dir.resolve(party + "-" + FILE_STAMP.format(Instant.now()) + ".yml");
        YamlConfiguration out = new YamlConfiguration();
        out.set("meta.plugin-version", plugin.getDescription().getVersion());
        out.set("meta.exported-at", System.currentTimeMillis());
        out.set("meta.exported-by", actor(sender));
        out.set("meta.party-key", party);
        out.set("meta.party-display", parties.display(party));

        ConfigurationSection partySection = db.parties.getConfigurationSection("parties." + party);
        copySection(partySection, out, "party");
        for (UUID member : parties.members(party)) out.set("player-index." + member + ".party", party);

        ConfigurationSection contracts = db.interactions.getConfigurationSection("contracts");
        if (contracts != null) for (String id : contracts.getKeys(false)) {
            String base = "contracts." + id;
            if (party.equals(db.interactions.getString(base + ".source")) || party.equals(db.interactions.getString(base + ".target"))) {
                copySection(contracts.getConfigurationSection(id), out, "interactions.contracts." + id);
            }
        }
        copySection(db.interactions.getConfigurationSection("applications." + party), out, "interactions.applications." + party);

        ConfigurationSection pairs = db.interactions.getConfigurationSection("diplomacy.pairs");
        if (pairs != null) for (String key : pairs.getKeys(false)) {
            String base = "diplomacy.pairs." + key;
            if (party.equals(db.interactions.getString(base + ".a")) || party.equals(db.interactions.getString(base + ".b"))) {
                copySection(pairs.getConfigurationSection(key), out, "interactions.diplomacy.pairs." + key);
            }
        }
        ConfigurationSection requests = db.interactions.getConfigurationSection("diplomacy.requests");
        if (requests != null) for (String target : requests.getKeys(false)) {
            ConfigurationSection sourceSection = requests.getConfigurationSection(target);
            if (sourceSection == null) continue;
            if (target.equals(party)) copySection(sourceSection, out, "interactions.diplomacy.requests." + target);
            else if (sourceSection.isConfigurationSection(party)) {
                copySection(sourceSection.getConfigurationSection(party), out, "interactions.diplomacy.requests." + target + "." + party);
            }
        }
        if (includeAudit) {
            ConfigurationSection audit = db.interactions.getConfigurationSection("moderation.audit");
            if (audit != null) for (String id : audit.getKeys(false)) {
                if (party.equals(audit.getString(id + ".party"))) {
                    copySection(audit.getConfigurationSection(id), out, "moderation-audit." + id);
                }
            }
        }
        out.save(path.toFile());
        return path;
    }

    private static void copySection(ConfigurationSection source, YamlConfiguration target, String root) {
        if (source == null) return;
        for (String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) target.set(root + "." + key, source.get(key));
        }
    }

    // ---------------------------------------------------------------------
    // Confirmation + audit
    // ---------------------------------------------------------------------

    private void requestDangerous(CommandSender sender, String description, Runnable action) {
        int seconds = Math.max(5, Math.min(120, settings.getInt("administration.confirmation-seconds", 30)));
        pending.put(actorKey(sender), new PendingAction(description, System.currentTimeMillis() + seconds * 1000L, action));
        msg(sender, "&ePending dangerous action: &f" + description);
        msg(sender, "&7Run &f/partyadmin confirm &7within &f" + seconds + "s&7, or /partyadmin cancel.");
    }

    private void confirm(CommandSender sender) {
        if (!require(sender, "dangerous")) return;
        PendingAction action = pending.remove(actorKey(sender));
        if (action == null) { msg(sender, "&eNo pending dangerous action."); return; }
        if (action.expiresAt() < System.currentTimeMillis()) { msg(sender, "&cConfirmation expired. Run the original command again."); return; }
        try {
            action.action().run();
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("Admin action failed: " + action.description() + " -> " + safe(failure));
            msg(sender, "&cAdmin action failed. Check console.");
        }
    }

    private void cancelPending(CommandSender sender) {
        PendingAction removed = pending.remove(actorKey(sender));
        msg(sender, removed == null ? "&eNo pending action." : "&aPending action cancelled.");
    }

    private void audit(CommandSender sender, String action, String party, String detail) {
        long seq = db.interactions.getLong("moderation.audit-seq", 0L) + 1L;
        db.interactions.set("moderation.audit-seq", seq);
        String id = String.format(Locale.ROOT, "%012d", seq);
        String base = "moderation.audit." + id;
        db.interactions.set(base + ".at", System.currentTimeMillis());
        db.interactions.set(base + ".actor", actor(sender));
        db.interactions.set(base + ".action", action);
        db.interactions.set(base + ".party", party == null ? "" : party);
        db.interactions.set(base + ".detail", trim(detail, 500));
        pruneAudit();
        plugin.saveDataSoon();
    }

    private void pruneAudit() {
        ConfigurationSection section = db.interactions.getConfigurationSection("moderation.audit");
        if (section == null) return;
        int max = Math.max(50, settings.getInt("administration.audit.max-entries", 500));
        int days = Math.max(1, settings.getInt("administration.audit.retention-days", 90));
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort(String::compareTo);
        for (String key : new ArrayList<>(keys)) {
            if (section.getLong(key + ".at", 0L) < cutoff) {
                db.interactions.set("moderation.audit." + key, null);
                keys.remove(key);
            }
        }
        while (keys.size() > max) {
            String oldest = keys.remove(0);
            db.interactions.set("moderation.audit." + oldest, null);
        }
    }

    private void showAudit(CommandSender sender, int requestedPage) {
        ConfigurationSection section = db.interactions.getConfigurationSection("moderation.audit");
        List<String> keys = section == null ? new ArrayList<>() : new ArrayList<>(section.getKeys(false));
        keys.sort(Comparator.reverseOrder());
        int perPage = 10;
        int pages = Math.max(1, (keys.size() + perPage - 1) / perPage);
        int page = Math.max(1, Math.min(pages, requestedPage));
        msg(sender, "&6&lAUDIT LOG &8- &fPage " + page + "/" + pages);
        int start = (page - 1) * perPage;
        for (int i = start; i < Math.min(keys.size(), start + perPage); i++) {
            String id = keys.get(i);
            String base = "moderation.audit." + id;
            long at = db.interactions.getLong(base + ".at", 0L);
            String actor = db.interactions.getString(base + ".actor", "?");
            String action = db.interactions.getString(base + ".action", "?");
            String party = db.interactions.getString(base + ".party", "");
            String detail = db.interactions.getString(base + ".detail", "");
            sender.sendMessage(Util.color("&8" + id + " &7" + formatTime(at) + " &e" + action + " &f" + party + " &8| &7" + actor + " &8| &7" + detail));
        }
        if (keys.isEmpty()) msg(sender, "&7No administration audit entries yet.");
    }

    // ---------------------------------------------------------------------
    // GUI
    // ---------------------------------------------------------------------

    public void openBrowser(Player player, int requestedPage) {
        if (!require(player, "inspect") || !require(player, "gui")) return;
        List<String> keys = partyKeys();
        keys.sort(Comparator.comparing(parties::display, String.CASE_INSENSITIVE_ORDER));
        int perPage = 45;
        int pages = Math.max(1, (keys.size() + perPage - 1) / perPage);
        int page = Math.max(1, Math.min(pages, requestedPage));
        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8" + BROWSER_TITLE));
        int start = (page - 1) * perPage;
        int slot = 0;
        for (int i = start; i < Math.min(keys.size(), start + perPage); i++) {
            String party = keys.get(i);
            UUID owner = parties.owner(party);
            inv.setItem(slot++, tagged(Material.PLAYER_HEAD,
                    (isFrozen(party) ? "&c❄ " : "&b") + parties.display(party), "inspect:" + party + ":" + page,
                    "&7Key: &f" + party,
                    "&7Owner: &f" + name(owner),
                    "&7Level/XP: &f" + parties.level(party) + " / " + parties.rep(party),
                    "&7Members: &f" + parties.memberCount(party) + "/" + parties.memberLimit(party),
                    isFrozen(party) ? "&cFROZEN: &f" + freezeReason(party) : "&aACTIVE",
                    "&eClick to inspect."));
        }
        if (keys.isEmpty()) inv.setItem(22, tagged(Material.PAPER, "&7No Parties", "noop", "&7No Party data exists."));
        if (page > 1) inv.setItem(45, tagged(Material.ARROW, "&ePrevious", "page:" + (page - 1), "&7Page " + (page - 1)));
        inv.setItem(49, tagged(Material.BOOK, "&6Admin Browser", "noop", "&7Page &f" + page + "/" + pages, "&7Parties: &f" + keys.size()));
        if (page < pages) inv.setItem(53, tagged(Material.ARROW, "&eNext", "page:" + (page + 1), "&7Page " + (page + 1)));
        player.openInventory(inv);
    }

    public void openInspect(Player player, String party, int browserPage) {
        if (!require(player, "inspect") || !require(player, "gui")) return;
        if (!parties.exists(party)) { notFound(player); openBrowser(player, browserPage); return; }
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8" + INSPECT_TITLE));
        UUID owner = parties.owner(party);
        String project = progression.currentProject(party);
        inv.setItem(4, tagged(Material.NETHER_STAR, "&b&l" + parties.display(party), "noop",
                "&7Key: &f" + party,
                "&7Owner: &f" + name(owner),
                "&7Members: &f" + parties.memberCount(party) + "/" + parties.memberLimit(party),
                "&7Level: &f" + parties.level(party) + " &8| &7XP: &f" + parties.rep(party),
                isFrozen(party) ? "&cFROZEN: &f" + freezeReason(party) : "&aACTIVE"));
        inv.setItem(10, tagged(Material.PLAYER_HEAD, "&fOwner", "noop", "&7" + name(owner)));
        inv.setItem(12, tagged(Material.EXPERIENCE_BOTTLE, "&bProgression", "noop",
                "&7Project: &f" + (project == null ? "none" : project),
                "&7Project progress: &f" + (int) progression.projectProgress(party),
                "&7Relic: &fLv." + parties.relicLevel(party)));
        inv.setItem(14, tagged(Material.WRITABLE_BOOK, "&6Interaction", "noop",
                "&7Recruitment: &f" + interactions.recruitmentMode(party),
                "&7Contracts active: &f" + interactions.activeContractCount(party),
                "&7Contracts completed: &f" + interactions.completedContractCount(party),
                "&7Applications: &f" + interactions.pendingApplicationCount(party)));
        inv.setItem(16, tagged(Material.CHEST, "&7Storage", "noop",
                "&7Backend: &f" + db.activeBackend(),
                "&7Degraded: &f" + db.degraded()));

        if (can(player, "modify")) {
            inv.setItem(19, tagged(isFrozen(party) ? Material.LIME_DYE : Material.ICE,
                    isFrozen(party) ? "&aUnfreeze Party" : "&cFreeze Party", "togglefreeze:" + party + ":" + browserPage,
                    "&7Reversible moderation lock."));
            inv.setItem(21, tagged(Material.MAP, "&eExport Snapshot", "export:" + party + ":" + browserPage,
                    "&7Write a support/export YAML snapshot."));
            inv.setItem(23, tagged(Material.ANVIL, "&aRepair Safe Indexes", "repair:" + party + ":" + browserPage,
                    "&7Repair owner/member/player indexes only."));
        }
        if (can(player, "audit")) inv.setItem(25, tagged(Material.BOOK, "&6Audit Log", "audit", "&7Show recent staff actions in chat."));
        inv.setItem(22, tagged(Material.ARROW, "&eBack", "page:" + browserPage, "&7Back to Admin Browser."));
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAdminClick(InventoryClickEvent event) {
        String title = clean(event.getView().getTitle());
        if (!title.equalsIgnoreCase(BROWSER_TITLE) && !title.equalsIgnoreCase(INSPECT_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        String action = taggedAction(event.getCurrentItem(), GUI_TAG);
        if (action == null || action.equals("noop")) return;
        try {
            String[] a = action.split(":");
            switch (a[0]) {
                case "page" -> openBrowser(player, parseInt(a.length > 1 ? a[1] : "1", 1, 1, 100000));
                case "inspect" -> openInspect(player, a[1], parseInt(a.length > 2 ? a[2] : "1", 1, 1, 100000));
                case "togglefreeze" -> {
                    if (!require(player, "modify")) return;
                    if (isFrozen(a[1])) unfreeze(player, a[1]); else freeze(player, a[1], "Admin GUI");
                    openInspect(player, a[1], parseInt(a.length > 2 ? a[2] : "1", 1, 1, 100000));
                }
                case "export" -> {
                    if (!require(player, "inspect")) return;
                    export(player, a[1], "admin-exports", "EXPORT");
                    openInspect(player, a[1], parseInt(a.length > 2 ? a[2] : "1", 1, 1, 100000));
                }
                case "repair" -> {
                    if (!require(player, "modify")) return;
                    repair(player, a[1]);
                    openInspect(player, a[1], parseInt(a.length > 2 ? a[2] : "1", 1, 1, 100000));
                }
                case "audit" -> { player.closeInventory(); showAudit(player, 1); }
            }
        } catch (Exception failure) {
            plugin.getLogger().warning("Admin GUI action failed: " + action + " -> " + safe(failure));
            player.closeInventory();
            msg(player, "&cAdmin GUI action failed. Re-open /partyadmin.");
        }
    }

    @EventHandler
    public void onAdminDrag(InventoryDragEvent event) {
        String title = clean(event.getView().getTitle());
        if (title.equalsIgnoreCase(BROWSER_TITLE) || title.equalsIgnoreCase(INSPECT_TITLE)) event.setCancelled(true);
    }

    // ---------------------------------------------------------------------
    // Read-only views / utilities
    // ---------------------------------------------------------------------

    private void inspectText(CommandSender sender, String party) {
        UUID owner = parties.owner(party);
        String project = progression.currentProject(party);
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lPARTY ADMIN INSPECT"));
        sender.sendMessage(Util.color("&7Party: &f" + parties.display(party) + " &8(&7key=&f" + party + "&8)"));
        sender.sendMessage(Util.color("&7Owner: &f" + name(owner)));
        sender.sendMessage(Util.color("&7Members: &f" + parties.memberCount(party) + "/" + parties.memberLimit(party)));
        sender.sendMessage(Util.color("&7Level/XP: &f" + parties.level(party) + " / " + parties.rep(party)));
        sender.sendMessage(Util.color("&7Project: &f" + (project == null ? "none" : project) + " &8| &7Progress: &f" + (int) progression.projectProgress(party)));
        sender.sendMessage(Util.color("&7Recruitment: &f" + interactions.recruitmentMode(party) + " &8| &7Applications: &f" + interactions.pendingApplicationCount(party)));
        sender.sendMessage(Util.color("&7Contracts: &f" + interactions.activeContractCount(party) + " active / " + interactions.completedContractCount(party) + " completed"));
        sender.sendMessage(Util.color("&7Frozen: &f" + isFrozen(party) + (isFrozen(party) ? " &8| &7" + freezeReason(party) : "")));
        sender.sendMessage(Util.color("&7Storage: &f" + db.activeBackend() + (db.degraded() ? " &cDEGRADED" : " &aOK")));
    }

    private void listText(CommandSender sender, int requestedPage) {
        List<String> keys = partyKeys();
        keys.sort(Comparator.comparing(parties::display, String.CASE_INSENSITIVE_ORDER));
        int perPage = 10;
        int pages = Math.max(1, (keys.size() + perPage - 1) / perPage);
        int page = Math.max(1, Math.min(pages, requestedPage));
        msg(sender, "&6&lPARTY ADMIN LIST &8- &fPage " + page + "/" + pages);
        int start = (page - 1) * perPage;
        for (int i = start; i < Math.min(keys.size(), start + perPage); i++) {
            String key = keys.get(i);
            sender.sendMessage(Util.color("&7- &f" + parties.display(key) + " &8[&7" + key + "&8] &7Lv.&f" + parties.level(key)
                    + " &8| &7Members &f" + parties.memberCount(key) + (isFrozen(key) ? " &cFROZEN" : "")));
        }
        if (keys.isEmpty()) msg(sender, "&7No Parties found.");
    }

    private List<String> partyKeys() {
        ConfigurationSection root = db.parties.getConfigurationSection("parties");
        return root == null ? new ArrayList<>() : new ArrayList<>(root.getKeys(false));
    }

    private String resolveParty(String input) {
        if (input == null || input.isBlank()) return null;
        String key = Util.key(input);
        if (parties.exists(key)) return key;
        for (String candidate : partyKeys()) if (parties.display(candidate).equalsIgnoreCase(input)) return candidate;
        return null;
    }

    private OfflinePlayer resolvePlayer(String input) {
        if (input == null || input.isBlank()) return null;
        try { return Bukkit.getOfflinePlayer(UUID.fromString(input)); } catch (Exception ignored) {}
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online;
        ConfigurationSection root = db.parties.getConfigurationSection("parties");
        if (root != null) for (String party : root.getKeys(false)) {
            ConfigurationSection members = root.getConfigurationSection(party + ".members");
            if (members == null) continue;
            for (String uuidText : members.getKeys(false)) {
                if (input.equalsIgnoreCase(members.getString(uuidText + ".name", ""))) {
                    try { return Bukkit.getOfflinePlayer(UUID.fromString(uuidText)); } catch (Exception ignored) {}
                }
            }
        }
        OfflinePlayer fallback = Bukkit.getOfflinePlayer(input);
        return fallback.hasPlayedBefore() || fallback.isOnline() ? fallback : null;
    }

    private void clearApplications(UUID player) {
        ConfigurationSection root = db.interactions.getConfigurationSection("applications");
        if (root == null) return;
        for (String party : new ArrayList<>(root.getKeys(false))) db.interactions.set("applications." + party + "." + player, null);
    }

    private boolean can(CommandSender sender, String suffix) {
        return sender.hasPermission("menkiestesparty.admin") || sender.hasPermission("menkiestesparty.admin." + suffix);
    }

    private boolean require(CommandSender sender, String suffix) {
        if (can(sender, suffix)) return true;
        msg(sender, "&cMissing permission: &fmenkiestesparty.admin." + suffix);
        return false;
    }

    private void durable() {
        plugin.saveDataSoon();
        if (!plugin.flushDurable()) plugin.getLogger().severe("Administration mutation could not be durably flushed.");
    }

    private void warLocked(CommandSender sender) {
        msg(sender, "&cParty roster is locked while Party War is PREPARE/ACTIVE. Finish or cancel War first.");
    }

    private void notFound(CommandSender sender) {
        msg(sender, "&cParty/player not found or not known to this server.");
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lMENKIESTES PARTY ADMIN &8- &fv1.6.0"));
        sender.sendMessage(Util.color("&f/partyadmin &7- Admin GUI"));
        sender.sendMessage(Util.color("&f/partyadmin list [page] | inspect <party>"));
        sender.sendMessage(Util.color("&f/partyadmin forcejoin <player> <party> | remove <player>"));
        sender.sendMessage(Util.color("&f/partyadmin transfer <party> <player> | rename <party> <name>"));
        sender.sendMessage(Util.color("&f/partyadmin freeze <party> [reason] | unfreeze <party>"));
        sender.sendMessage(Util.color("&f/partyadmin xp <party> <set|add|remove> <amount>"));
        sender.sendMessage(Util.color("&f/partyadmin repair <party> | resetproject <party> | resetcontract <id>"));
        sender.sendMessage(Util.color("&f/partyadmin export <party> | archive <party> | disband <party>"));
        sender.sendMessage(Util.color("&f/partyadmin audit [page] | confirm | cancel"));
        sender.sendMessage(Util.color("&7Nested alias: &f/party admin ..."));
    }

    private ItemStack tagged(Material material, String name, String action, String... lore) {
        String[] copy = Arrays.copyOf(lore, lore.length + 1);
        copy[lore.length] = "&0" + GUI_TAG + action;
        return Util.item(material, name, copy);
    }

    private String taggedAction(ItemStack item, String tag) {
        if (item == null || item.getType() == Material.AIR) return null;
        var meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return null;
        for (String line : meta.getLore()) {
            String clean = Util.strip(line);
            if (clean != null && clean.startsWith(tag)) return clean.substring(tag.length());
        }
        return null;
    }

    private String actor(CommandSender sender) {
        return sender instanceof Player player ? player.getName() + "(" + player.getUniqueId() + ")" : sender.getName();
    }

    private String actorKey(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "console:" + sender.getName().toLowerCase(Locale.ROOT);
    }

    private String playerName(OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString().substring(0, 8) : name;
    }

    private String name(UUID uuid) {
        if (uuid == null) return "INVALID";
        return playerName(Bukkit.getOfflinePlayer(uuid));
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(parties.prefix() + Util.color(" " + text));
    }

    private static String clean(String value) {
        String clean = Util.strip(value);
        return clean == null ? "" : clean;
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String safe = value.replace('\n', ' ').replace('\r', ' ').trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private static String safe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(value))); }
        catch (Exception ignored) { return fallback; }
    }

    private static String formatTime(long millis) {
        return millis <= 0L ? "never" : DISPLAY_TIME.format(Instant.ofEpochMilli(millis));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!enabled()) return List.of();
        if (args.length == 1) {
            return filter(List.of("help", "list", "inspect", "forcejoin", "remove", "transfer", "rename", "freeze", "unfreeze",
                    "xp", "repair", "resetproject", "resetcontract", "export", "archive", "disband", "audit", "confirm", "cancel"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && Set.of("inspect", "rename", "freeze", "unfreeze", "xp", "repair", "resetproject", "export", "archive", "disband").contains(sub)) {
            return filter(partyKeys(), args[1]);
        }
        if (args.length == 2 && Set.of("forcejoin", "remove").contains(sub)) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList(), args[1]);
        }
        if (args.length == 2 && sub.equals("transfer")) return filter(partyKeys(), args[1]);
        if (args.length == 3 && sub.equals("transfer")) {
            String party = resolveParty(args[1]);
            if (party == null) return List.of();
            return filter(parties.members(party).stream().map(this::name).toList(), args[2]);
        }
        if (args.length == 3 && sub.equals("forcejoin")) return filter(partyKeys(), args[2]);
        if (args.length == 3 && (sub.equals("xp") || sub.equals("rep"))) return filter(List.of("set", "add", "remove"), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> values, String typed) {
        String q = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(q)).distinct().limit(100).toList();
    }
}
