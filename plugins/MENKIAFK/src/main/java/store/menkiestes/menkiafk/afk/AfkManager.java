package store.menkiestes.menkiafk.afk;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import store.menkiestes.menkiafk.MenkiAfkPlugin;
import store.menkiestes.menkiafk.util.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class AfkManager {
    private final MenkiAfkPlugin plugin;
    private final Map<UUID, AfkSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextManualAfk = new ConcurrentHashMap<>();

    public AfkManager(MenkiAfkPlugin plugin) {
        this.plugin = plugin;
    }

    public void initializePlayer(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void removePlayer(Player player) {
        UUID id = player.getUniqueId();
        sessions.remove(id);
        lastActivity.remove(id);
        nextManualAfk.remove(id);
    }

    public boolean isAfk(UUID id) {
        return sessions.containsKey(id);
    }

    public AfkSession getSession(UUID id) {
        return sessions.get(id);
    }

    public int afkCount() {
        return sessions.size();
    }

    public int trackedPlayers() {
        return lastActivity.size();
    }

    public long lastActivity(UUID id) {
        return lastActivity.getOrDefault(id, System.currentTimeMillis());
    }

    public void touch(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** Hot-path friendly activity update for movement/rotation. */
    public void touchThrottled(Player player, long minimumIntervalMs) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long previous = lastActivity.getOrDefault(id, 0L);
        if (now - previous >= Math.max(100L, minimumIntervalMs)) {
            lastActivity.put(id, now);
        }
    }

    public long manualCooldownRemaining(Player player) {
        long remaining = nextManualAfk.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public void setManualAfk(Player player, String rawReason) {
        String reason = player.hasPermission("menki.afk.color")
                ? Text.coloredReason(rawReason)
                : Text.plainReason(rawReason);
        long now = System.currentTimeMillis();
        sessions.put(player.getUniqueId(), new AfkSession(reason, now, AfkType.MANUAL));
        lastActivity.put(player.getUniqueId(), now);

        long cooldown = Math.max(0L, plugin.getConfig().getLong("manual-afk.cooldown-seconds", 8L)) * 1000L;
        nextManualAfk.put(player.getUniqueId(), now + cooldown);

        if (plugin.getConfig().getBoolean("broadcast.on-afk", true)) {
            String msg = Text.replace(Text.cfg(plugin, "messages.afk-broadcast"),
                    "%player%", player.getName(), "%reason%", reason);
            Bukkit.broadcastMessage(msg);
        }
    }

    public void setAutoAfk(Player player) {
        if (isAfk(player.getUniqueId())) return;
        long timeoutSeconds = Math.max(30L, plugin.getConfig().getLong("auto-afk.timeout-seconds", 300L));
        long roundedMinutes = Math.max(1L, (timeoutSeconds + 59L) / 60L);
        String reason = Text.color(Text.replace(
                plugin.getConfig().getString("auto-afk.reason", "Tidak aktif selama %minutes% menit"),
                "%minutes%", roundedMinutes,
                "%seconds%", timeoutSeconds));
        long now = System.currentTimeMillis();
        sessions.put(player.getUniqueId(), new AfkSession(reason, now, AfkType.AUTO));

        if (plugin.getConfig().getBoolean("broadcast.on-afk", true)) {
            String msg = Text.replace(Text.cfg(plugin, "messages.auto-afk-broadcast"),
                    "%player%", player.getName(), "%reason%", reason);
            Bukkit.broadcastMessage(msg);
        }
    }

    public boolean returnFromAfk(Player player, boolean broadcast) {
        AfkSession session = sessions.remove(player.getUniqueId());
        touch(player);
        if (session == null) return false;

        long duration = System.currentTimeMillis() - session.startedAt();
        if (broadcast && plugin.getConfig().getBoolean("broadcast.on-return", true)) {
            String msg = Text.replace(Text.cfg(plugin, "messages.return-broadcast"),
                    "%player%", player.getName(), "%duration%", Text.duration(duration));
            Bukkit.broadcastMessage(msg);
        }

        deliverRemembered(player, session);
        return true;
    }

    private void deliverRemembered(Player player, AfkSession session) {
        List<RememberedMessage> messages = session.drainMessages();
        if (messages.isEmpty()) return;

        player.sendMessage(Text.replace(Text.cfg(plugin, "messages.inbox-header"), "%count%", messages.size()));
        String format = Text.cfg(plugin, "messages.inbox-line");
        for (RememberedMessage message : messages) {
            player.sendMessage(Text.replace(format,
                    "%sender%", message.sender(),
                    "%message%", message.message()));
        }
    }

    public void checkAutoAfk() {
        if (!plugin.getConfig().getBoolean("auto-afk.enabled", true)) return;

        long now = System.currentTimeMillis();
        long timeoutMs = Math.max(30L, plugin.getConfig().getLong("auto-afk.timeout-seconds", 300L)) * 1000L;
        Set<String> ignoredWorlds = new HashSet<>();
        for (String world : plugin.getConfig().getStringList("auto-afk.ignored-worlds")) {
            ignoredWorlds.add(world.toLowerCase(Locale.ROOT));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player.getUniqueId())) continue;
            if (player.hasPermission("menki.afk.auto.bypass")) continue;
            if (ignoredWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) continue;

            long last = lastActivity.getOrDefault(player.getUniqueId(), now);
            if (now - last >= timeoutMs) {
                setAutoAfk(player);
            }
        }
    }

    public void handleMentions(Player sender, String message) {
        if (!plugin.getConfig().getBoolean("mention.enabled", true)) return;
        int max = Math.max(1, plugin.getConfig().getInt("mention.max-notifications-per-message", 3));
        int sent = 0;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(sender.getUniqueId())) continue;
            AfkSession session = sessions.get(target.getUniqueId());
            if (session == null) continue;

            Pattern p = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(target.getName()) + "(?![A-Za-z0-9_])");
            if (!p.matcher(message).find()) continue;

            sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.mention-warning"),
                    "%player%", target.getName(),
                    "%reason%", session.reason(),
                    "%duration%", Text.duration(System.currentTimeMillis() - session.startedAt())));
            sent++;
            if (sent >= max) break;
        }
    }

    public void handlePrivateMessage(Player sender, String commandLine) {
        if (!plugin.getConfig().getBoolean("private-message.enabled", true)) return;
        if (commandLine == null || commandLine.length() < 2) return;

        String raw = commandLine.charAt(0) == '/' ? commandLine.substring(1) : commandLine;
        String[] parts = raw.split("\\s+", 3);
        if (parts.length < 2) return;

        String label = parts[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0 && namespace + 1 < label.length()) label = label.substring(namespace + 1);
        final String normalizedLabel = label;
        boolean supported = plugin.getConfig().getStringList("private-message.command-aliases")
                .stream().anyMatch(alias -> alias.equalsIgnoreCase(normalizedLabel));
        if (!supported) return;

        Player target = Bukkit.getPlayerExact(parts[1]);
        if (target == null || target.getUniqueId().equals(sender.getUniqueId())) return;
        AfkSession session = sessions.get(target.getUniqueId());
        if (session == null) return;

        sender.sendMessage(Text.replace(Text.cfg(plugin, "messages.pm-warning"),
                "%player%", target.getName(),
                "%reason%", session.reason(),
                "%duration%", Text.duration(System.currentTimeMillis() - session.startedAt())));

        if (plugin.getConfig().getBoolean("private-message.remember-while-afk", true) && parts.length >= 3) {
            int max = Math.max(1, plugin.getConfig().getInt("private-message.max-remembered", 10));
            String sanitized = Text.plainReason(parts[2]);
            session.remember(new RememberedMessage(sender.getName(), sanitized, System.currentTimeMillis()), max);
        }
    }

    public String placeholderStatus(UUID id) {
        return Text.color(plugin.getConfig().getString(isAfk(id) ? "placeholder.afk-text" : "placeholder.active-text",
                isAfk(id) ? "AFK" : "Aktif"));
    }

    public String placeholderReason(UUID id) {
        AfkSession session = sessions.get(id);
        return session == null
                ? Text.color(plugin.getConfig().getString("placeholder.no-reason-text", "-"))
                : session.reason();
    }

    public String placeholderTime(UUID id) {
        AfkSession session = sessions.get(id);
        return session == null ? "0d" : Text.duration(System.currentTimeMillis() - session.startedAt());
    }

    public String placeholderType(UUID id) {
        AfkSession session = sessions.get(id);
        if (session == null) return "-";
        String path = session.type() == AfkType.AUTO ? "placeholder.auto-text" : "placeholder.manual-text";
        return Text.color(plugin.getConfig().getString(path, session.type() == AfkType.AUTO ? "Otomatis" : "Manual"));
    }
}
