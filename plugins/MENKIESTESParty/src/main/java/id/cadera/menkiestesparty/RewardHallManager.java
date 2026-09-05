package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

        ItemStack reward = consumeNextPlannedItem();
        if (reward != null && reward.getType() != Material.AIR) {
            db.hall.set("entries." + id + ".item", reward.clone());
        } else {
            db.hall.set("entries." + id + ".item", null);
            plugin.getLogger().warning("Reward Hall " + id + " dibuat tanpa planned item. Admin bisa recovery dengan /partyhall setcurrent sambil memegang item.");
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

    public void setPlannedItem(Player admin, int slot) {
        if (!requireAdmin(admin)) return;
        int max = maxPlanned();
        if (slot < 1 || slot > max) {
            admin.sendMessage(parties.prefix() + Util.color(" &cSlot harus 1-" + max + "."));
            return;
        }
        ItemStack held = heldItem(admin);
        if (held == null) return;
        if (slot > 1 && plannedItem(slot - 1) == null) {
            admin.sendMessage(parties.prefix() + Util.color(" &cIsi slot #" + (slot - 1) + " dulu agar antrean tidak berlubang."));
            return;
        }
        db.hall.set(queuePath(slot), held.clone());
        plugin.saveDataSoon();
        admin.sendMessage(parties.prefix() + Util.color(" &aReward War #" + slot + " diset: &f" + itemName(held) + " &7x" + held.getAmount()));
    }

    public void appendPlannedItem(Player admin) {
        if (!requireAdmin(admin)) return;
        ItemStack held = heldItem(admin);
        if (held == null) return;
        int slot = plannedCount() + 1;
        if (slot > maxPlanned()) {
            admin.sendMessage(parties.prefix() + Util.color(" &cAntrean Reward Hall penuh. Maksimal " + maxPlanned() + " War ke depan."));
            return;
        }
        db.hall.set(queuePath(slot), held.clone());
        plugin.saveDataSoon();
        admin.sendMessage(parties.prefix() + Util.color(" &aDitambahkan ke antrean War #" + slot + ": &f" + itemName(held) + " &7x" + held.getAmount()));
    }

    public void removePlannedItem(Player admin, int slot) {
        if (!requireAdmin(admin)) return;
        int count = plannedCount();
        if (slot < 1 || slot > count || plannedItem(slot) == null) {
            admin.sendMessage(parties.prefix() + Util.color(" &cSlot reward tidak ada."));
            return;
        }
        db.hall.set(queuePath(slot), null);
        compactQueue();
        plugin.saveDataSoon();
        admin.sendMessage(parties.prefix() + Util.color(" &eReward slot #" + slot + " dihapus. Antrean dirapikan."));
    }

    public void clearPlannedItems(Player admin) {
        if (!requireAdmin(admin)) return;
        db.hall.set("reward-queue", null);
        db.hall.set("reward-item", null);
        plugin.saveDataSoon();
        admin.sendMessage(parties.prefix() + Util.color(" &eSemua planned Reward Hall dihapus."));
    }

    public void setCurrentItem(Player admin) {
        if (!requireAdmin(admin)) return;
        String base = "entries." + currentId();
        if (!db.hall.getBoolean(base + ".awarded", false)) {
            admin.sendMessage(parties.prefix() + Util.color(" &cBelum ada Reward Hall aktif Week ini."));
            return;
        }
        ItemStack held = heldItem(admin);
        if (held == null) return;
        db.hall.set(base + ".item", held.clone());
        plugin.saveDataSoon();
        admin.sendMessage(parties.prefix() + Util.color(" &aItem Hall aktif berhasil direcovery: &f" + itemName(held) + " &7x" + held.getAmount()));
    }

    public void showPlan(Player player) {
        if (!player.hasPermission("menkiestesparty.admin")) {
            player.sendMessage(Util.color("&cNo permission."));
            return;
        }
        player.sendMessage(Util.color("&8&m-----------------------------"));
        player.sendMessage(Util.color("&6&lREWARD HALL PLAN"));
        int count = plannedCount();
        if (count == 0) {
            ItemStack legacy = db.hall.getItemStack("reward-item");
            if (legacy != null && legacy.getType() != Material.AIR) {
                player.sendMessage(Util.color("&eLegacy next reward: &f" + itemName(legacy) + " &7x" + legacy.getAmount()));
            } else {
                player.sendMessage(Util.color("&7Belum ada reward yang disiapkan."));
            }
            player.sendMessage(Util.color("&7Pegang item lalu: &f/partyhall additem"));
            return;
        }
        for (int i = 1; i <= count; i++) {
            ItemStack item = plannedItem(i);
            if (item == null) continue;
            player.sendMessage(Util.color("&e#" + i + " &8- &f" + itemName(item) + " &7x" + item.getAmount() + (i == 1 ? " &a(NEXT WAR)" : "")));
        }
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
            if (player.hasPermission("menkiestesparty.admin")) {
                ItemStack next = peekNextReward();
                player.sendMessage(Util.color("&7Next planned reward: &f" + (next == null ? "Belum diset" : itemName(next) + " x" + next.getAmount())));
                player.sendMessage(Util.color("&7Planned Wars: &f" + plannedCount()));
            }
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

    private ItemStack consumeNextPlannedItem() {
        ItemStack first = plannedItem(1);
        if (first != null) {
            db.hall.set(queuePath(1), null);
            compactQueue();
            return first.clone();
        }
        ItemStack legacy = db.hall.getItemStack("reward-item");
        if (legacy != null && legacy.getType() != Material.AIR) {
            db.hall.set("reward-item", null);
            return legacy.clone();
        }
        return null;
    }

    private ItemStack peekNextReward() {
        ItemStack first = plannedItem(1);
        if (first != null) return first;
        ItemStack legacy = db.hall.getItemStack("reward-item");
        return legacy != null && legacy.getType() != Material.AIR ? legacy : null;
    }

    private ItemStack plannedItem(int slot) {
        ItemStack item = db.hall.getItemStack(queuePath(slot));
        return item == null || item.getType() == Material.AIR ? null : item;
    }

    private int plannedCount() {
        ConfigurationSection sec = db.hall.getConfigurationSection("reward-queue");
        if (sec == null) return 0;
        int count = 0;
        for (String key : sec.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                if (plannedItem(slot) != null) count++;
            } catch (NumberFormatException ignored) {}
        }
        return count;
    }

    private void compactQueue() {
        ConfigurationSection sec = db.hall.getConfigurationSection("reward-queue");
        if (sec == null) return;
        List<Integer> slots = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            try { slots.add(Integer.parseInt(key)); } catch (NumberFormatException ignored) {}
        }
        slots.sort(Comparator.naturalOrder());
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = plannedItem(slot);
            if (item != null) items.add(item.clone());
        }
        db.hall.set("reward-queue", null);
        for (int i = 0; i < items.size(); i++) db.hall.set(queuePath(i + 1), items.get(i));
    }

    private String queuePath(int slot) { return "reward-queue." + slot; }
    private int maxPlanned() { return Math.max(1, plugin.getConfig().getInt("reward-hall.max-planned-rewards", 12)); }

    private ItemStack heldItem(Player admin) {
        ItemStack held = admin.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            admin.sendMessage(parties.prefix() + Util.color(" &cPegang item Reward Hall di tangan utama dulu."));
            return null;
        }
        return held;
    }

    private boolean requireAdmin(Player admin) {
        if (admin.hasPermission("menkiestesparty.admin")) return true;
        admin.sendMessage(Util.color("&cNo permission."));
        return false;
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
