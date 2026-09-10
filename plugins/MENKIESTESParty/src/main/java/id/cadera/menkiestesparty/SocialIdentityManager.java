package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.event.PartyLevelChangeEvent;
import id.cadera.menkiestesparty.api.event.PartyMemberChangeEvent;
import id.cadera.menkiestesparty.api.event.PartyProjectCompleteEvent;
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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MENKIESTESParty v1.7.0 Social & Party Identity layer.
 *
 * Dynamic Identity from v1.2.x remains activity-derived and automatic. This
 * manager adds the player-facing public profile layer on top of it without
 * adding a scheduler or a new persistence document. Social values are stored
 * below parties.<key>.social.* inside the existing parties.yml document.
 */
public final class SocialIdentityManager implements Listener, CommandExecutor, TabCompleter {
    private static final Set<String> NESTED = Set.of(
            "profile", "social", "badges", "achievements", "top", "leaderboard"
    );
    private static final Set<String> LEADERBOARD_METRICS = Set.of(
            "reputation", "level", "members", "projects", "activity", "age"
    );
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());
    private static final Map<String, String> COLOR_CODES = Map.ofEntries(
            Map.entry("AQUA", "&b"),
            Map.entry("BLUE", "&9"),
            Map.entry("DARK_AQUA", "&3"),
            Map.entry("GREEN", "&a"),
            Map.entry("DARK_GREEN", "&2"),
            Map.entry("YELLOW", "&e"),
            Map.entry("GOLD", "&6"),
            Map.entry("RED", "&c"),
            Map.entry("LIGHT_PURPLE", "&d"),
            Map.entry("DARK_PURPLE", "&5"),
            Map.entry("WHITE", "&f"),
            Map.entry("GRAY", "&7")
    );

    private record Achievement(String id, String display, String description, String type, long target) {}
    private record LeaderboardEntry(String party, long score, int reputation) {}

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final ProgressionManager progression;
    private final InteractionManager interactions;
    private final StorageBundle db;
    private final Map<UUID, Long> editCooldown = new ConcurrentHashMap<>();
    private final Path configPath;
    private YamlConfiguration config;

    public SocialIdentityManager(MENKIESTESPartyPlugin plugin,
                                 PartyService parties,
                                 ProgressionManager progression,
                                 InteractionManager interactions,
                                 StorageBundle db) {
        this.plugin = plugin;
        this.parties = parties;
        this.progression = progression;
        this.interactions = interactions;
        this.db = db;
        this.configPath = plugin.getDataFolder().toPath().resolve("social.yml");
        loadConfig();

        for (String name : List.of("partyprofile", "partysocial", "partytop")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
    }

    public boolean enabled() {
        return config == null || config.getBoolean("social.enabled", true);
    }

    public void reload() {
        loadConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!enabled()) {
            if (name.equals("partyprofile") && sender instanceof Player player) {
                progression.showProfile(player);
            } else {
                sender.sendMessage(Util.color("&cMENKIESTESParty social identity module is disabled."));
            }
            return true;
        }
        return switch (name) {
            case "partyprofile" -> commandProfile(sender, args);
            case "partysocial" -> commandSocial(sender, args);
            case "partytop" -> commandTop(sender, args);
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPartySubcommand(PlayerCommandPreprocessEvent event) {
        if (!enabled()) return;
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] split = raw.substring(1).trim().split("\\s+");
        if (split.length < 2) return;
        String root = root(split[0]);
        if (!Set.of("party", "p", "parties").contains(root)) return;
        String sub = split[1].toLowerCase(Locale.ROOT);
        if (!NESTED.contains(sub)) return;

        event.setCancelled(true);
        String[] args = split.length <= 2 ? new String[0] : Arrays.copyOfRange(split, 2, split.length);
        switch (sub) {
            case "profile" -> commandProfile(event.getPlayer(), args);
            case "social" -> commandSocial(event.getPlayer(), args);
            case "badges", "achievements" -> commandSocial(event.getPlayer(), new String[]{"achievements"});
            case "top", "leaderboard" -> commandTop(event.getPlayer(), args);
        }
    }

    @EventHandler
    public void onPartyTab(TabCompleteEvent event) {
        if (!enabled()) return;
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) return;
        String raw = buffer.substring(1);
        boolean trailing = raw.endsWith(" ");
        String[] split = raw.trim().isEmpty() ? new String[0] : raw.trim().split("\\s+");
        if (split.length == 0 || !Set.of("party", "p", "parties").contains(root(split[0]))) return;

        if (split.length == 1 || (split.length == 2 && !trailing)) {
            String typed = split.length == 2 ? split[1].toLowerCase(Locale.ROOT) : "";
            List<String> out = new ArrayList<>(event.getCompletions());
            for (String option : List.of("profile", "social", "badges", "top", "leaderboard")) {
                if (option.startsWith(typed) && out.stream().noneMatch(x -> x.equalsIgnoreCase(option))) out.add(option);
            }
            event.setCompletions(out);
            return;
        }

        if (split.length >= 2) {
            String sub = split[1].toLowerCase(Locale.ROOT);
            String[] args = split.length <= 2 ? new String[0] : Arrays.copyOfRange(split, 2, split.length);
            if (trailing) args = Arrays.copyOf(args, args.length + 1);
            List<String> values = switch (sub) {
                case "profile" -> completeProfile(args);
                case "social" -> completeSocial(event.getSender(), args);
                case "top", "leaderboard" -> completeTop(args);
                default -> List.of();
            };
            if (!values.isEmpty()) event.setCompletions(values);
        }
    }

    @EventHandler
    public void onLevelChange(PartyLevelChangeEvent event) {
        if (enabled()) refreshAchievements(event.partyKey(), true);
    }

    @EventHandler
    public void onProjectComplete(PartyProjectCompleteEvent event) {
        if (enabled()) refreshAchievements(event.partyKey(), true);
    }

    @EventHandler
    public void onMemberChange(PartyMemberChangeEvent event) {
        if (enabled()) refreshAchievements(event.partyKey(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        editCooldown.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------------
    // Public profile commands
    // ---------------------------------------------------------------------

    private boolean commandProfile(CommandSender sender, String[] args) {
        if (!require(sender, "view")) return true;
        String party;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                msg(sender, "&c/partyprofile <party|player>");
                return true;
            }
            party = parties.partyOf(player.getUniqueId());
        } else {
            party = resolveParty(String.join(" ", args));
        }
        if (party == null || !parties.exists(party)) {
            msg(sender, "&cParty profile not found.");
            return true;
        }
        if (!visibleTo(sender, party)) {
            msg(sender, "&cThis Party profile is private.");
            return true;
        }
        refreshAchievements(party, false);
        showProfile(sender, party);
        return true;
    }

    private boolean commandSocial(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) {
            msg(player, "&cKamu belum punya Party.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            if (!require(player, "view")) return true;
            refreshAchievements(party, false);
            showProfile(player, party);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> showSocialHelp(player);
            case "achievements", "badges" -> {
                if (!require(player, "view")) return true;
                refreshAchievements(party, false);
                showAchievements(player, party);
            }
            case "member" -> {
                if (!require(player, "view")) return true;
                OfflinePlayer target = args.length >= 2 ? resolveMember(party, args[1]) : player;
                if (target == null) msg(player, "&cMember tidak ditemukan di Party-mu.");
                else showMember(player, party, target);
            }
            case "description" -> {
                if (!canEdit(player, party)) return true;
                if (args.length < 2) { msg(player, "&c/partysocial description <text|clear>"); return true; }
                if (!takeEditToken(player)) return true;
                String raw = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                String value = raw.equalsIgnoreCase("clear") ? "" : SocialIdentityPolicy.sanitizeDescription(raw, descriptionMax());
                db.parties.set(socialRoot(party) + ".description", value.isBlank() ? null : value);
                touchSocial(party, player);
                msg(player, value.isBlank() ? "&aParty description cleared." : "&aParty description updated: &f" + value);
            }
            case "tag" -> {
                if (!canEdit(player, party)) return true;
                if (args.length < 2) { msg(player, "&c/partysocial tag <TAG|clear>"); return true; }
                if (!takeEditToken(player)) return true;
                if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("none")) {
                    db.parties.set(socialRoot(party) + ".tag", null);
                    touchSocial(party, player);
                    msg(player, "&aParty tag cleared.");
                    return true;
                }
                String tag = SocialIdentityPolicy.normalizeTag(args[1], tagMin(), tagMax());
                if (tag == null) {
                    msg(player, "&cTag harus A-Z/0-9 sepanjang &f" + tagMin() + "-" + tagMax() + "&c karakter.");
                    return true;
                }
                if (config.getBoolean("social.profile.tag-unique", true) && tagInUse(tag, party)) {
                    msg(player, "&cTag tersebut sudah dipakai Party lain.");
                    return true;
                }
                db.parties.set(socialRoot(party) + ".tag", tag);
                touchSocial(party, player);
                msg(player, "&aParty tag updated: &f[" + tag + "]");
            }
            case "color" -> {
                if (!canEdit(player, party)) return true;
                if (args.length < 2) { msg(player, "&c/partysocial color <color>"); return true; }
                String color = args[1].toUpperCase(Locale.ROOT);
                if (!allowedColors().contains(color)) {
                    msg(player, "&cColor tidak diizinkan. &7" + String.join(", ", allowedColors()));
                    return true;
                }
                if (!takeEditToken(player)) return true;
                db.parties.set(socialRoot(party) + ".color", color);
                touchSocial(party, player);
                msg(player, "&aParty color updated: " + colorCode(color) + color);
            }
            case "icon" -> {
                if (!canEdit(player, party)) return true;
                if (args.length < 2) { msg(player, "&c/partysocial icon <material>"); return true; }
                String icon = args[1].toUpperCase(Locale.ROOT);
                Material material = Material.matchMaterial(icon);
                if (material == null || !allowedIcons().contains(icon)) {
                    msg(player, "&cIcon tidak diizinkan. &7" + String.join(", ", allowedIcons()));
                    return true;
                }
                if (!takeEditToken(player)) return true;
                db.parties.set(socialRoot(party) + ".icon", material.name());
                touchSocial(party, player);
                msg(player, "&aParty icon updated: &f" + material.name());
            }
            case "visibility" -> {
                if (!require(player, "edit")) return true;
                if (!player.getUniqueId().equals(parties.owner(party))) {
                    msg(player, "&cHanya Owner yang dapat mengubah visibility profile.");
                    return true;
                }
                if (args.length < 2 || (!args[1].equalsIgnoreCase("public") && !args[1].equalsIgnoreCase("private"))) {
                    msg(player, "&c/partysocial visibility <public|private>");
                    return true;
                }
                if (!takeEditToken(player)) return true;
                String value = args[1].toUpperCase(Locale.ROOT);
                db.parties.set(socialRoot(party) + ".visibility", value);
                touchSocial(party, player);
                msg(player, "&aParty profile visibility: &f" + value);
            }
            case "badge" -> {
                if (!canEdit(player, party)) return true;
                if (args.length < 2) { msg(player, "&c/partysocial badge <achievement|none>"); return true; }
                refreshAchievements(party, false);
                String id = args[1].toLowerCase(Locale.ROOT);
                if (id.equals("none") || id.equals("clear")) {
                    if (!takeEditToken(player)) return true;
                    db.parties.set(socialRoot(party) + ".badge", null);
                    touchSocial(party, player);
                    msg(player, "&aActive Party badge cleared.");
                    return true;
                }
                Achievement achievement = achievement(id);
                if (achievement == null) { msg(player, "&cAchievement tidak ditemukan."); return true; }
                if (!achievementUnlocked(party, id)) {
                    msg(player, "&cAchievement itu belum terbuka untuk Party ini.");
                    return true;
                }
                if (!takeEditToken(player)) return true;
                db.parties.set(socialRoot(party) + ".badge", id);
                touchSocial(party, player);
                msg(player, "&aActive Party badge: &f" + achievement.display());
            }
            default -> showSocialHelp(player);
        }
        return true;
    }

    private boolean commandTop(CommandSender sender, String[] args) {
        if (!require(sender, "leaderboard")) return true;
        String metric = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "reputation";
        if (!LEADERBOARD_METRICS.contains(metric)) {
            msg(sender, "&cMetric: &freputation, level, members, projects, activity, age");
            return true;
        }
        int requestedPage = args.length >= 2 ? parsePage(args[1]) : 1;
        showLeaderboard(sender, metric, requestedPage);
        return true;
    }

    // ---------------------------------------------------------------------
    // Profile rendering / member information
    // ---------------------------------------------------------------------

    private void showProfile(CommandSender sender, String party) {
        UUID owner = parties.owner(party);
        int online = 0;
        for (UUID member : parties.members(party)) if (Bukkit.getOfflinePlayer(member).isOnline()) online++;
        String projectId = progression.currentProject(party);
        String badge = activeBadgeDisplay(party);
        String description = description(party);
        long created = db.parties.getLong("parties." + party + ".created-at", 0L);

        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(profileName(party) + Util.color(" &8| &7Lv.&f" + parties.level(party)));
        sender.sendMessage(Util.color("&7Description: &f" + (description.isBlank() ? "No description yet." : description)));
        sender.sendMessage(Util.color("&7Owner: &f" + playerName(owner) + " &8| &7Members: &f" + online + "/" + parties.memberCount(party)));
        sender.sendMessage(Util.color("&7Identity: &e" + progression.identityDisplay(progression.identity(party))
                + " &8| &7Badge: &f" + (badge.isBlank() ? "None" : badge)));
        sender.sendMessage(Util.color("&7Reputation: &f" + parties.rep(party)
                + " &8| &7Recruitment: &f" + interactions.recruitmentMode(party)));
        sender.sendMessage(Util.color("&7Project: &f" + (projectId == null ? "None" : progression.projectName(projectId))
                + " &8| &7Achievements: &f" + achievementCount(party) + "/" + achievements().size()));
        sender.sendMessage(Util.color("&7Founded: &f" + (created <= 0L ? "Unknown" : DATE.format(Instant.ofEpochMilli(created)))
                + " &8| &7Visibility: &f" + visibility(party)));
        sender.sendMessage(Util.color("&7Icon: &f" + iconName(party) + " &8| &7Dynamic activity: &f" + activityTotal(party)));
    }

    private void showMember(CommandSender sender, String party, OfflinePlayer member) {
        UUID uuid = member.getUniqueId();
        if (!party.equals(parties.partyOf(uuid))) {
            msg(sender, "&cTarget bukan member Party ini.");
            return;
        }
        long joinedAt = memberSince(uuid);
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&b&lPARTY MEMBER &8- &f" + playerName(uuid)));
        sender.sendMessage(Util.color("&7Role: &f" + parties.role(uuid)));
        sender.sendMessage(Util.color("&7Division: &f" + progression.divisionDisplay(progression.divisionOf(uuid))));
        sender.sendMessage(Util.color("&7Status: &f" + memberStatus(uuid)));
        sender.sendMessage(Util.color("&7Joined: &f" + (joinedAt <= 0L ? "Unknown" : DATE.format(Instant.ofEpochMilli(joinedAt)))));
    }

    private void showAchievements(CommandSender sender, String party) {
        SocialIdentityPolicy.Metrics metrics = metrics(party);
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&d&lPARTY ACHIEVEMENTS &8- &f" + parties.display(party)));
        for (Achievement achievement : achievements()) {
            boolean unlocked = achievementUnlocked(party, achievement.id());
            long progress = SocialIdentityPolicy.achievementProgress(achievement.type(), metrics);
            String selected = achievement.id().equalsIgnoreCase(activeBadgeId(party)) ? " &d[ACTIVE BADGE]" : "";
            sender.sendMessage(Util.color((unlocked ? "&a✔ " : "&8✖ ") + "&f" + achievement.display()
                    + " &8- &7" + achievement.description() + " &8(&f" + Math.min(progress, achievement.target())
                    + "/" + achievement.target() + "&8)" + selected));
        }
        sender.sendMessage(Util.color("&7Unlocked: &f" + achievementCount(party) + "/" + achievements().size()));
    }

    private void showLeaderboard(CommandSender sender, String metric, int requestedPage) {
        boolean includePrivate = config.getBoolean("social.privacy.leaderboard-include-private", false);
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (String party : partyKeys()) {
            if (!includePrivate && visibility(party).equals("PRIVATE")) continue;
            SocialIdentityPolicy.Metrics metrics = metrics(party);
            entries.add(new LeaderboardEntry(party,
                    SocialIdentityPolicy.leaderboardScore(metric, metrics), parties.rep(party)));
        }
        entries.sort(Comparator.comparingLong(LeaderboardEntry::score).reversed()
                .thenComparing(Comparator.comparingInt(LeaderboardEntry::reputation).reversed())
                .thenComparing(entry -> parties.display(entry.party()), String.CASE_INSENSITIVE_ORDER));

        int perPage = Math.max(5, Math.min(25, config.getInt("social.leaderboard.page-size", 10)));
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        int page = Math.max(1, Math.min(pages, requestedPage));
        int start = (page - 1) * perPage;
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&b&lPARTY LEADERBOARD &8- &f" + metric.toUpperCase(Locale.ROOT)
                + " &8- &7Page &f" + page + "/" + pages));
        for (int index = start; index < Math.min(entries.size(), start + perPage); index++) {
            LeaderboardEntry entry = entries.get(index);
            sender.sendMessage(Util.color("&e#" + (index + 1) + " ") + profileName(entry.party())
                    + Util.color(" &8- &f" + entry.score() + " &7" + metric));
        }
        if (entries.isEmpty()) sender.sendMessage(Util.color("&7No public Party profiles available."));
    }

    // ---------------------------------------------------------------------
    // Achievement engine (event + command driven, no scheduler)
    // ---------------------------------------------------------------------

    private List<String> refreshAchievements(String party, boolean announce) {
        if (party == null || !parties.exists(party)) return List.of();
        SocialIdentityPolicy.Metrics metrics = metrics(party);
        List<String> unlocked = new ArrayList<>();
        for (Achievement achievement : achievements()) {
            String path = socialRoot(party) + ".achievements." + achievement.id() + ".unlocked-at";
            if (db.parties.getLong(path, 0L) > 0L) continue;
            if (!SocialIdentityPolicy.achievementUnlocked(achievement.type(), achievement.target(), metrics)) continue;
            db.parties.set(path, System.currentTimeMillis());
            unlocked.add(achievement.id());
        }
        if (!unlocked.isEmpty()) {
            plugin.saveDataSoon();
            if (announce && config.getBoolean("social.announce-achievement-unlock", true)) {
                for (String id : unlocked) {
                    Achievement achievement = achievement(id);
                    if (achievement != null) {
                        parties.broadcastParty(party, parties.prefix() + " &dAchievement unlocked: &f" + achievement.display());
                    }
                }
            }
        }
        return List.copyOf(unlocked);
    }

    private SocialIdentityPolicy.Metrics metrics(String party) {
        long now = System.currentTimeMillis();
        long created = db.parties.getLong("parties." + party + ".created-at", now);
        long ageDays = Math.max(0L, (now - Math.min(now, created)) / 86_400_000L);
        long activity = activityTotal(party);
        return new SocialIdentityPolicy.Metrics(
                parties.level(party),
                parties.rep(party),
                parties.memberCount(party),
                db.parties.getInt("parties." + party + ".progression.projects.completed", 0),
                ageDays,
                activity
        );
    }

    private List<Achievement> achievements() {
        ConfigurationSection section = config.getConfigurationSection("social.achievements");
        if (section == null) return List.of();
        List<Achievement> values = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            String root = "social.achievements." + id;
            values.add(new Achievement(
                    id.toLowerCase(Locale.ROOT),
                    config.getString(root + ".display-name", id),
                    config.getString(root + ".description", ""),
                    config.getString(root + ".type", "reputation"),
                    Math.max(0L, config.getLong(root + ".target", 0L))
            ));
        }
        return values;
    }

    private Achievement achievement(String id) {
        if (id == null) return null;
        for (Achievement achievement : achievements()) if (achievement.id().equalsIgnoreCase(id)) return achievement;
        return null;
    }

    private boolean achievementUnlocked(String party, String id) {
        return db.parties.getLong(socialRoot(party) + ".achievements." + id + ".unlocked-at", 0L) > 0L;
    }

    // ---------------------------------------------------------------------
    // PAPI-facing read methods
    // ---------------------------------------------------------------------

    public String description(String party) {
        return party == null ? "" : db.parties.getString(socialRoot(party) + ".description", "");
    }

    public String tag(String party) {
        return party == null ? "" : db.parties.getString(socialRoot(party) + ".tag", "").toUpperCase(Locale.ROOT);
    }

    public String colorName(String party) {
        String fallback = config == null ? "AQUA" : config.getString("social.profile.default-color", "AQUA");
        String value = party == null ? fallback : db.parties.getString(socialRoot(party) + ".color", fallback);
        value = value == null ? "AQUA" : value.toUpperCase(Locale.ROOT);
        return allowedColors().contains(value) ? value : fallback.toUpperCase(Locale.ROOT);
    }

    public String iconName(String party) {
        String fallback = config == null ? "PLAYER_HEAD" : config.getString("social.profile.default-icon", "PLAYER_HEAD");
        String value = party == null ? fallback : db.parties.getString(socialRoot(party) + ".icon", fallback);
        value = value == null ? "PLAYER_HEAD" : value.toUpperCase(Locale.ROOT);
        return Material.matchMaterial(value) != null && allowedIcons().contains(value) ? value : fallback.toUpperCase(Locale.ROOT);
    }

    public String visibility(String party) {
        String fallback = config == null ? "PUBLIC" : config.getString("social.profile.default-visibility", "PUBLIC");
        String value = party == null ? fallback : db.parties.getString(socialRoot(party) + ".visibility", fallback);
        return "PRIVATE".equalsIgnoreCase(value) ? "PRIVATE" : "PUBLIC";
    }

    public String activeBadgeId(String party) {
        if (party == null) return "";
        String id = db.parties.getString(socialRoot(party) + ".badge", "").toLowerCase(Locale.ROOT);
        return id.isBlank() || !achievementUnlocked(party, id) ? "" : id;
    }

    public String activeBadgeDisplay(String party) {
        String id = activeBadgeId(party);
        if (id.isBlank()) return "";
        Achievement achievement = achievement(id);
        return achievement == null ? id : achievement.display();
    }

    public int achievementCount(String party) {
        ConfigurationSection section = db.parties.getConfigurationSection(socialRoot(party) + ".achievements");
        if (section == null) return 0;
        int count = 0;
        for (String id : section.getKeys(false)) if (section.getLong(id + ".unlocked-at", 0L) > 0L) count++;
        return count;
    }

    public long activityTotal(String party) {
        if (party == null) return 0L;
        long total = 0L;
        for (int amount : progression.activityTotals(party).values()) total += Math.max(0, amount);
        return total;
    }

    public long memberSince(UUID uuid) {
        if (uuid == null) return 0L;
        String party = parties.partyOf(uuid);
        if (party == null) return 0L;
        long joined = db.parties.getLong("parties." + party + ".members." + uuid + ".joined-at", 0L);
        return joined > 0L ? joined : db.parties.getLong("parties." + party + ".created-at", 0L);
    }

    public String memberStatus(UUID uuid) {
        if (uuid == null) return "INACTIVE";
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return SocialIdentityPolicy.activityStatus(
                player.isOnline(),
                player.getLastPlayed(),
                System.currentTimeMillis(),
                config.getInt("social.member-activity.active-days", 7),
                config.getInt("social.member-activity.away-days", 30)
        );
    }

    public String profileName(String party) {
        if (party == null || !parties.exists(party)) return "";
        String tag = tag(party);
        String prefix = tag.isBlank() ? "" : "[" + tag + "] ";
        return Util.color(colorCode(colorName(party)) + prefix + parties.display(party));
    }

    // ---------------------------------------------------------------------
    // Validation / resolution / configuration
    // ---------------------------------------------------------------------

    private boolean visibleTo(CommandSender sender, String party) {
        if (!visibility(party).equals("PRIVATE")) return true;
        if (!(sender instanceof Player player)) return true;
        if (party.equals(parties.partyOf(player.getUniqueId()))) return true;
        return sender.hasPermission("menkiestesparty.social.private.bypass")
                || sender.hasPermission("menkiestesparty.admin")
                || sender.hasPermission("menkiestesparty.admin.inspect");
    }

    private boolean canEdit(Player player, String party) {
        if (!require(player, "edit")) return false;
        if (!parties.canManage(player.getUniqueId())) {
            msg(player, "&cHanya Owner/Officer yang dapat mengubah Party social profile.");
            return false;
        }
        if (!party.equals(parties.partyOf(player.getUniqueId()))) {
            msg(player, "&cParty context changed; run the command again.");
            return false;
        }
        return true;
    }

    private boolean takeEditToken(Player player) {
        long now = System.currentTimeMillis();
        long delay = Math.max(0L, Math.min(10_000L, config.getLong("social.edit-cooldown-millis", 750L)));
        Long previous = editCooldown.put(player.getUniqueId(), now);
        if (previous != null && now - previous < delay) {
            editCooldown.put(player.getUniqueId(), previous);
            msg(player, "&eTunggu sebentar sebelum mengubah profile lagi.");
            return false;
        }
        return true;
    }

    private void touchSocial(String party, Player actor) {
        String root = socialRoot(party);
        db.parties.set(root + ".updated-at", System.currentTimeMillis());
        db.parties.set(root + ".updated-by", actor.getUniqueId().toString());
        plugin.saveDataSoon();
    }

    private boolean tagInUse(String tag, String exceptParty) {
        for (String party : partyKeys()) {
            if (party.equals(exceptParty)) continue;
            if (tag.equalsIgnoreCase(tag(party))) return true;
        }
        return false;
    }

    private String resolveParty(String input) {
        if (input == null || input.isBlank()) return null;
        String key = Util.key(input);
        if (parties.exists(key)) return key;
        for (String party : partyKeys()) {
            if (parties.display(party).equalsIgnoreCase(input)) return party;
            ConfigurationSection members = db.parties.getConfigurationSection("parties." + party + ".members");
            if (members == null) continue;
            for (String uuid : members.getKeys(false)) {
                if (input.equalsIgnoreCase(members.getString(uuid + ".name", ""))) return party;
            }
        }
        return null;
    }

    private OfflinePlayer resolveMember(String party, String input) {
        if (input == null || input.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(input);
            return party.equals(parties.partyOf(uuid)) ? Bukkit.getOfflinePlayer(uuid) : null;
        } catch (Exception ignored) {}
        ConfigurationSection members = db.parties.getConfigurationSection("parties." + party + ".members");
        if (members == null) return null;
        for (String uuidText : members.getKeys(false)) {
            if (!input.equalsIgnoreCase(members.getString(uuidText + ".name", ""))) continue;
            try { return Bukkit.getOfflinePlayer(UUID.fromString(uuidText)); }
            catch (Exception ignored) { return null; }
        }
        return null;
    }

    private List<String> partyKeys() {
        ConfigurationSection root = db.parties.getConfigurationSection("parties");
        if (root == null) return List.of();
        List<String> keys = new ArrayList<>(root.getKeys(false));
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        return keys;
    }

    private List<String> profileTargets() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String party : partyKeys()) {
            if (visibility(party).equals("PRIVATE")) continue;
            values.add(parties.display(party));
            String tag = tag(party);
            if (!tag.isBlank()) values.add(tag);
        }
        return new ArrayList<>(values);
    }

    private List<String> allowedColors() {
        List<String> configured = config == null ? List.of() : config.getStringList("social.profile.allowed-colors");
        List<String> out = new ArrayList<>();
        for (String value : configured) {
            String upper = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (COLOR_CODES.containsKey(upper) && !out.contains(upper)) out.add(upper);
        }
        return out.isEmpty() ? List.of("AQUA") : List.copyOf(out);
    }

    private List<String> allowedIcons() {
        List<String> configured = config == null ? List.of() : config.getStringList("social.profile.allowed-icons");
        List<String> out = new ArrayList<>();
        for (String value : configured) {
            String upper = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (Material.matchMaterial(upper) != null && !out.contains(upper)) out.add(upper);
        }
        return out.isEmpty() ? List.of("PLAYER_HEAD") : List.copyOf(out);
    }

    private int tagMin() { return Math.max(1, config.getInt("social.profile.tag-min-length", 2)); }
    private int tagMax() { return Math.max(tagMin(), config.getInt("social.profile.tag-max-length", 5)); }
    private int descriptionMax() { return Math.max(20, Math.min(500, config.getInt("social.profile.description-max-length", 120))); }

    private String socialRoot(String party) { return "parties." + party + ".social"; }

    private String colorCode(String color) { return COLOR_CODES.getOrDefault(color.toUpperCase(Locale.ROOT), "&b"); }

    private String playerName(UUID uuid) {
        if (uuid == null) return "Unknown";
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name == null || name.isBlank() ? uuid.toString().substring(0, 8) : name;
    }

    private String playerName(OfflinePlayer player) {
        return player == null ? "Unknown" : playerName(player.getUniqueId());
    }

    private boolean require(CommandSender sender, String suffix) {
        if (!(sender instanceof Player)) return true;
        if (sender.hasPermission("menkiestesparty.social." + suffix)
                || sender.hasPermission("menkiestesparty.admin")) return true;
        msg(sender, "&cMissing permission: &fmenkiestesparty.social." + suffix);
        return false;
    }

    private void showSocialHelp(CommandSender sender) {
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&d&lPARTY SOCIAL &8- &fv1.7.0"));
        for (String line : List.of(
                "&f/party profile [party|player]",
                "&f/party social status",
                "&f/party social description <text|clear>",
                "&f/party social tag <TAG|clear>",
                "&f/party social color <color>",
                "&f/party social icon <material>",
                "&f/party social visibility <public|private> &8- Owner",
                "&f/party social badge <achievement|none>",
                "&f/party social achievements",
                "&f/party social member [player]",
                "&f/party top [reputation|level|members|projects|activity|age] [page]")) {
            sender.sendMessage(Util.color(line));
        }
    }

    private List<String> completeProfile(String[] args) {
        String typed = args.length == 0 ? "" : args[args.length - 1];
        return args.length <= 1 ? filter(profileTargets(), typed) : List.of();
    }

    private List<String> completeTop(String[] args) {
        String typed = args.length == 0 ? "" : args[args.length - 1];
        if (args.length <= 1) return filter(LEADERBOARD_METRICS, typed);
        return List.of();
    }

    private List<String> completeSocial(CommandSender sender, String[] args) {
        String typed = args.length == 0 ? "" : args[args.length - 1];
        if (args.length <= 1) {
            return filter(List.of("status", "description", "tag", "color", "icon", "visibility", "badge", "achievements", "member", "help"), typed);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("color")) return filter(allowedColors(), typed);
        if (args.length == 2 && sub.equals("icon")) return filter(allowedIcons(), typed);
        if (args.length == 2 && sub.equals("visibility")) return filter(List.of("public", "private"), typed);
        if (args.length == 2 && sub.equals("badge")) {
            List<String> values = new ArrayList<>();
            values.add("none");
            if (sender instanceof Player player) {
                String party = parties.partyOf(player.getUniqueId());
                if (party != null) {
                    refreshAchievements(party, false);
                    for (Achievement achievement : achievements()) if (achievementUnlocked(party, achievement.id())) values.add(achievement.id());
                }
            }
            return filter(values, typed);
        }
        if (args.length == 2 && sub.equals("member") && sender instanceof Player player) {
            String party = parties.partyOf(player.getUniqueId());
            if (party == null) return List.of();
            List<String> values = new ArrayList<>();
            for (UUID uuid : parties.members(party)) values.add(playerName(uuid));
            return filter(values, typed);
        }
        return List.of();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!enabled()) return List.of();
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "partyprofile" -> completeProfile(args);
            case "partysocial" -> completeSocial(sender, args);
            case "partytop" -> completeTop(args);
            default -> List.of();
        };
    }

    private static <T> List<String> filter(Iterable<T> values, String typed) {
        String q = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (T raw : values) {
            String value = String.valueOf(raw);
            if (value.toLowerCase(Locale.ROOT).startsWith(q) && out.stream().noneMatch(x -> x.equalsIgnoreCase(value))) out.add(value);
        }
        return out.stream().limit(100).toList();
    }

    private static int parsePage(String raw) {
        try { return Math.max(1, Integer.parseInt(raw)); }
        catch (Exception ignored) { return 1; }
    }

    private static String root(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        return colon >= 0 ? lower.substring(colon + 1) : lower;
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(parties.prefix() + Util.color(" " + text));
    }

    private void loadConfig() {
        try {
            if (!Files.exists(configPath)) plugin.saveResource("social.yml", false);
            YamlConfiguration live = YamlConfiguration.loadConfiguration(configPath.toFile());
            try (InputStream input = plugin.getResource("social.yml")) {
                if (input != null) {
                    try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                        live.setDefaults(defaults);
                        live.options().copyDefaults(true);
                        live.save(configPath.toFile());
                    }
                }
            }
            this.config = live;
        } catch (Exception failure) {
            plugin.getLogger().warning("Could not load/merge social.yml: " + failure.getMessage());
            this.config = YamlConfiguration.loadConfiguration(configPath.toFile());
        }
    }
}
