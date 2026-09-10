package id.cadera.menkiestesparty;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StorageAdminCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final MENKIESTESPartyPlugin plugin;

    public StorageAdminCommand(MENKIESTESPartyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("menkiestesparty.admin")) {
            plugin.messages().send(sender, "common.no-permission");
            return true;
        }

        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "status" -> status(sender);
            case "verify" -> verify(sender);
            case "flush" -> flush(sender);
            case "backup" -> backup(sender);
            case "migrate" -> migrate(sender, args);
            case "rollback" -> rollback(sender);
            default -> {
                plugin.messages().send(sender, "storage.usage");
                yield true;
            }
        };
    }

    private boolean status(CommandSender sender) {
        StorageBundle storage = plugin.storage();
        plugin.messages().send(sender, "storage.header");
        plugin.messages().send(sender, "storage.configured", Map.of("backend", storage.configuredBackend()));
        plugin.messages().send(sender, "storage.active", Map.of("backend", storage.activeBackend()));
        plugin.messages().send(sender, "storage.schema", Map.of("version", storage.schemaVersion()));
        plugin.messages().send(sender, "storage.document-schema", Map.of(
                "version", plugin.architecture().documentSchemaVersion(),
                "state", plugin.architecture().schemaState()));
        plugin.messages().send(sender, "storage.async", Map.of("value", storage.asyncWrites()));
        plugin.messages().send(sender, "storage.degraded", Map.of("value", storage.degraded()));
        plugin.messages().send(sender, "storage.pending", Map.of(
                "count", storage.pendingWrites(),
                "age", storage.pendingAgeMillis()));
        plugin.messages().send(sender, "storage.queue", Map.of(
                "submitted", storage.asyncSubmissions(),
                "coalesced", storage.coalescedSnapshots(),
                "success", storage.successfulWrites(),
                "failed", storage.failedWrites(),
                "delay", storage.lastQueueDelayMillis()));
        plugin.messages().send(sender, "storage.health", Map.of(
                "healthy", storage.lastHealthHealthy(),
                "failures", storage.consecutiveHealthFailures(),
                "checked", formatTime(storage.lastHealthCheckAt())));
        plugin.messages().send(sender, "storage.recovery", Map.of(
                "attempts", storage.recoveryAttempts(),
                "last", formatTime(storage.lastRecoveryAt())));
        plugin.messages().send(sender, "storage.migration-state", Map.of(
                "state", storage.migrationState(),
                "rollback", storage.rollbackBackend(),
                "running", storage.migrationRunning()));
        plugin.messages().send(sender, "storage.unclean", Map.of(
                "value", storage.uncleanShutdownDetected()));
        plugin.messages().send(sender, "storage.last-write", Map.of(
                "value", formatTime(storage.lastSuccessfulWriteAt())));
        if (!storage.lastError().isBlank()) {
            plugin.messages().send(sender, "storage.last-error", Map.of("error", storage.lastError()));
        }
        sender.sendMessage(plugin.messages().prefix() + " "
                + Util.color("&7Scheduler: &f" + plugin.schedulerCompat().mode()));
        sender.sendMessage(plugin.messages().prefix() + " "
                + Util.color("&8" + storage.backendDiagnostics()));
        return true;
    }

    private boolean verify(CommandSender sender) {
        plugin.messages().send(sender, "storage.verify-start");
        plugin.storage().verifyAsync().whenComplete((healthy, failure) ->
                plugin.schedulerCompat().runGlobal(() -> {
                    if (failure == null && Boolean.TRUE.equals(healthy)) {
                        plugin.messages().send(sender, "storage.verify-ok");
                    } else {
                        plugin.messages().send(sender, "storage.verify-failed");
                    }
                }));
        return true;
    }

    private boolean flush(CommandSender sender) {
        if (plugin.flushDurable()) plugin.messages().send(sender, "storage.flush-ok");
        else plugin.messages().send(sender, "storage.flush-failed");
        return true;
    }

    private boolean backup(CommandSender sender) {
        try {
            Path path = plugin.storage().backupNow();
            plugin.messages().send(sender, "storage.backup-ok",
                    Map.of("path", path.toAbsolutePath()));
        } catch (Exception failure) {
            plugin.messages().send(sender, "storage.backup-failed",
                    Map.of("error", safe(failure)));
        }
        return true;
    }

    private boolean migrate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "storage.migrate-usage");
            return true;
        }

        String target = args[1].toUpperCase(Locale.ROOT);
        boolean force = args.length >= 3 && args[2].equalsIgnoreCase("force");
        plugin.messages().send(sender, "storage.migrate-start",
                Map.of("target", target, "force", force));

        plugin.storage().migrateAsync(target, force).whenComplete((result, failure) ->
                plugin.schedulerCompat().runGlobal(() -> {
                    if (failure != null) {
                        plugin.messages().send(sender, "storage.migrate-failed",
                                Map.of("error", safe(failure)));
                        return;
                    }
                    if (result.success()) {
                        plugin.messages().send(sender, "storage.migrate-ok", Map.of(
                                "from", result.from(),
                                "to", result.to(),
                                "backup", result.backup() == null
                                        ? "none" : result.backup().toAbsolutePath()));
                    } else {
                        plugin.messages().send(sender, "storage.migrate-failed",
                                Map.of("error", result.message()));
                    }
                }));
        return true;
    }

    private boolean rollback(CommandSender sender) {
        String target = plugin.storage().rollbackBackend();
        if ("NONE".equals(target)) {
            plugin.messages().send(sender, "storage.rollback-none");
            return true;
        }

        plugin.messages().send(sender, "storage.rollback-start", Map.of("target", target));
        plugin.storage().rollbackAsync().whenComplete((result, failure) ->
                plugin.schedulerCompat().runGlobal(() -> {
                    if (failure != null) {
                        plugin.messages().send(sender, "storage.rollback-failed",
                                Map.of("error", safe(failure)));
                        return;
                    }
                    if (result.success()) {
                        plugin.messages().send(sender, "storage.rollback-ok", Map.of(
                                "from", result.from(),
                                "to", result.to(),
                                "backup", result.backup() == null
                                        ? "none" : result.backup().toAbsolutePath()));
                    } else {
                        plugin.messages().send(sender, "storage.rollback-failed",
                                Map.of("error", result.message()));
                    }
                }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("menkiestesparty.admin")) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("status", "verify", "flush", "backup", "migrate", "rollback").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
            String prefix = args[1].toUpperCase(Locale.ROOT);
            return List.of("YAML", "SQLITE", "MYSQL").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("migrate")) {
            return "force".startsWith(args[2].toLowerCase(Locale.ROOT))
                    ? List.of("force") : List.of();
        }
        return List.of();
    }

    private static String formatTime(long millis) {
        return millis <= 0L ? "never" : TIME.format(Instant.ofEpochMilli(millis));
    }

    private static String safe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName() : message;
    }
}
