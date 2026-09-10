package id.cadera.menkiestesparty;

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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
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
 * MENKIESTESParty v1.6.2 read-only recovery and administration observability.
 *
 * Existing mutations continue to flow through the v1.6.1 safety layer.
 * v1.6.2 adds inspection, dry-run planning, audit querying and snapshot
 * verification without introducing an automatic restore path.
 */
public final class AdministrationRecoveryManager implements Listener, CommandExecutor, TabCompleter {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Set<String> TERMINAL_CONFIRM_EVENTS = Set.of(
            "CONFIRM_REPLACED", "CONFIRM_CANCEL", "CONFIRM_LOGOUT_CANCEL", "CONFIRM_EXPIRED",
            "CONFIRM_REPLAY", "DANGEROUS_EXECUTE", "DISBAND_ARCHIVE_VERIFY");

    private record PendingObservation(String action, String target, long requestedAt,
                                      long expiresAt, int failedAttempts, boolean expired) {}
    private record SnapshotObservation(Path file, String bucket, String partyKey, String partyDisplay,
                                       String purpose, long exportedAt, long modifiedAt,
                                       String status, String sha256, String detail) {}

    private final MENKIESTESPartyPlugin plugin;
    private final AdministrationStabilityManager stability;
    private final AdministrationManager administration;
    private final PartyService parties;
    private final StorageBundle db;
    private final YamlConfiguration settings;
    private final PluginCommand partyAdminCommand;

    public AdministrationRecoveryManager(MENKIESTESPartyPlugin plugin,
                                         AdministrationStabilityManager stability,
                                         AdministrationManager administration,
                                         PartyService parties,
                                         StorageBundle db) {
        this.plugin = plugin;
        this.stability = stability;
        this.administration = administration;
        this.parties = parties;
        this.db = db;
        File file = new File(plugin.getDataFolder(), "administration.yml");
        if (!file.isFile()) plugin.saveResource("administration.yml", false);
        this.settings = YamlConfiguration.loadConfiguration(file);
        this.partyAdminCommand = plugin.getCommand("partyadmin");
        if (partyAdminCommand != null) {
            partyAdminCommand.setExecutor(this);
            partyAdminCommand.setTabCompleter(this);
        }
    }

    public boolean enabled() {
        return administration.enabled() && settings.getBoolean("administration.observability.enabled", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (enabled() && handleSpecial(sender, args)) return true;
        return stability.onCommand(sender, command, label, args);
    }

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
            case "pending" -> {
                if (!require(sender, "dangerous")) return true;
                pending(sender);
                return true;
            }
            case "repair" -> {
                if (args.length >= 3 && args[2].equalsIgnoreCase("dryrun")) {
                    if (!require(sender, "modify")) return true;
                    repairDryRun(sender, args[1]);
                    return true;
                }
                return false;
            }
            case "audit" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("search")) {
                    if (!require(sender, "audit")) return true;
                    if (args.length < 3) msg(sender, "&c/partyadmin audit search <keyword> [page]");
                    else showAuditQuery(sender, null, args[2], args.length > 3 ? parseInt(args[3], 1) : 1, true);
                    return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("filter")) {
                    if (!require(sender, "audit")) return true;
                    if (args.length < 4) {
                        msg(sender, "&c/partyadmin audit filter <party|staff|action|result> <value> [page]");
                    } else if (!AdminAuditQuery.supportedField(args[2])) {
                        msg(sender, "&cAudit filter field must be party, staff, action, or result.");
                    } else {
                        showAuditQuery(sender, args[2], args[3], args.length > 4 ? parseInt(args[4], 1) : 1, false);
                    }
                    return true;
                }
                return false;
            }
            case "snapshot" -> {
                if (!require(sender, "inspect")) return true;
                if (args.length < 4 || !args[1].equalsIgnoreCase("verify")) {
                    msg(sender, "&c/partyadmin snapshot verify <archive|export> <filename.yml>");
                } else {
                    verifySnapshot(sender, args[2], args[3]);
                }
                return true;
            }
            case "recovery" -> {
                if (!require(sender, "inspect")) return true;
                if (args.length < 2) msg(sender, "&c/partyadmin recovery <party>");
                else recovery(sender, args[1]);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Pending confirmation observability
    // ---------------------------------------------------------------------

    private void pending(CommandSender sender) {
        PendingObservation observation = pendingObservation(sender);
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lADMIN PENDING &8- &fv1.6.2"));
        sender.sendMessage(Util.color("&7Registry count (all staff): &f" + stability.pendingConfirmations()));
        if (observation == null) {
            sender.sendMessage(Util.color("&7Your pending action: &fnone detected"));
            return;
        }
        long remaining = Math.max(0L, (observation.expiresAt() - System.currentTimeMillis() + 999L) / 1000L);
        sender.sendMessage(Util.color("&7Action: &f" + observation.action()));
        sender.sendMessage(Util.color("&7Target: &f" + observation.target()));
        sender.sendMessage(Util.color("&7Requested: &f" + DISPLAY_TIME.format(Instant.ofEpochMilli(observation.requestedAt()))));
        sender.sendMessage(Util.color("&7Status: " + (observation.expired() ? "&cEXPIRED" : "&ePENDING")));
        sender.sendMessage(Util.color("&7Remaining: &f" + remaining + "s"));
        sender.sendMessage(Util.color("&7Wrong-token attempts: &f" + observation.failedAttempts()
                + "/" + Math.max(1, settings.getInt("administration.confirmation.max-attempts", 3))));
        sender.sendMessage(Util.color("&8Token is intentionally not displayed by /partyadmin pending."));
    }

    private PendingObservation pendingObservation(CommandSender sender) {
        String actor = actor(sender);
        int failedAttempts = 0;
        long now = System.currentTimeMillis();
        for (AdminAuditQuery.Entry entry : auditEntries()) {
            if (!entry.actor().equals(actor)) continue;
            String action = entry.action().toUpperCase(Locale.ROOT);
            if (TERMINAL_CONFIRM_EVENTS.contains(action)) return null;
            if (action.equals("CONFIRM_TOKEN")) {
                String detail = entry.detail().toLowerCase(Locale.ROOT);
                if (detail.contains("max attempts reached")) return null;
                failedAttempts = Math.max(failedAttempts, numberAfter(detail, "wrong-token attempt=", 0));
                continue;
            }
            if (!action.equals("CONFIRM_REQUEST")) continue;
            int fallback = Math.max(5, settings.getInt("administration.confirmation-seconds", 30));
            int seconds = numberAfter(entry.detail().toLowerCase(Locale.ROOT), "expires=", fallback);
            String requestedAction = valueAfter(entry.detail(), "action=", "UNKNOWN");
            long expiresAt = entry.at() + Math.max(1, seconds) * 1000L;
            return new PendingObservation(requestedAction, entry.party(), entry.at(), expiresAt,
                    failedAttempts, now > expiresAt);
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Repair dry-run
    // ---------------------------------------------------------------------

    private void repairDryRun(CommandSender sender, String input) {
        String party = resolveParty(input);
        if (party == null) {
            msg(sender, "&cParty not found: &f" + input);
            return;
        }
        AdminRepairPlanner.Plan plan = buildRepairPlan(party);
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lREPAIR DRY-RUN &8- &f" + parties.display(party)));
        sender.sendMessage(Util.color("&7Internal key: &f" + party));
        if (!plan.safe()) {
            sender.sendMessage(Util.color("&cUNSAFE TO AUTO-REPAIR"));
            for (String warning : plan.warnings()) sender.sendMessage(Util.color("&c- &7" + warning));
            sender.sendMessage(Util.color("&8No Party data was changed."));
            return;
        }
        sender.sendMessage(Util.color("&7Planned deterministic changes: &f" + plan.changeCount()));
        if (plan.actions().isEmpty()) sender.sendMessage(Util.color("&aNo repair changes are currently required."));
        int maxLines = Math.max(5, Math.min(50,
                settings.getInt("administration.observability.repair-dryrun-max-lines", 20)));
        for (String action : plan.actions().stream().limit(maxLines).toList()) {
            sender.sendMessage(Util.color("&e- &7" + action));
        }
        if (plan.actions().size() > maxLines) {
            sender.sendMessage(Util.color("&8... " + (plan.actions().size() - maxLines) + " additional planned change(s) hidden."));
        }
        sender.sendMessage(Util.color("&8No Party data was changed. Run /partyadmin repair " + party + " to apply deterministic fixes."));
    }

    private AdminRepairPlanner.Plan buildRepairPlan(String party) {
        UUID owner = parties.owner(party);
        Set<UUID> members = new LinkedHashSet<>(parties.members(party));
        String ownerUuid = owner == null ? null : owner.toString();
        boolean ownerInRoster = owner != null && members.contains(owner);
        String ownerRole = owner == null ? "" : db.parties.getString(
                "parties." + party + ".members." + owner + ".role", "");
        String ownerIndex = owner == null ? "" : db.parties.getString("players." + owner + ".party", "");

        List<AdminRepairPlanner.MemberState> memberStates = new ArrayList<>();
        for (UUID member : members) {
            memberStates.add(new AdminRepairPlanner.MemberState(
                    member.toString(), owner != null && member.equals(owner),
                    db.parties.getString("parties." + party + ".members." + member + ".role", "MEMBER"),
                    db.parties.getString("players." + member + ".party", "")));
        }

        Set<UUID> membersAfterOwnerRepair = new LinkedHashSet<>(members);
        if (owner != null) membersAfterOwnerRepair.add(owner);
        List<AdminRepairPlanner.IndexState> indexStates = new ArrayList<>();
        ConfigurationSection indexes = db.parties.getConfigurationSection("players");
        if (indexes != null) for (String uuidText : indexes.getKeys(false)) {
            if (!party.equals(indexes.getString(uuidText + ".party"))) continue;
            try {
                UUID uuid = UUID.fromString(uuidText);
                indexStates.add(new AdminRepairPlanner.IndexState(uuidText, true, membersAfterOwnerRepair.contains(uuid)));
            } catch (Exception invalid) {
                indexStates.add(new AdminRepairPlanner.IndexState(uuidText, false, false));
            }
        }

        return AdminRepairPlanner.plan(party, ownerUuid, ownerInRoster, ownerRole, ownerIndex,
                memberStates, indexStates);
    }

    // ---------------------------------------------------------------------
    // Audit search/filter
    // ---------------------------------------------------------------------

    private void showAuditQuery(CommandSender sender, String field, String value, int requestedPage, boolean search) {
        List<AdminAuditQuery.Entry> filtered = auditEntries().stream()
                .filter(entry -> search ? AdminAuditQuery.matchesSearch(entry, value)
                        : AdminAuditQuery.matchesFilter(entry, field, value))
                .toList();
        int perPage = Math.max(5, Math.min(25,
                settings.getInt("administration.observability.audit-page-size", 10)));
        int pages = Math.max(1, (filtered.size() + perPage - 1) / perPage);
        int page = Math.max(1, Math.min(pages, requestedPage));
        String label = search ? "search '" + trim(value, 32) + "'"
                : "filter " + field + "='" + trim(value, 32) + "'";
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lADMIN AUDIT &8- &f" + label + " &8- &f" + page + "/" + pages));
        sender.sendMessage(Util.color("&7Matches: &f" + filtered.size()));
        int start = (page - 1) * perPage;
        for (int i = start; i < Math.min(filtered.size(), start + perPage); i++) {
            AdminAuditQuery.Entry entry = filtered.get(i);
            sender.sendMessage(Util.color("&8#" + entry.id() + " &7"
                    + DISPLAY_TIME.format(Instant.ofEpochMilli(entry.at())) + " &f" + entry.action()
                    + " &8[&f" + entry.result() + "&8]"));
            sender.sendMessage(Util.color("  &7party=&f" + value(entry.party(), "-")
                    + " &7staff=&f" + trim(entry.actor(), 36)
                    + " &7detail=&f" + trim(entry.detail(), 100)));
        }
        if (filtered.isEmpty()) sender.sendMessage(Util.color("&7No audit entries matched."));
    }

    private List<AdminAuditQuery.Entry> auditEntries() {
        ConfigurationSection section = db.interactions.getConfigurationSection("moderation.audit");
        if (section == null) return List.of();
        List<String> ids = new ArrayList<>(section.getKeys(false));
        ids.sort(Comparator.reverseOrder());
        List<AdminAuditQuery.Entry> entries = new ArrayList<>(ids.size());
        for (String id : ids) {
            String base = "moderation.audit." + id;
            entries.add(new AdminAuditQuery.Entry(
                    id,
                    db.interactions.getLong(base + ".at", 0L),
                    db.interactions.getString(base + ".actor", "UNKNOWN"),
                    db.interactions.getString(base + ".action", "UNKNOWN"),
                    db.interactions.getString(base + ".party", ""),
                    db.interactions.getString(base + ".result", "LEGACY"),
                    db.interactions.getString(base + ".detail", "")));
        }
        return List.copyOf(entries);
    }

    // ---------------------------------------------------------------------
    // Snapshot verification / recovery intelligence
    // ---------------------------------------------------------------------

    private void verifySnapshot(CommandSender sender, String bucket, String fileName) {
        try {
            Path path = AdminSnapshotVerifier.resolveSnapshot(plugin.getDataFolder().toPath(), bucket, fileName);
            SnapshotObservation observation = inspectSnapshot(path, normalizeBucket(bucket));
            sender.sendMessage(Util.color("&8&m--------------------------------"));
            sender.sendMessage(Util.color("&6&lSNAPSHOT VERIFY &8- &fv1.6.2"));
            sender.sendMessage(Util.color("&7File: &f" + observation.file().getFileName()));
            sender.sendMessage(Util.color("&7Bucket: &f" + observation.bucket()));
            sender.sendMessage(Util.color("&7Status: " + statusColor(observation.status()) + observation.status()));
            sender.sendMessage(Util.color("&7Party: &f" + value(observation.partyDisplay(), observation.partyKey())
                    + " &8[&7" + value(observation.partyKey(), "unknown") + "&8]"));
            sender.sendMessage(Util.color("&7Purpose: &f" + value(observation.purpose(), "legacy/unknown")));
            if (observation.exportedAt() > 0L) {
                sender.sendMessage(Util.color("&7Created: &f" + DISPLAY_TIME.format(Instant.ofEpochMilli(observation.exportedAt()))));
            }
            if (!observation.sha256().isBlank()) sender.sendMessage(Util.color("&7SHA-256: &f" + observation.sha256()));
            sender.sendMessage(Util.color("&7Detail: &f" + observation.detail()));
        } catch (Exception failure) {
            msg(sender, "&cSnapshot verification failed: &f" + safe(failure));
        }
    }

    private void recovery(CommandSender sender, String input) {
        String liveParty = resolveParty(input);
        String normalizedInput = Util.key(input);
        List<SnapshotObservation> candidates = scanSnapshots(liveParty, normalizedInput, input);
        String partyKey = liveParty;
        if (partyKey == null && !candidates.isEmpty()) partyKey = candidates.get(0).partyKey();
        if (partyKey == null || partyKey.isBlank()) {
            msg(sender, "&cNo live Party or matching snapshot found for &f" + input + "&c.");
            return;
        }

        final String resolvedKey = partyKey;
        List<SnapshotObservation> matching = candidates.stream()
                .filter(snapshot -> resolvedKey.equals(snapshot.partyKey()))
                .sorted(Comparator.comparingLong(SnapshotObservation::modifiedAt).reversed())
                .toList();
        long verified = matching.stream().filter(s -> s.status().equals("VERIFIED")).count();
        long legacy = matching.stream().filter(s -> s.status().equals("LEGACY_UNVERIFIED")).count();
        long corrupt = matching.stream().filter(s -> s.status().equals("CORRUPT") || s.status().equals("METADATA_INVALID")).count();
        SnapshotObservation latestVerified = matching.stream().filter(s -> s.status().equals("VERIFIED")).findFirst().orElse(null);
        SnapshotObservation latestLegacy = matching.stream().filter(s -> s.status().equals("LEGACY_UNVERIFIED")).findFirst().orElse(null);
        boolean live = parties.exists(resolvedKey);

        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lPARTY RECOVERY REPORT &8- &fv1.6.2"));
        sender.sendMessage(Util.color("&7Party key: &f" + resolvedKey));
        sender.sendMessage(Util.color("&7Live Party: " + (live ? "&aYES" : "&cNO")));
        if (live) {
            AdminRepairPlanner.Plan plan = buildRepairPlan(resolvedKey);
            sender.sendMessage(Util.color("&7Live integrity: " + (!plan.safe() ? "&cUNSAFE OWNER STATE"
                    : plan.changeCount() == 0 ? "&aHEALTHY" : "&e" + plan.changeCount() + " deterministic repair(s) available")));
        }
        sender.sendMessage(Util.color("&7Snapshots: &a" + verified + " verified &8| &e" + legacy
                + " legacy-unverified &8| &c" + corrupt + " corrupt/invalid"));
        SnapshotObservation preferred = latestVerified != null ? latestVerified : latestLegacy;
        if (preferred != null) {
            sender.sendMessage(Util.color("&7Latest usable candidate: &f" + preferred.file().getFileName()));
            sender.sendMessage(Util.color("&7Candidate status: " + statusColor(preferred.status()) + preferred.status()
                    + " &8| &7purpose=&f" + value(preferred.purpose(), "legacy/unknown")));
            if (preferred.exportedAt() > 0L) {
                sender.sendMessage(Util.color("&7Candidate created: &f"
                        + DISPLAY_TIME.format(Instant.ofEpochMilli(preferred.exportedAt()))));
            }
            if (!preferred.sha256().isBlank()) sender.sendMessage(Util.color("&7Candidate SHA-256: &f" + preferred.sha256()));
        } else {
            sender.sendMessage(Util.color("&cNo usable verified/legacy snapshot candidate was found."));
        }
        sender.sendMessage(Util.color("&8Recovery in v1.6.2 is read-only; no snapshot is automatically restored."));
    }

    private List<SnapshotObservation> scanSnapshots(String liveParty, String normalizedInput, String rawInput) {
        int limit = Math.max(20, Math.min(500,
                settings.getInt("administration.observability.snapshot-scan-limit", 200)));
        List<SnapshotObservation> observations = new ArrayList<>();
        for (String bucket : List.of("archives", "admin-exports")) {
            Path directory = plugin.getDataFolder().toPath().resolve(bucket);
            if (!Files.isDirectory(directory)) continue;
            try (var stream = Files.list(directory)) {
                for (Path file : stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                        .sorted(Comparator.comparingLong(AdministrationRecoveryManager::modifiedTime).reversed())
                        .limit(limit).toList()) {
                    SnapshotObservation observation = inspectSnapshot(file, bucket);
                    boolean matches = liveParty != null
                            ? liveParty.equals(observation.partyKey())
                            : normalizedInput.equals(observation.partyKey())
                            || rawInput.equalsIgnoreCase(observation.partyDisplay());
                    if (matches) observations.add(observation);
                }
            } catch (Exception failure) {
                plugin.getLogger().warning("v1.6.2 recovery scan warning for " + bucket + ": " + safe(failure));
            }
        }
        observations.sort(Comparator.comparingLong(SnapshotObservation::modifiedAt).reversed());
        return List.copyOf(observations);
    }

    private SnapshotObservation inspectSnapshot(Path file, String bucket) {
        long modified = modifiedTime(file);
        AdminSnapshotVerifier.Result checksum;
        try {
            checksum = AdminSnapshotVerifier.verifyChecksum(file);
        } catch (Exception failure) {
            return new SnapshotObservation(file, bucket, "", "", "", 0L, modified,
                    "CORRUPT", "", "checksum read failed: " + safe(failure));
        }
        if (checksum.status() == AdminSnapshotVerifier.Status.MISSING) {
            return new SnapshotObservation(file, bucket, "", "", "", 0L, modified,
                    "MISSING", "", checksum.detail());
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        String partyKey = yaml.getString("meta.party-key", "");
        String display = yaml.getString("meta.party-display", "");
        String format = yaml.getString("meta.format", "");
        String purpose = yaml.getString("meta.purpose", "");
        long exportedAt = yaml.getLong("meta.exported-at", 0L);
        if (partyKey.isBlank()) {
            return new SnapshotObservation(file, bucket, "", display, purpose, exportedAt, modified,
                    "METADATA_INVALID", checksum.sha256(), "meta.party-key missing");
        }
        if (checksum.status() == AdminSnapshotVerifier.Status.CORRUPT) {
            return new SnapshotObservation(file, bucket, partyKey, display, purpose, exportedAt, modified,
                    "CORRUPT", checksum.sha256(), checksum.detail());
        }
        if (checksum.status() == AdminSnapshotVerifier.Status.VERIFIED
                && "MENKIESTESParty-admin-snapshot-v1".equals(format)) {
            return new SnapshotObservation(file, bucket, partyKey, display, purpose, exportedAt, modified,
                    "VERIFIED", checksum.sha256(), "SHA-256 and snapshot-v1 metadata verified");
        }
        String detail = checksum.status() == AdminSnapshotVerifier.Status.VERIFIED
                ? "checksum matches but snapshot format is legacy/unknown"
                : checksum.detail();
        return new SnapshotObservation(file, bucket, partyKey, display, purpose, exportedAt, modified,
                "LEGACY_UNVERIFIED", checksum.sha256(), detail);
    }

    // ---------------------------------------------------------------------
    // Enhanced health
    // ---------------------------------------------------------------------

    private void health(CommandSender sender) {
        List<String> keys = partyKeys();
        int frozen = 0;
        for (String party : keys) if (administration.isFrozen(party)) frozen++;
        List<AdminAuditQuery.Entry> audit = auditEntries();
        int auditCount = audit.size();
        int integrityWarnings = healthIntegrityWarnings(keys);
        int failureWindowHours = Math.max(1, Math.min(168,
                settings.getInt("administration.observability.failed-action-window-hours", 24)));
        long cutoff = System.currentTimeMillis() - failureWindowHours * 3_600_000L;
        long recentFailed = audit.stream()
                .filter(entry -> entry.at() >= cutoff && entry.result().equalsIgnoreCase("FAILED"))
                .count();

        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lADMINISTRATION HEALTH &8- &fv1.6.2"));
        sender.sendMessage(Util.color("&7Parties: &f" + keys.size() + " &8| &7Frozen: &f" + frozen));
        sender.sendMessage(Util.color("&7Pending confirmations: &f" + stability.pendingConfirmations()));
        sender.sendMessage(Util.color("&7Audit entries: &f" + auditCount + " &8| &7Failed last "
                + failureWindowHours + "h: " + (recentFailed == 0 ? "&a0" : "&c" + recentFailed)));
        sender.sendMessage(Util.color("&7Archive dir: " + (directoryHealthy(plugin.getDataFolder().toPath().resolve("archives")) ? "&aOK" : "&cNOT WRITABLE")
                + " &8| &7Export dir: " + (directoryHealthy(plugin.getDataFolder().toPath().resolve("admin-exports")) ? "&aOK" : "&cNOT WRITABLE")));
        sender.sendMessage(Util.color("&7Storage: &f" + db.activeBackend() + (db.degraded() ? " &cDEGRADED" : " &aHEALTHY")));
        sender.sendMessage(Util.color("&7Storage queue: &fpending=" + db.pendingWrites()
                + " age=" + db.pendingAgeMillis() + "ms failed=" + db.failedWrites()));
        String probe = db.lastHealthCheckAt() <= 0L ? "&eNOT RUN"
                : db.lastHealthHealthy() ? "&aHEALTHY" : "&cFAILED";
        sender.sendMessage(Util.color("&7Storage health probe: " + probe
                + " &8| &7consecutive failures=&f" + db.consecutiveHealthFailures()));
        sender.sendMessage(Util.color("&7Developer API: &f" + plugin.apiHardening().healthLabel()));
        sender.sendMessage(Util.color("&7Integrity warnings: " + (integrityWarnings == 0 ? "&a0" : "&c" + integrityWarnings)));
    }

    private int healthIntegrityWarnings(List<String> partyList) {
        int warnings = 0;
        Set<String> valid = new LinkedHashSet<>(partyList);
        for (String party : partyList) {
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
            } catch (Exception invalid) {
                warnings++;
            }
        }
        return warnings;
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    private boolean require(CommandSender sender, String suffix) {
        if (sender.hasPermission("menkiestesparty.admin")
                || sender.hasPermission("menkiestesparty.admin." + suffix)) return true;
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

    private List<String> partyKeys() {
        ConfigurationSection root = db.parties.getConfigurationSection("parties");
        return root == null ? new ArrayList<>() : new ArrayList<>(root.getKeys(false));
    }

    private boolean directoryHealthy(Path directory) {
        try {
            Files.createDirectories(directory);
            return Files.isDirectory(directory) && Files.isWritable(directory);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String actor(CommandSender sender) {
        return sender instanceof Player player ? player.getName() + "(" + player.getUniqueId() + ")" : sender.getName();
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&6&lMENKIESTES PARTY ADMIN &8- &fv1.6.2"));
        sender.sendMessage(Util.color("&f/partyadmin pending &7- inspect your staged dangerous action without revealing its token"));
        sender.sendMessage(Util.color("&f/partyadmin repair <party> dryrun &7- preview deterministic repair changes"));
        sender.sendMessage(Util.color("&f/partyadmin audit search <keyword> [page]"));
        sender.sendMessage(Util.color("&f/partyadmin audit filter <party|staff|action|result> <value> [page]"));
        sender.sendMessage(Util.color("&f/partyadmin snapshot verify <archive|export> <filename.yml>"));
        sender.sendMessage(Util.color("&f/partyadmin recovery <party> &7- read-only snapshot/live-state recovery report"));
        sender.sendMessage(Util.color("&f/partyadmin health &7- enhanced storage/admin/integrity health"));
        sender.sendMessage(Util.color("&8All v1.6.1 administration commands remain available."));
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(parties.prefix() + Util.color(" " + text));
    }

    private static String normalizeBucket(String bucket) {
        String lower = bucket == null ? "" : bucket.toLowerCase(Locale.ROOT);
        return lower.startsWith("archive") ? "archives" : "admin-exports";
    }

    private static String statusColor(String status) {
        return switch (status) {
            case "VERIFIED" -> "&a";
            case "LEGACY_UNVERIFIED" -> "&e";
            case "CORRUPT", "METADATA_INVALID", "MISSING" -> "&c";
            default -> "&f";
        };
    }

    private static String valueAfter(String text, String key, String fallback) {
        if (text == null) return fallback;
        int at = text.toLowerCase(Locale.ROOT).indexOf(key.toLowerCase(Locale.ROOT));
        if (at < 0) return fallback;
        int start = at + key.length();
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        String value = text.substring(start, end).trim();
        return value.isBlank() ? fallback : value;
    }

    private static int numberAfter(String text, String key, int fallback) {
        String value = valueAfter(text, key, Integer.toString(fallback));
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return fallback;
        try {
            return Integer.parseInt(digits);
        } catch (Exception ignored) {
            return fallback;
        }
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

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!enabled()) return stability.onTabComplete(sender, command, alias, args);
        if (args.length == 1) {
            List<String> values = new ArrayList<>(stability.onTabComplete(sender, command, alias, args));
            values.addAll(List.of("pending", "snapshot", "recovery"));
            return filter(values, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("audit")) {
            return filter(List.of("filter", "search"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("recovery")) {
            return filter(partyKeys(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("repair")) {
            return filter(List.of("dryrun"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("audit") && args[1].equalsIgnoreCase("filter")) {
            return filter(List.of("party", "staff", "action", "result"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("snapshot")) {
            return filter(List.of("verify"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("snapshot") && args[1].equalsIgnoreCase("verify")) {
            return filter(List.of("archive", "export"), args[2]);
        }
        return stability.onTabComplete(sender, command, alias, args);
    }

    private static List<String> filter(List<String> values, String typed) {
        String q = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(q)).distinct().limit(100).toList();
    }
}
