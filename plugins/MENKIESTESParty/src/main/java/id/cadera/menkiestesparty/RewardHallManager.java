package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public final class RewardHallManager {
    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final StorageBundle db;

    public RewardHallManager(MENKIESTESPartyPlugin plugin, PartyService parties, StorageBundle db) {
        this.plugin = plugin;
        this.parties = parties;
        this.db = db;
    }

    public void awardWar(String party, String runId, Collection<UUID> eligiblePlayers) {
        if (!plugin.getConfig().getBoolean("reward-hall.enabled", true)) return;

        String id = currentId();
        if (db.hall.getBoolean("entries." + id + ".awarded", false)) return;

        db.hall.set("entries." + id + ".awarded", true);
        db.hall.set("entries." + id + ".party", party);
        db.hall.set("entries." + id + ".run-id", runId);
        db.hall.set("entries." + id + ".awarded-at", System.currentTimeMillis());

        ItemStack configured = db.hall.getItemStack("reward-item");
        if (configured != null && configured.getType() != Material.AIR) {
            db.hall.set("entries." + id + ".item", configured.clone());
        } else {
            db.hall.set("entries." + id + ".item", null);
            plugin.getLogger().warning("Reward Hall " + id + " dibuat tanpa item. Gunakan /partyhall setitem sebelum Party War berikutnya.");
        }

        int count = 0;
        if (eligiblePlayers != null) {
            for (UUID uuid : eligiblePlayers) {
                db.hall.set("entries." + id + ".eligible." + uuid, true);
                db.hall.set("entries." + id + ".claims." + uuid, false);
                count++;
            }
        }
        db.hall.set("entries." + id + ".eligible-count", count);
        plugin.saveDataSoon();

        parties.broadcastParty(party, parties.prefix() + Util.color(
                " &6Reward Hall tersedia untuk &f" + count + " participant eligible&6. Claim: &f/partyhall claim"));
    }

    public void setRewardItem(Player admin) {
        if (!admin.hasPermission("menkiestesparty.admin")) {
            admin.sendMessage(Util.color("&cNo permission."));
            return;
        }
        ItemStack held = admin.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            admin.sendMessage(parties.prefix() + Util.color(" &cPegang item Reward Hall di tangan utama dulu."));
            return;
        }
        db.hall.set("reward-item", held.clone());
        plugin.saveDataSoon();
        admin.sendMessage(parties.prefix() + Util.color(" &aReward Hall item diset: &f" + itemName(held) + " &7x" + held.getAmount()));
    }

    public void clearRewardItem(Player admin) {
        if (!admin.hasPermission("menkiestesparty.admin")) {
            admin.sendMessage(Util.color("&cNo permission."));
            return;
        }
        db.hall.set("reward-item", null);
        plugin.saveDataSoon();
        admin.sendMessage(parties.prefix() + Util.color(" &eReward Hall item dihapus."));
    }

    public void showConfiguredItem(Player player) {
        ItemStack item = db.hall.getItemStack("reward-item");
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(parties.prefix() + Util.color(" &7Reward Hall item: &cBelum diset"));
            return;
        }
        player.sendMessage(parties.prefix() + Util.color(" &7Reward Hall item: &f" + itemName(item) + " &7x" + item.getAmount()));
    }

    public void claim(Player player) {
        String id = currentId();
        String base = "entries." + id;
        UUID uuid = player.getUniqueId();

        String winner = db.hall.getString(base + ".party");
        if (winner == null) {
            player.sendMessage(parties.prefix() + Util.color(" &cBelum ada Reward Hall aktif untuk Week ini."));
            return;
        }

        if (!db.hall.getBoolean(base + ".eligible." + uuid, false)) {
            player.sendMessage(parties.prefix() + Util.color(" &cKamu tidak eligible Reward Hall. Harus tercatat ikut combat dan memenuhi minimal waktu partisipasi Party War."));
            return;
        }

        if (db.hall.getBoolean(base + ".claims." + uuid, false)) {
            player.sendMessage(parties.prefix() + Util.color(" &cReward Hall Week ini sudah kamu claim."));
            return;
        }

        ItemStack reward = db.hall.getItemStack(base + ".item");
        if (reward == null || reward.getType() == Material.AIR) {
            player.sendMessage(parties.prefix() + Util.color(" &cItem Reward Hall untuk Week ini belum tersedia. Hubungi admin."));
            return;
        }

        ItemStack give = reward.clone();
        var overflow = player.getInventory().addItem(give);
        for (ItemStack item : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }

        db.hall.set(base + ".claims." + uuid, true);
        db.hall.set(base + ".claimed-at." + uuid, System.currentTimeMillis());
        plugin.saveDataSoon();
        player.sendMessage(parties.prefix() + Util.color(" &aReward Hall berhasil diclaim: &f" + itemName(give) + " &7x" + give.getAmount()));
    }

    public void show(Player player) {
        String id = currentId();
        String base = "entries." + id;
        player.sendMessage(Util.color("&8&m-----------------------------"));
        player.sendMessage(Util.color("&6&lPARTY REWARD HALL &7- &f" + id));

        String winner = db.hall.getString(base + ".party");
        if (winner == null) {
            player.sendMessage(Util.color("&7Belum ada reward untuk Week ini."));
            showConfiguredItem(player);
            return;
        }

        player.sendMessage(Util.color("&7Pemenang: &b" + parties.display(winner)));
        ItemStack reward = db.hall.getItemStack(base + ".item");
        player.sendMessage(Util.color("&7Item: &f" + (reward == null ? "Belum tersedia" : itemName(reward) + " x" + reward.getAmount())));
        player.sendMessage(Util.color("&7Eligible participant: &f" + db.hall.getInt(base + ".eligible-count", 0)));

        UUID uuid = player.getUniqueId();
        boolean eligible = db.hall.getBoolean(base + ".eligible." + uuid, false);
        boolean claimed = db.hall.getBoolean(base + ".claims." + uuid, false);
        player.sendMessage(Util.color("&7Status kamu: " + (eligible ? "&aELIGIBLE" : "&cTIDAK ELIGIBLE") + " &8| &7Claimed: &f" + claimed));

        String runId = db.hall.getString(base + ".run-id");
        if (runId != null) {
            String historyBase = findWarHistoryByRunId(runId);
            if (historyBase != null) {
                String pbase = historyBase + ".participants." + uuid;
                int minutes = db.wars.getInt(pbase + ".minutes", 0);
                int kills = db.wars.getInt(pbase + ".kills", 0);
                int points = db.wars.getInt(pbase + ".points", 0);
                int deaths = db.wars.getInt(pbase + ".deaths", 0);
                boolean combat = db.wars.getBoolean(pbase + ".combat", false);
                player.sendMessage(Util.color("&7Kontribusi: &f" + minutes + "m &8| &f" + kills + " kill &8| &f" + points + " poin &8| &f" + deaths + " death &8| &7Combat: &f" + combat));
            }
        }
    }

    private String findWarHistoryByRunId(String runId) {
        int seq = db.wars.getInt("history-seq", 0);
        for (int i = seq; i >= 1; i--) {
            String base = "history." + i;
            if (runId.equals(db.wars.getString(base + ".run-id"))) return base;
        }
        return null;
    }

    private String currentId() {
        LocalDate now = LocalDate.now();
        int week = Math.min(4, Math.max(1, (now.getDayOfMonth() - 1) / 7 + 1));
        return now.toString().substring(0, 7) + "-W" + week;
    }

    private String itemName(ItemStack item) {
        if (item == null) return "Unknown";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return item.getType().name();
    }
}
