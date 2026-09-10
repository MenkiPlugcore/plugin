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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * v1.6.1 stability/safety wrapper for AdministrationManager.
 *
 * This layer deliberately reuses the v1.6.0 mutation implementation after
 * performing stronger confirmation, archive and post-condition checks. It is
 * registered before the legacy administration listener and becomes the direct
 * /partyadmin executor, while non-v1.6.1 commands delegate unchanged.
 */
public final class AdministrationStabilityManager implements Listener, CommandExecutor, TabCompleter {
    private static final String ADMIN_GUI_TAG = "MENKI_ADMIN:";
    private static final DateTimeFormatter SNAPSHOT_STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());

    private record SnapshotResult(Path path, Path checksumPath, String sha256) {}
    private record IntegrityReport(List<String> warnings) {
        int count() { return warnings.size(); }
        boolean healthy() { return warnings.isEmpty(); }
    }

    private final MENKIESTESPartyPlugin plugin;
    private final AdministrationManager legacy;
    private final PartyService parties;
    private final ProgressionManager progression;
    private final InteractionManager interactions;
    private final StorageBundle db;
    private final PluginCommand partyAdminCommand;
    private final AdminConfirmationGate confirmations = new AdminConfirmationGate();

    public AdministrationStabilityManager(MENKIESTESPartyPlugin plugin,
                                          AdministrationManager legacy,
                                          PartyService parties,
                                          ProgressionManager progression,
                                          InteractionManager interactions,
                                          StorageBundle db) {
        this.plugin = plugin;
        this.legacy = legacy;
        this.parties = parties;
        this.progression = progression;
        this.interactions = interactions;
        this.db = db;
        this.partyAdminCommand = plugin.getCommand("partyadmin");
        if (partyAdminCommand != null) {
            partyAdminCommand.setExecutor(this);
            partyAdminCommand.setTabCompleter(this);
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("administration-stability.enabled", true);
    }

    public int pendingConfirmations() {
        return confirmations.size();
    }

    public void clearAll() {
        confirmations.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (enabled() && handleSpecial(sender, args)) return true;
        return legacy.onCommand(sender, command, label, args);
    }

    /**
     * Intercepts direct aliases and nested /party admin routes before the v1.6.0
     * listener. Returning true means the event must be cancelled.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAdminPreprocess(PlayerCommandPreprocessEvent event) {
        if (!enabled()) return;
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] split = raw.substring(1).trim().split("\\s+");
        if (split.length == 0) return;

        String root = root(split[0]);
        String[] args = null;
        if (Set.of("partyadmin", "padmin", "mpartyadmin").contains(root)) {
            args = Arrays.copyOfRange(split, 1, split.length);
        } else if (Set.of("party", "p", "parties").contains(root)
                && split.length >= 2 && split[1].equalsIgnoreCase("admin")) {
            args = Arrays.copyOfRange(split, 2, split.length);
        }
        if (args != null && handleSpecial(event.getPlayer(), args)) event.setCancelled(true);
    }

    /** Protect v1.6.0 Admin GUI export/repair buttons with the v1.6.1 implementations. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAdminGuiSafety(InventoryClickEvent event) {
        if (!enabled() || !(event.getWhoClicked() instanceof Player player)) return;
        String action = taggedAction(event.getCurrentItem());
        if (action == null) return;
        String[] parts = action.split(":");
        if (parts.length < 2) return;
        if (parts[0].equals("export")) {
            event.setCancelled(true);
            safeExport(player, parts[1], "admin-exports", "EXPORT");
            legacy.openInspect(player, parts[1], parts.length > 2 ? parseInt(parts[2], 1) : 1);
        } else if (parts[0].equals("repair")) {
            event.setCancelled(true);
            verifiedRepair(player, parts[1]);
            legacy.openInspect(player, parts[1], parts.length > 2 ? parseInt(parts[2], 1) : 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStaffQuit(PlayerQuitEvent event) {
        AdminConfirmationGate.Ticket removed = confirmations.cancel(actorKey(event.getPlayer()));
        if (removed != null) {
            audit(event.getPlayer(), "CONFIRM_LOGOUT_CANCEL", removed.targetKey(), "CANCELLED",
                    "action=" + removed.actionKey());
        }
    }

    private boolean handleSpecial(CommandSender sender, String[] args) {
        if (args.length == 0) return false;
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> {
                usage(sender);
                return true;
            }
            case "health" -> {
                if (!require(sender, "inspect")) return true;
                health(sender);
                return true;
            }
            case "search" -> {
                if (!require(sender, "inspect")) return true;
                if (args.length < 2) msg(sender, "&c/partyadmin search <query> [page]");
                else search(sender, args[1], args.length > 2 ? parseInt(args[2], 1) : 1);
                return true;
            }
            case "inspect" -> {
                if (args.length >= 3 && args[2].equalsIgnoreCase("verbose")) {
                    if (!require(sender, "inspect")) return true;
                    String party = resolveParty(args[1]);
                    if (party == null) failedRead(sender, "INSPECT_VERBOSE", args[1]);
                    else inspectVerbose(sender, party);
                    return true;
                }
                return false;
            }
            case "export" -> {
                if (!require(sender, "inspect")) return true;
                if (args.length < 2) msg(sender, "&c/partyadmin export <party>");
                else safeExport(sender, args[1], "admin-exports", "EXPORT");
                return true;
            }
            case "archive" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 2) msg(sender, "&c/partyadmin archive <party>");
                else safeExport(sender, args[1], "archives", "ARCHIVE");
                return true;
            }
            case "repair" -> {
                if (!require(sender, "modify")) return true;
                if (args.length < 2) msg(sender, "&c/partyadmin repair <party>");
                else verifiedRepair(sender, args[1]);
                return true;
            }
            case "transfer", "resetproject", "resetcontract", "disband" -> {
                if (!require(sender, "dangerous")) return true;
                stageDangerous(sender, args);
                return true;
            }
            case "confirm" -> {
                confirm(sender, args.length > 1 ? args[1] : "");
                return true;
            }
            case "cancel" -> {
                cancel(sender);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Confirmation hardening
    // ---------------------------------------------------------------------

    private void stageDangerous(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        String actionKey;
        String targetKey;
        String description;

        switch (sub) {
            case "transfer" -> {
                if (args.length < 3) { msg(sender, "&c/partyadmin transfer <party> <player>"); return; }
                String party = resolveParty(args[1]);
                OfflinePlayer target = resolvePlayer(args[2]);
                if (party == null || target == null) {
                    failedMutation(sender, "TRANSFER_OWNER", party == null ? args[1] : party, "party/player not found");
                    return;
                }
                if (plugin.war().membershipLocked()) {
                    failedMutation(sender, "TRANSFER_OWNER", party, "Party War roster lock active");
                    return;
                }
                if (!party.equals(parties.partyOf(target.getUniqueId()))) {
                    failedMutation(sender, "TRANSFER_OWNER", party, "target is not a Party member");
                    return;
                }
                if (target.getUniqueId().equals(parties.owner(party))) {
                    failedMutation(sender, "TRANSFER_OWNER", party, "target is already owner");
                    return;
                }
                actionKey = "TRANSFER_OWNER";
                targetKey = party;
                description = "Transfer owner " + parties.display(party) + " -> " + playerName(target);
            }
            case "resetproject" -> {
                if (args.length < 2) { msg(sender, "&c/partyadmin resetproject <party>"); return; }
                String party = resolveParty(args[1]);
                if (party == null) { failedMutation(sender, "RESET_PROJECT", args[1], "Party not found"); return; }
                String project = progression.currentProject(party);
                if (project == null) { failedMutation(sender, "RESET_PROJECT", party, "no active project"); return; }
                actionKey = "RESET_PROJECT";
                targetKey = party;
                description = "Reset active project " + project + " for " + parties.display(party);
            }
            case "resetcontract" -> {
                if (args.length < 2) { msg(sender, "&c/partyadmin resetcontract <id>"); return; }
                String id = args[1].toUpperCase(Locale.ROOT);
                String base = "contracts." + id;
                if (!db.interactions.contains(base + ".status")) {
                    failedMutation(sender, "RESET_CONTRACT", id, "Contract not found"); return;
                }
                String status = db.interactions.getString(base + ".status", "UNKNOWN").toUpperCase(Locale.ROOT);
                if (!status.equals("ACTIVE")) {
                    failedMutation(sender, "RESET_CONTRACT", id, "Contract status is " + status); return;
                }
                actionKey = "RESET_CONTRACT";
                targetKey = id;
                description = "Reset ACTIVE contract progress " + id;
            }
            case "disband" -> {
                if (args.length < 2) { msg(sender, "&c/partyadmin disband <party>"); return; }
                String party = resolveParty(args[1]);
                if (party == null) { failedMutation(sender, "DISBAND", args[1], "Party not found"); return; }
                if (plugin.war().membershipLocked()) {
                    failedMutation(sender, "DISBAND", party, "Party War roster lock active"); return;
                }
                actionKey = "DISBAND";
                targetKey = party;
                description = "Verified archive then permanently disband " + parties.display(party);
            }
            default -> { return; }
        }

        long now = System.currentTimeMillis();
        int seconds = Math.max(5, Math.min(120, administrationInt("confirmation-seconds", 30)));
        AdminConfirmationGate.Ticket replaced = confirmations.peek(actorKey(sender));
        AdminConfirmationGate.Ticket ticket = confirmations.create(actorKey(sender), actionKey, targetKey,
                description, args, now, seconds * 1000L);
        if (replaced != null) {
            audit(sender, "CONFIRM_REPLACED", replaced.targetKey(), "CANCELLED",
                    "old-action=" + replaced.actionKey());
        }
        audit(sender, "CONFIRM_REQUEST", targetKey, "PENDING",
                "action=" + actionKey + " expires=" + seconds + "s");
        msg(sender, "&ePending dangerous action: &f" + description);
        msg(sender, "&7Confirmation token: &f&l" + ticket.token());
        msg(sender, "&7Run &f/partyadmin confirm " + ticket.token() + " &7within &f" + seconds + "s&7.");
    }

    private void confirm(CommandSender sender, String providedToken) {
        if (!require(sender, "dangerous")) return;
        boolean requireToken = administrationBoolean("confirmation.require-token", true);
        int maxAttempts = Math.max(1, Math.min(10, administrationInt("confirmation.max-attempts", 3)));
        AdminConfirmationGate.ConsumeResult result = confirmations.consume(
                actorKey(sender), providedToken, System.currentTimeMillis(), requireToken, maxAttempts);
        AdminConfirmationGate.Ticket ticket = result.ticket();

        switch (result.status()) {
            case MISSING -> { msg(sender, "&eNo pending dangerous action."); return; }
            case TOKEN_REQUIRED -> {
                msg(sender, "&cConfirmation token required. &7Use &f/partyadmin confirm " + ticket.token());
                return;
            }
            case TOKEN_MISMATCH -> {
                audit(sender, "CONFIRM_TOKEN", ticket.targetKey(), "FAILED",
                        "action=" + ticket.actionKey() + " wrong-token attempt=" + result.failedAttempts());
                msg(sender, "&cWrong confirmation token. Attempts: &f" + result.failedAttempts() + "/" + maxAttempts);
                return;
            }
            case TOO_MANY_ATTEMPTS -> {
                audit(sender, "CONFIRM_TOKEN", ticket.targetKey(), "FAILED",
                        "action=" + ticket.actionKey() + " max attempts reached");
                msg(sender, "&cToo many wrong confirmation attempts. Pending action cancelled.");
                return;
            }
            case EXPIRED -> {
                audit(sender, "CONFIRM_EXPIRED", ticket.targetKey(), "FAILED", "action=" + ticket.actionKey());
                msg(sender, "&cConfirmation expired. Run the original command again.");
                return;
            }
            case ALREADY_CONSUMED -> {
                audit(sender, "CONFIRM_REPLAY", ticket.targetKey(), "FAILED", "action=" + ticket.actionKey());
                msg(sender, "&cConfirmation was already consumed. Action was not executed twice.");
                return;
            }
            case ACCEPTED -> { /* execute below */ }
        }

        SnapshotResult preDisband = null;
        if (ticket.actionKey().equals("DISBAND")) {
            try {
                preDisband = writeVerifiedSnapshot(sender, ticket.targetKey(), "archives", "pre-disband-v161");
            } catch (Exception failure) {
                audit(sender, "DISBAND_ARCHIVE_VERIFY", ticket.targetKey(), "FAILED", safe(failure));
                msg(sender, "&cDisband aborted: verified safety archive could not be created. &f" + safe(failure));
                return;
            }
        }

        try {
            // Reuse the hardened v1.6.0 mutation implementation. The first
            // call creates its internal one-shot pending action; the second
            // executes it immediately inside this already-consumed v1.6.1 gate.
            delegate(sender, ticket.commandArgs());
            delegate(sender, new String[]{"confirm"});
            boolean success = postCondition(ticket);
            String detail = "action=" + ticket.actionKey();
            if (preDisband != null) detail += " safety-archive=" + preDisband.path().getFileName()
                    + " sha256=" + preDisband.sha256();
            audit(sender, "DANGEROUS_EXECUTE", ticket.targetKey(), success ? "SUCCESS" : "FAILED", detail);
            if (!success) msg(sender, "&cPost-condition verification failed. Review /partyadmin audit and server data.");
        } catch (RuntimeException failure) {
            audit(sender, "DANGEROUS_EXECUTE", ticket.targetKey(), "FAILED",
                    "action=" + ticket.actionKey() + " exception=" + safe(failure));
            plugin.getLogger().severe("v1.6.1 admin action failed: " + ticket.description() + " -> " + safe(failure));
            msg(sender, "&cAdmin action failed. Check console and audit log.");
        }
    }

    private boolean postCondition(AdminConfirmationGate.Ticket ticket) {
        String[] args = ticket.commandArgs();
        return switch (ticket.actionKey()) {
            case "TRANSFER_OWNER" -> {
                if (args.length < 3 || !parties.exists(ticket.targetKey())) yield false;
                OfflinePlayer target = resolvePlayer(args[2]);
                yield target != null && target.getUniqueId().equals(parties.owner(ticket.targetKey()));
            }
            case "RESET_PROJECT" -> parties.exists(ticket.targetKey())
                    && progression.currentProject(ticket.targetKey()) != null
                    && progression.projectProgress(ticket.targetKey()) <= 0.0001D;
            case "RESET_CONTRACT" -> "ACTIVE".equalsIgnoreCase(db.interactions.getString(
                    "contracts." + ticket.targetKey() + ".status", ""))
                    && db.interactions.getInt("contracts." + ticket.targetKey() + ".progress", -1) == 0;
            case "DISBAND" -> !parties.exists(ticket.targetKey());
            default -> false;
        };
    }

    private void cancel(CommandSender sender) {
        AdminConfirmationGate.Ticket ticket = confirmations.cancel(actorKey(sender));
        if (ticket == null) { msg(sender, "&eNo pending v1.6.1 action."); return; }
        audit(sender, "CONFIRM_CANCEL", ticket.targetKey(), "CANCELLED", "action=" + ticket.actionKey());
        msg(sender, "&aPending action cancelled.");
    }

    // ---------------------------------------------------------------------
    // Verified export/archive
    // ---------------------------------------------------------------------

    private void safeExport(CommandSender sender, String input, String directory, String action) {
        String party = resolveParty(input);
        if (party == null) {
            failedMutation(sender, action, input, "Party not found");
            return;
        }
        try {
            SnapshotResult result = writeVerifiedSnapshot(sender, party, directory, action.toLowerCase(Locale.ROOT));
            audit(sender, action, party, "SUCCESS",
                    result.path().toAbsolutePath() + " sha256=" + result.sha256());
            msg(sender, "&a" + action + " verified: &f" + result.path().toAbsolutePath());
            msg(sender, "&7SHA-256: &f" + result.sha256());
        } catch (Exception failure) {
            audit(sender, action, party, "FAILED", safe(failure));
            msg(sender, "&c" + action + " failed verification: &f" + safe(failure));
        }
    }

    private SnapshotResult writeVerifiedSnapshot(CommandSender sender, String party,
                                                 String directory, String purpose) throws IOException {
        if (!parties.exists(party)) throw new IOException("Party not found");
        Path dir = plugin.getDataFolder().toPath().resolve(directory);
        String stem = party + "-" + SNAPSHOT_STAMP.format(Instant.now()) + "-" + purpose;
        Path path = AdminArchiveIntegrity.uniquePath(dir, stem, ".yml");
        Path sidecar = null;
        try {
            YamlConfiguration out = new YamlConfiguration();
            out.set("meta.plugin-version", plugin.getDescription().getVersion());
            out.set("meta.format", "MENKIESTESParty-admin-snapshot-v1");
            out.set("meta.purpose", purpose);
            out.set("meta.exported-at", System.currentTimeMillis());
            out.set("meta.exported-by", actor(sender));
            out.set("meta.party-key", party);
            out.set("meta.party-display", parties.display(party));
            UUID owner = parties.owner(party);
            out.set("meta.expected-owner", owner == null ? null : owner.toString());
            out.set("meta.expected-members", parties.memberCount(party));

            copySection(db.parties.getConfigurationSection("parties." + party), out, "party");
            for (UUID member : parties.members(party)) {
                out.set("player-index." + member + ".party", db.parties.getString("players." + member + ".party"));
            }

            ConfigurationSection contracts = db.interactions.getConfigurationSection("contracts");
            if (contracts != null) for (String id : contracts.getKeys(false)) {
                String base = "contracts." + id;
                if (party.equals(db.interactions.getString(base + ".source"))
                        || party.equals(db.interactions.getString(base + ".target"))) {
                    copySection(contracts.getConfigurationSection(id), out, "interactions.contracts." + id);
                }
            }
            copySection(db.interactions.getConfigurationSection("applications." + party), out,
                    "interactions.applications." + party);

            ConfigurationSection pairs = db.interactions.getConfigurationSection("diplomacy.pairs");
            if (pairs != null) for (String key : pairs.getKeys(false)) {
                String base = "diplomacy.pairs." + key;
                if (party.equals(db.interactions.getString(base + ".a"))
                        || party.equals(db.interactions.getString(base + ".b"))) {
                    copySection(pairs.getConfigurationSection(key), out, "interactions.diplomacy.pairs." + key);
                }
            }

            ConfigurationSection audit = db.interactions.getConfigurationSection("moderation.audit");
            if (audit != null) for (String id : audit.getKeys(false)) {
                if (party.equals(audit.getString(id + ".party"))) {
                    copySection(audit.getConfigurationSection(id), out, "moderation-audit." + id);
                }
            }

            out.save(path.toFile());
            verifyYamlSnapshot(path, party, owner, parties.memberCount(party));
            String digest = AdminArchiveIntegrity.sha256(path);
            sidecar = AdminArchiveIntegrity.writeChecksumSidecar(path, digest);
            if (!AdminArchiveIntegrity.checksumMatches(path, sidecar)) {
                throw new IOException("checksum sidecar verification failed");
            }
            return new SnapshotResult(path, sidecar, digest);
        } catch (Exception failure) {
            if (sidecar != null) Files.deleteIfExists(sidecar);
            Files.deleteIfExists(path);
            if (failure instanceof IOException io) throw io;
            throw new IOException(safe(failure), failure);
        }
    }

    private void verifyYamlSnapshot(Path path, String party, UUID expectedOwner, int expectedMembers) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) < 64L) throw new IOException("snapshot file missing/empty");
        YamlConfiguration check = YamlConfiguration.loadConfiguration(path.toFile());
        if (!party.equals(check.getString("meta.party-key"))) throw new IOException("party-key verification failed");
        if (!"MENKIESTESParty-admin-snapshot-v1".equals(check.getString("meta.format"))) {
            throw new IOException("snapshot format marker missing");
        }
        String owner = check.getString("party.owner");
        if (expectedOwner != null && !expectedOwner.toString().equals(owner)) throw new IOException("owner verification failed");
        ConfigurationSection members = check.getConfigurationSection("party.members");
        int count = members == null ? 0 : members.getKeys(false).size();
        if (count != expectedMembers) throw new IOException("member-count verification failed: " + count + " != " + expectedMembers);
    }

    private static void copySection(ConfigurationSection source, YamlConfiguration target, String root) {
        if (source == null) return;
        for (String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) target.set(root + "." + key, source.get(key));
        }
    }

    // ---------------------------------------------------------------------
    // Health, search and verified repair
    // ---------------------------------------------------------------------

    private void health(CommandSender sender) {
        List<String> keys = partyKeys();
        int frozen = 0;
        for (String party : keys) if (legacy.isFrozen(party)) frozen++;
        int auditEntries = sectionSize(db.interactions.getConfigurationSection("moderation.audit"));
        int integrityWarnings = totalIntegrityWarnings(keys);
        boolean archiveOk = directoryHealthy(plugin.getDataFolder().toPath().resolve("archives"));

        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lADMINISTRATION HEALTH &8- &fv1.6.1"));
        sender.sendMessage(Util.color("&7Parties: &f" + keys.size() + " &8| &7Frozen: &f" + frozen));
        sender.sendMessage(Util.color("&7Pending confirmations: &f" + confirmations.size()));
        sender.sendMessage(Util.color("&7Audit entries: &f" + auditEntries));
        sender.sendMessage(Util.color("&7Archive directory: " + (archiveOk ? "&aOK" : "&cNOT WRITABLE")));
        sender.sendMessage(Util.color("&7Storage: &f" + db.activeBackend() + (db.degraded() ? " &cDEGRADED" : " &aHEALTHY")));
        sender.sendMessage(Util.color("&7Storage schema: &f" + db.schemaVersion() + " &8| &7Unclean recovery: &f" + db.uncleanShutdownDetected()));
        sender.sendMessage(Util.color("&7Developer API: &f" + plugin.apiHardening().healthLabel()));
        sender.sendMessage(Util.color("&7Integrity warnings: " + (integrityWarnings == 0 ? "&a0" : "&c" + integrityWarnings)));
    }

    private void inspectVerbose(CommandSender sender, String party) {
        IntegrityReport report = analyzeParty(party);
        UUID owner = parties.owner(party);
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lPARTY ADMIN VERBOSE &8- &f" + parties.display(party)));
        sender.sendMessage(Util.color("&7Internal key: &f" + party));
        sender.sendMessage(Util.color("&7Owner: &f" + name(owner)));
        sender.sendMessage(Util.color("&7Owner index: " + (owner != null && party.equals(parties.partyOf(owner)) ? "&aOK" : "&cBROKEN")));
        sender.sendMessage(Util.color("&7Member indexes: &f" + memberIndexErrors(party) + " mismatch"));
        sender.sendMessage(Util.color("&7Applications: &f" + interactions.pendingApplicationCount(party)));
        sender.sendMessage(Util.color("&7Contracts: &f" + interactions.activeContractCount(party) + " active / " + interactions.completedContractCount(party) + " completed"));
        sender.sendMessage(Util.color("&7Diplomacy pairs: &f" + diplomacyPairCount(party)));
        sender.sendMessage(Util.color("&7Active project: &f" + value(progression.currentProject(party), "none")
                + " &8| &7progress=&f" + (int) progression.projectProgress(party)));
        sender.sendMessage(Util.color("&7Frozen: &f" + legacy.isFrozen(party)));
        sender.sendMessage(Util.color("&7Storage: &f" + db.activeBackend() + (db.degraded() ? " &cDEGRADED" : " &aSYNCED")));
        sender.sendMessage(Util.color("&7Integrity: " + (report.healthy() ? "&aOK" : "&c" + report.count() + " warning(s)")));
        for (String warning : report.warnings().stream().limit(8).toList()) {
            sender.sendMessage(Util.color("&c- &7" + warning));
        }
    }

    private void search(CommandSender sender, String query, int requestedPage) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> matches = partyKeys().stream()
                .filter(key -> key.toLowerCase(Locale.ROOT).contains(q)
                        || parties.display(key).toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(parties::display, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int perPage = 10;
        int pages = Math.max(1, (matches.size() + perPage - 1) / perPage);
        int page = Math.max(1, Math.min(pages, requestedPage));
        msg(sender, "&6&lPARTY SEARCH &8- &f'" + trim(query, 32) + "' &8- &f" + matches.size() + " result(s), page " + page + "/" + pages);
        int start = (page - 1) * perPage;
        for (int i = start; i < Math.min(matches.size(), start + perPage); i++) {
            String party = matches.get(i);
            sender.sendMessage(Util.color("&7- &f" + parties.display(party) + " &8[&7" + party + "&8]"
                    + " &7Lv.&f" + parties.level(party) + " &8| &7Members &f" + parties.memberCount(party)
                    + (legacy.isFrozen(party) ? " &cFROZEN" : "")));
        }
        if (matches.isEmpty()) msg(sender, "&7No Party matched that query.");
    }

    private void verifiedRepair(CommandSender sender, String input) {
        String party = resolveParty(input);
        if (party == null) { failedMutation(sender, "REPAIR_VERIFY", input, "Party not found"); return; }
        IntegrityReport before = analyzeParty(party);
        delegate(sender, new String[]{"repair", party});
        IntegrityReport after = analyzeParty(party);
        String result = after.count() <= before.count() ? (after.healthy() ? "SUCCESS" : "PARTIAL") : "FAILED";
        audit(sender, "REPAIR_VERIFY", party, result,
                "warnings-before=" + before.count() + " warnings-after=" + after.count());
        msg(sender, "&7Repair verification: &f" + before.count() + " warning(s) -> " + after.count()
                + (after.healthy() ? " &aHEALTHY" : " &eREVIEW"));
        for (String warning : after.warnings().stream().limit(5).toList()) msg(sender, "&eRemaining: &7" + warning);
    }

    private IntegrityReport analyzeParty(String party) {
        List<String> warnings = new ArrayList<>();
        if (!parties.exists(party)) {
            warnings.add("Party record missing");
            return new IntegrityReport(List.copyOf(warnings));
        }
        UUID owner = parties.owner(party);
        Set<UUID> members = new LinkedHashSet<>(parties.members(party));
        if (owner == null) warnings.add("Owner UUID missing/invalid");
        else if (!members.contains(owner)) warnings.add("Owner missing from member roster");

        for (UUID member : members) {
            if (!party.equals(parties.partyOf(member))) warnings.add("Player index mismatch: " + name(member));
            String role = db.parties.getString("parties." + party + ".members." + member + ".role", "MEMBER");
            if (owner != null && member.equals(owner) && !role.equalsIgnoreCase("OWNER")) {
                warnings.add("Owner role field is " + role);
            } else if ((owner == null || !member.equals(owner)) && role.equalsIgnoreCase("OWNER")) {
                warnings.add("Non-owner member has OWNER role: " + name(member));
            }
        }

        ConfigurationSection indexes = db.parties.getConfigurationSection("players");
        if (indexes != null) for (String uuidText : indexes.getKeys(false)) {
            if (!party.equals(indexes.getString(uuidText + ".party"))) continue;
            try {
                UUID uuid = UUID.fromString(uuidText);
                if (!members.contains(uuid)) warnings.add("Player index has non-member: " + uuidText);
            } catch (Exception invalid) {
                warnings.add("Invalid UUID in player index: " + uuidText);
            }
        }
        return new IntegrityReport(List.copyOf(warnings));
    }

    private int totalIntegrityWarnings(List<String> partiesList) {
        int warnings = 0;
        Set<String> valid = new LinkedHashSet<>(partiesList);
        for (String party : partiesList) {
            UUID owner = parties.owner(party);
            Set<UUID> members = new LinkedHashSet<>(parties.members(party));
            if (owner == null || !members.contains(owner)) warnings++;
            for (UUID member : members) {
                if (!party.equals(parties.partyOf(member))) warnings++;
                String role = db.parties.getString("parties." + party + ".members." + member + ".role", "MEMBER");
                if (owner != null && member.equals(owner) && !role.equalsIgnoreCase("OWNER")) warnings++;
                if ((owner == null || !member.equals(owner)) && role.equalsIgnoreCase("OWNER")) warnings++;
            }
        }
        ConfigurationSection indexes = db.parties.getConfigurationSection("players");
        if (indexes != null) for (String uuidText : indexes.getKeys(false)) {
            String party = indexes.getString(uuidText + ".party");
            if (party == null) continue;
            if (!valid.contains(party)) { warnings++; continue; }
            try {
                UUID uuid = UUID.fromString(uuidText);
                if (!parties.members(party).contains(uuid)) warnings++;
            } catch (Exception invalid) { warnings++; }
        }
        return warnings;
    }

    private int memberIndexErrors(String party) {
        int errors = 0;
        for (UUID member : parties.members(party)) if (!party.equals(parties.partyOf(member))) errors++;
        return errors;
    }

    private int diplomacyPairCount(String party) {
        int count = 0;
        ConfigurationSection pairs = db.interactions.getConfigurationSection("diplomacy.pairs");
        if (pairs == null) return 0;
        for (String key : pairs.getKeys(false)) {
            String base = "diplomacy.pairs." + key;
            if (party.equals(db.interactions.getString(base + ".a"))
                    || party.equals(db.interactions.getString(base + ".b"))) count++;
        }
        return count;
    }

    // ---------------------------------------------------------------------
    // Audit and utilities
    // ---------------------------------------------------------------------

    private void audit(CommandSender sender, String action, String party, String result, String detail) {
        long seq = db.interactions.getLong("moderation.audit-seq", 0L) + 1L;
        db.interactions.set("moderation.audit-seq", seq);
        String id = String.format(Locale.ROOT, "%012d", seq);
        String base = "moderation.audit." + id;
        db.interactions.set(base + ".at", System.currentTimeMillis());
        db.interactions.set(base + ".actor", actor(sender));
        db.interactions.set(base + ".action", action);
        db.interactions.set(base + ".party", party == null ? "" : trim(party, 80));
        db.interactions.set(base + ".result", result);
        db.interactions.set(base + ".detail", trim("result=" + result + " " + detail, 500));
        pruneAudit();
        plugin.saveDataSoon();
        if (!plugin.flushDurable()) plugin.getLogger().warning("v1.6.1 audit entry could not be durably flushed.");
    }

    private void pruneAudit() {
        ConfigurationSection section = db.interactions.getConfigurationSection("moderation.audit");
        if (section == null) return;
        int max = Math.max(50, administrationInt("audit.max-entries", 500));
        int days = Math.max(1, administrationInt("audit.retention-days", 90));
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort(String::compareTo);
        for (String key : new ArrayList<>(keys)) {
            if (section.getLong(key + ".at", 0L) < cutoff) {
                db.interactions.set("moderation.audit." + key, null);
                keys.remove(key);
            }
        }
        while (keys.size() > max) db.interactions.set("moderation.audit." + keys.remove(0), null);
    }

    private void failedMutation(CommandSender sender, String action, String target, String reason) {
        audit(sender, action, target, "FAILED", reason);
        msg(sender, "&cAction rejected: &f" + reason);
    }

    private void failedRead(CommandSender sender, String action, String target) {
        msg(sender, "&cParty not found: &f" + target);
    }

    private void delegate(CommandSender sender, String[] args) {
        if (partyAdminCommand == null) throw new IllegalStateException("partyadmin command missing from plugin.yml");
        legacy.onCommand(sender, partyAdminCommand, "partyadmin", args);
    }

    private boolean require(CommandSender sender, String suffix) {
        if (sender.hasPermission("menkiestesparty.admin") || sender.hasPermission("menkiestesparty.admin." + suffix)) return true;
        msg(sender, "&cMissing permission: &fmenkiestesparty.admin." + suffix);
        return false;
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

    private List<String> partyKeys() {
        ConfigurationSection root = db.parties.getConfigurationSection("parties");
        return root == null ? new ArrayList<>() : new ArrayList<>(root.getKeys(false));
    }

    private String taggedAction(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        var meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return null;
        for (String line : meta.getLore()) {
            String clean = Util.strip(line);
            if (clean != null && clean.startsWith(ADMIN_GUI_TAG)) return clean.substring(ADMIN_GUI_TAG.length());
        }
        return null;
    }

    private boolean directoryHealthy(Path directory) {
        try {
            Files.createDirectories(directory);
            return Files.isDirectory(directory) && Files.isWritable(directory);
        } catch (Exception ignored) { return false; }
    }

    private int sectionSize(ConfigurationSection section) {
        return section == null ? 0 : section.getKeys(false).size();
    }

    private String actor(CommandSender sender) {
        return sender instanceof Player player ? player.getName() + "(" + player.getUniqueId() + ")" : sender.getName();
    }

    private String actorKey(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString()
                : "console:" + sender.getName().toLowerCase(Locale.ROOT);
    }

    private String playerName(OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString().substring(0, 8) : name;
    }

    private String name(UUID uuid) {
        return uuid == null ? "INVALID" : playerName(Bukkit.getOfflinePlayer(uuid));
    }

    private int administrationInt(String path, int fallback) {
        Path file = plugin.getDataFolder().toPath().resolve("administration.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        return config.getInt("administration." + path, fallback);
    }

    private boolean administrationBoolean(String path, boolean fallback) {
        Path file = plugin.getDataFolder().toPath().resolve("administration.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        return config.getBoolean("administration." + path, fallback);
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lMENKIESTES PARTY ADMIN &8- &fv1.6.1"));
        sender.sendMessage(Util.color("&f/partyadmin &7- Admin GUI"));
        sender.sendMessage(Util.color("&f/partyadmin list [page] | search <query> [page]"));
        sender.sendMessage(Util.color("&f/partyadmin inspect <party> [verbose] | health"));
        sender.sendMessage(Util.color("&f/partyadmin forcejoin <player> <party> | remove <player>"));
        sender.sendMessage(Util.color("&f/partyadmin transfer <party> <player> | rename <party> <name>"));
        sender.sendMessage(Util.color("&f/partyadmin freeze <party> [reason] | unfreeze <party>"));
        sender.sendMessage(Util.color("&f/partyadmin xp <party> <set|add|remove> <amount>"));
        sender.sendMessage(Util.color("&f/partyadmin repair <party> | resetproject <party> | resetcontract <id>"));
        sender.sendMessage(Util.color("&f/partyadmin export <party> | archive <party> | disband <party>"));
        sender.sendMessage(Util.color("&f/partyadmin audit [page] | confirm <token> | cancel"));
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(parties.prefix() + Util.color(" " + text));
    }

    private static String root(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        return colon >= 0 ? lower.substring(colon + 1) : lower;
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

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int parseInt(String value, int fallback) {
        try { return Math.max(1, Integer.parseInt(value)); }
        catch (Exception ignored) { return fallback; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (enabled()) {
            if (args.length == 1) {
                List<String> values = new ArrayList<>(legacy.onTabComplete(sender, command, alias, args));
                values.add("health");
                values.add("search");
                return filter(values, args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
                AdminConfirmationGate.Ticket ticket = confirmations.peek(actorKey(sender));
                return ticket == null ? List.of() : filter(List.of(ticket.token()), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("inspect")) {
                return filter(List.of("verbose"), args[2]);
            }
        }
        return legacy.onTabComplete(sender, command, alias, args);
    }

    private static List<String> filter(List<String> values, String typed) {
        String q = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(q)).distinct().limit(100).toList();
    }
}
