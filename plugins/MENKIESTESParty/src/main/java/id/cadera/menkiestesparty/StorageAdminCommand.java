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
        plugin.messages().send(sender, "storage.async", Map.of("value", storage.asyncWrites()));
        plugin.messages().send(sender, "storage.degraded", Map.of("value", storage.degraded()));
        plugin.messages().send(sender, "storage.pending", Map.of("count", storage.pendingWrites()));
        plugin.messages().send(sender, "storage.last-write", Map.of("value", formatTime(storage.lastSuccessfulWriteAt())));
        if (!storage.lastError().isBlank()) {
            plugin.messages().send(sender, "storage.last-error", Map.of("error", storage.lastError()));
        }
        sender.sendMessage(plugin.messages().prefix() + " " + Util.color("&7Scheduler: &f" + plugin.schedulerCompat().mode()));
        sender.sendMessage(plugin.messages().prefix() + " " + Util.color("&8" + storage.backendDiagnostics()));
        return true;
    }

    private boolean verify(CommandSender sender) {
        plugin.messages().send(sender, "storage.verify-start");
        plugin.storage().verifyAsync().whenComplete((healthy, failure) -> plugin.schedulerCompat().runGlobal(() -> {
            if (failure == null && Boolean.TRUE.equals(healthy)) plugin.messages().send(sender, "storage.verify-ok");
            else plugin.messages().send(sender, "storage.verify-failed");
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
            plugin.messages().send(sender, "storage.backup-ok", Map.of("path", path.toAbsolutePath()));
        } catch (Exception failure) {
            plugin.messages().send(sender, "storage.backup-failed", Map.of("error", safe(failure)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("menkiestesparty.admin")) return List.of();
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("status", "verify", "flush", "backup").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }

    private static String formatTime(long millis) {
        return millis <= 0L ? "never" : TIME.format(Instant.ofEpochMilli(millis));
    }

    private static String safe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
