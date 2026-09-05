package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class WarManager {
    public enum Phase { NONE, PREPARE, ACTIVE }
    private record CombatTag(UUID attacker, String attackerParty, long expiresAt, boolean scoreEligible) {}

    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final StorageBundle db;
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Map<String, Integer> kills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sessionKills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sessionPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sessionDeaths = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> participationMinutes = new ConcurrentHashMap<>();
    private final Map<String, Long> victimCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, CombatTag> combat = new ConcurrentHashMap<>();
    private Phase phase = Phase.NONE;
    private long phaseEndsAt = 0;
    private int durationMinutes = 30;
    private int targetPoints = 30;
    private String runId;

    public WarManager(MENKIESTESPartyPlugin plugin, PartyService parties, StorageBundle db) {
        this.plugin = plugin;
        this.parties = parties;
        this.db = db;
        restoreSafeState();
    }

    private void restoreSafeState() {
        String old = db.wars.getString("runtime.phase", "NONE");
        if (!"NONE".equalsIgnoreCase(old)) {
            plugin.getLogger().warning("Party War sebelumnya tidak selesai bersih. Runtime direset ke NONE agar aman.");
        }
        db.wars.set("runtime", null);
    }

    public Phase phase() { return phase; }
    public boolean active() { return phase == Phase.ACTIVE; }
    public boolean membershipLocked() {
        return plugin.getConfig().getBoolean("war.lock-membership-during-active", true) && phase != Phase.NONE;
    }

    public void start(int duration, int target, int prepareMinutes) {
        if (phase != Phase.NONE) return;
        this.durationMinutes = Math.max(1, duration);
        this.targetPoints = Math.max(1, target);
        this.runId = "WAR-" + System.currentTimeMillis();
        clearRuntime();
        if (prepareMinutes > 0) {
            phase = Phase.PREPARE;
            phaseEndsAt = System.currentTimeMillis() + prepareMinutes * 60_000L;
            Bukkit.broadcastMessage(parties.prefix() + Util.color(" &eParty War prepare dimulai selama &f" + prepareMinutes + " menit&e. Tidak ada teleport; War akan berlangsung langsung di &f" + scoreWorld() + "&e."));
        } else {
            activate();
        }
        persistRuntime();
    }

    private void activate() {
        phase = Phase.ACTIVE;
        phaseEndsAt = System.currentTimeMillis() + durationMinutes * 60_000L;
        Bukkit.broadcastMessage(parties.prefix() + Util.color(" &c&lPARTY WAR DIMULAI! &7Durasi &f" + durationMinutes + "m &8| &7Target &f" + targetPoints + " &8| &7World &f" + scoreWorld()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            String party = parties.partyOf(p.getUniqueId());
            if (party != null && !p.isOp()) participationMinutes.putIfAbsent(p.getUniqueId(), 0);
        }
        persistRuntime();
    }

    public void cancel(String reason) {
        if (phase == Phase.NONE) return;
        Bukkit.broadcastMessage(parties.prefix() + Util.color(" &cParty War dibatalkan" + (reason == null ? "." : ": &f" + reason)));
        phase = Phase.NONE; phaseEndsAt = 0; clearRuntime(); persistRuntime();
    }

    public void finishManual() {
        if (phase != Phase.ACTIVE) return;
        end(selectWinner(), "manual");
    }

    public void tickSecond() {
        long now = System.currentTimeMillis();
        if (phase == Phase.PREPARE && now >= phaseEndsAt) activate();
        else if (phase == Phase.ACTIVE && now >= phaseEndsAt) end(selectWinner(), "time");
        combat.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        victimCooldowns.entrySet().removeIf(e -> e.getValue() < now);
        if (phase != Phase.NONE) persistRuntime();
    }

    public void tickMinute() {
        if (!active()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) continue;
            String party = parties.partyOf(p.getUniqueId());
            if (party == null) continue;
            participationMinutes.merge(p.getUniqueId(), 1, Integer::sum);
        }
    }

    public boolean isEnemy(Player a, Player b) {
        if (!active()) return false;
        String pa = parties.partyOf(a.getUniqueId()), pb = parties.partyOf(b.getUniqueId());
        return pa != null && pb != null && !pa.equals(pb) && !a.isOp() && !b.isOp();
    }

    public boolean scoreWorld(Player p) {
        return p.getWorld() != null && p.getWorld().getName().equalsIgnoreCase(scoreWorld());
    }

    public String scoreWorld() { return plugin.getConfig().getString("war.score-world", "world"); }

    public void registerCombat(Player attacker, Player victim) {
        if (!isEnemy(attacker, victim)) return;
        long exp = System.currentTimeMillis() + plugin.getConfig().getLong("war.combat-logout-window-seconds", 20) * 1000L;
        boolean eligible = scoreWorld(attacker) && scoreWorld(victim);
        combat.put(victim.getUniqueId(), new CombatTag(attacker.getUniqueId(), parties.partyOf(attacker.getUniqueId()), exp, eligible));
    }

    public void onKill(Player killer, Player victim) {
        if (!active() || killer == null || victim == null || killer.equals(victim)) return;
        if (!scoreWorld(killer) || !scoreWorld(victim) || !isEnemy(killer, victim)) return;
        if (killer.isOp() || victim.isOp()) return;
        String cdKey = killer.getUniqueId() + "::" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        if (victimCooldowns.getOrDefault(cdKey, 0L) > now) {
            killer.sendMessage(parties.prefix() + Util.color(" &7Kill player yang sama masih cooldown, tidak menambah poin War."));
            return;
        }
        victimCooldowns.put(cdKey, now + plugin.getConfig().getLong("war.same-victim-cooldown-minutes", 5) * 60_000L);
        String kp = parties.partyOf(killer.getUniqueId());
        int points = victim.getUniqueId().equals(parties.owner(parties.partyOf(victim.getUniqueId())))
                ? plugin.getConfig().getInt("war.owner-kill-points", 2)
                : plugin.getConfig().getInt("war.kill-points", 1);
        addScore(kp, points, killer, victim, false);
        sessionKills.merge(killer.getUniqueId(), 1, Integer::sum);
        sessionPoints.merge(killer.getUniqueId(), points, Integer::sum);
        sessionDeaths.merge(victim.getUniqueId(), 1, Integer::sum);
        combat.remove(victim.getUniqueId());
    }

    public void onQuit(Player victim) {
        if (!active() || !plugin.getConfig().getBoolean("war.combat-logout-points", true)) return;
        CombatTag tag = combat.remove(victim.getUniqueId());
        if (tag == null || tag.expiresAt() < System.currentTimeMillis() || !tag.scoreEligible() || !scoreWorld(victim)) return;
        Player attacker = Bukkit.getPlayer(tag.attacker());
        if (attacker == null || attacker.isOp()) return;
        String victimParty = parties.partyOf(victim.getUniqueId());
        if (victimParty == null || victimParty.equals(tag.attackerParty())) return;
        String cdKey = attacker.getUniqueId() + "::" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        if (victimCooldowns.getOrDefault(cdKey, 0L) > now) return;
        victimCooldowns.put(cdKey, now + plugin.getConfig().getLong("war.same-victim-cooldown-minutes", 5) * 60_000L);
        int points = victim.getUniqueId().equals(parties.owner(victimParty))
                ? plugin.getConfig().getInt("war.owner-kill-points", 2)
                : plugin.getConfig().getInt("war.kill-points", 1);
        addScore(tag.attackerParty(), points, attacker, victim, true);
        sessionKills.merge(attacker.getUniqueId(), 1, Integer::sum);
        sessionPoints.merge(attacker.getUniqueId(), points, Integer::sum);
        sessionDeaths.merge(victim.getUniqueId(), 1, Integer::sum);
    }

    private void addScore(String party, int points, Player killer, Player victim, boolean logout) {
        scores.merge(party, points, Integer::sum); kills.merge(party, 1, Integer::sum);
        Bukkit.broadcastMessage(parties.prefix() + Util.color(" &cWAR &8» &b" + parties.display(party) + " &e+" + points + " poin &7(" + killer.getName() + (logout ? " menang combat logout " : " mengalahkan ") + victim.getName() + ") &8| &f" + scores.get(party) + "/" + targetPoints));
        if (scores.get(party) >= targetPoints) end(party, "target");
    }

    private String selectWinner() {
        return scores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private void end(String winner, String reason) {
        if (phase != Phase.ACTIVE) return;
        phase = Phase.NONE;
        int best = winner == null ? 0 : scores.getOrDefault(winner, 0);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        int id = db.wars.getInt("history-seq", 0) + 1; db.wars.set("history-seq", id);
        String base = "history." + id;
        db.wars.set(base + ".run-id", runId); db.wars.set(base + ".date", stamp); db.wars.set(base + ".winner", winner);
        db.wars.set(base + ".winner-display", winner == null ? "DRAW" : parties.display(winner)); db.wars.set(base + ".score", best); db.wars.set(base + ".reason", reason);
        for (Map.Entry<String,Integer> e : scores.entrySet()) db.wars.set(base + ".scores." + e.getKey(), e.getValue());
        if (winner != null) {
            int rep = plugin.getConfig().getInt("war.reward.reputation", 500);
            parties.addRep(winner, rep);
            int chest = plugin.getConfig().getInt("war.reward.chests-per-member", 1);
            int minMinutes = plugin.getConfig().getInt("war.min-participation-minutes", 5);
            for (UUID u : parties.members(winner)) {
                if (participationMinutes.getOrDefault(u, 0) >= minMinutes) {
                    String path = "players." + u + ".war-chests";
                    int current = db.parties.getInt(path, 0);
                    db.parties.set(path, Math.min(16, current + chest));
                }
            }
            plugin.season().recordWarWin(winner, plugin.getConfig().getInt("war.season-points", 10));
            Bukkit.broadcastMessage(parties.prefix() + Util.color(" &6&lPARTY WAR SELESAI! &fPemenang: &b" + parties.display(winner) + " &8| &e" + best + " poin &8| &a+" + rep + " Rep"));
            plugin.hall().awardWar(winner, runId);
        } else {
            Bukkit.broadcastMessage(parties.prefix() + Util.color(" &6&lPARTY WAR SELESAI! &7Tidak ada pemenang."));
        }
        clearRuntime(); persistRuntime(); plugin.saveDataSoon();
    }

    public void showStatus(Player p) {
        long secs = Math.max(0, (phaseEndsAt - System.currentTimeMillis()) / 1000L);
        p.sendMessage(Util.color("&8&m-----------------------------"));
        p.sendMessage(Util.color("&c&lPARTY WAR STATUS"));
        p.sendMessage(Util.color("&7Phase: &f" + phase + " &8| &7World: &f" + scoreWorld()));
        if (phase != Phase.NONE) p.sendMessage(Util.color("&7Sisa: &f" + (secs/60) + "m " + (secs%60) + "s &8| &7Target: &f" + targetPoints));
        showTop(p);
    }

    public void showTop(Player p) {
        if (scores.isEmpty()) { p.sendMessage(Util.color("&7Belum ada poin Party War.")); return; }
        List<Map.Entry<String,Integer>> list = new ArrayList<>(scores.entrySet()); list.sort((a,b)->Integer.compare(b.getValue(),a.getValue()));
        int i=1; for (Map.Entry<String,Integer> e : list.subList(0,Math.min(10,list.size()))) p.sendMessage(Util.color("&e#"+i+++" &b"+parties.display(e.getKey())+" &7- &f"+e.getValue()+" poin"));
    }

    public void hunt(Player p) {
        if (!active()) { p.sendMessage(parties.prefix()+Util.color(" &cParty War belum aktif.")); return; }
        String own = parties.partyOf(p.getUniqueId()); if (own == null) { p.sendMessage(parties.prefix()+Util.color(" &cKamu belum punya Party.")); return; }
        Player nearest = null; double dist = Double.MAX_VALUE;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p) || other.isOp() || !scoreWorld(other) || !scoreWorld(p)) continue;
            String op = parties.partyOf(other.getUniqueId()); if (op == null || op.equals(own)) continue;
            double d = p.getLocation().distanceSquared(other.getLocation()); if (d < dist) { dist=d; nearest=other; }
        }
        if (nearest == null) { p.sendMessage(parties.prefix()+Util.color(" &7Tidak ada musuh online di world War.")); return; }
        p.setCompassTarget(nearest.getLocation());
        p.sendMessage(parties.prefix()+Util.color(" &cTracker diarahkan ke musuh terdekat: &f"+nearest.getName()+" &7("+(int)Math.sqrt(dist)+" block)"));
    }

    public int score(String party) { return scores.getOrDefault(party, 0); }

    private void persistRuntime() {
        db.wars.set("runtime.phase", phase.name()); db.wars.set("runtime.ends-at", phaseEndsAt); db.wars.set("runtime.duration", durationMinutes); db.wars.set("runtime.target", targetPoints); db.wars.set("runtime.run-id", runId);
    }

    private void clearRuntime() {
        scores.clear(); kills.clear(); sessionKills.clear(); sessionPoints.clear(); sessionDeaths.clear(); participationMinutes.clear(); victimCooldowns.clear(); combat.clear();
        db.wars.set("runtime", null);
    }
}
