package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PartyManageGui implements Listener {
    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;

    public PartyManageGui(MENKIESTESPartyPlugin plugin, PartyService parties) {
        this.plugin = plugin;
        this.parties = parties;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!"MENKIESTES Party".equalsIgnoreCase(Util.strip(event.getView().getTitle()))) return;
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) return;

        Inventory inv = event.getInventory();
        inv.setItem(11, Util.item(Material.EMERALD, "&aInvite Player",
                parties.canManage(player.getUniqueId()) ? "&7Klik untuk pilih player online." : "&cHanya Owner/Officer."));
        inv.setItem(13, Util.item(Material.CHEST, "&eManage Members",
                "&7Lihat roster Party.", parties.canManage(player.getUniqueId()) ? "&7Klik member untuk manage." : "&7Mode lihat saja."));
        inv.setItem(15, Util.item(Material.ENDER_CHEST, "&6Party Reward Hall",
                "&7Lihat reward Hall dan status claim."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        String title = Util.strip(event.getView().getTitle());
        if (title == null) return;

        if (title.equalsIgnoreCase("MENKIESTES Party")) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getRawSlot() == 11 || event.getRawSlot() == 13 || event.getRawSlot() == 15) event.setCancelled(true);
            if (event.getRawSlot() == 11) openInviteMenu(player);
            else if (event.getRawSlot() == 13) openMemberMenu(player);
            else if (event.getRawSlot() == 15) { player.closeInventory(); plugin.hall().show(player); }
            return;
        }

        if (title.equalsIgnoreCase("Party Invite")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getRawSlot() == 49) { parties.openMenu(player); return; }
            UUID targetId = taggedUuid(event.getCurrentItem());
            if (targetId == null) return;
            if (plugin.war().membershipLocked()) {
                player.sendMessage(parties.prefix() + Util.color(" &cRoster Party dikunci selama Party War prepare/aktif."));
                parties.openMenu(player);
                return;
            }
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                player.sendMessage(parties.prefix() + Util.color(" &cPlayer sudah offline."));
                openInviteMenu(player);
                return;
            }
            parties.invite(player, target);
            openInviteMenu(player);
            return;
        }

        if (title.equalsIgnoreCase("Party Members")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getRawSlot() == 49) { parties.openMenu(player); return; }
            UUID targetId = taggedUuid(event.getCurrentItem());
            if (targetId == null) return;
            if (canManageTarget(player.getUniqueId(), targetId)) openMemberActions(player, targetId);
            return;
        }

        if (title.equalsIgnoreCase("Manage Member")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getRawSlot() == 22) { openMemberMenu(player); return; }
            UUID targetId = taggedUuid(event.getCurrentItem());
            if (targetId == null || !canManageTarget(player.getUniqueId(), targetId)) {
                if (targetId != null) player.sendMessage(parties.prefix() + Util.color(" &cAkses manage sudah tidak valid."));
                openMemberMenu(player);
                return;
            }

            if (event.getRawSlot() == 11 && parties.role(player.getUniqueId()) == PartyService.Role.OWNER) {
                PartyService.Role current = parties.role(targetId);
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
                parties.setRole(player, target, current == PartyService.Role.OFFICER ? PartyService.Role.MEMBER : PartyService.Role.OFFICER);
                openMemberMenu(player);
            } else if (event.getRawSlot() == 15) {
                parties.kick(player, Bukkit.getOfflinePlayer(targetId));
                openMemberMenu(player);
            }
        }
    }

    public void openInviteMenu(Player player) {
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) { player.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party.")); return; }
        if (!parties.canManage(player.getUniqueId())) { player.sendMessage(parties.prefix() + Util.color(" &cHanya Owner/Officer yang dapat invite.")); return; }
        if (plugin.war().membershipLocked()) { player.sendMessage(parties.prefix() + Util.color(" &cRoster Party dikunci selama Party War prepare/aktif.")); return; }

        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8Party Invite"));
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player) || parties.inParty(online.getUniqueId())) continue;
            targets.add(online);
        }
        targets.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int slot = 0;
        for (Player target : targets) {
            if (slot >= 45) break;
            inv.setItem(slot++, taggedItem(Material.PLAYER_HEAD, "&a" + target.getName(), target.getUniqueId(),
                    "&7Klik untuk invite ke &f" + parties.display(party)));
        }
        if (targets.isEmpty()) inv.setItem(22, Util.item(Material.BARRIER, "&cTidak ada player tersedia", "&7Semua player online sudah punya Party."));
        inv.setItem(49, Util.item(Material.ARROW, "&eKembali", "&7Kembali ke menu Party."));
        player.openInventory(inv);
    }

    public void openMemberMenu(Player player) {
        String party = parties.partyOf(player.getUniqueId());
        if (party == null) { player.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party.")); return; }

        Inventory inv = Bukkit.createInventory(null, 54, Util.color("&8Party Members"));
        List<UUID> members = new ArrayList<>(parties.members(party));
        members.sort((a, b) -> {
            int roleCompare = Integer.compare(roleOrder(parties.role(a)), roleOrder(parties.role(b)));
            if (roleCompare != 0) return roleCompare;
            return memberName(a).compareToIgnoreCase(memberName(b));
        });

        int slot = 0;
        for (UUID uuid : members) {
            if (slot >= 45) break;
            PartyService.Role targetRole = parties.role(uuid);
            boolean manageable = canManageTarget(player.getUniqueId(), uuid);
            inv.setItem(slot++, taggedItem(Material.PLAYER_HEAD, "&b" + memberName(uuid), uuid,
                    "&7Role: &f" + (targetRole == null ? "MEMBER" : targetRole.name()),
                    uuid.equals(player.getUniqueId()) ? "&8Ini kamu." : (manageable ? "&aKlik untuk manage." : "&8Tidak dapat dikelola oleh role kamu.")));
        }
        inv.setItem(49, Util.item(Material.ARROW, "&eKembali", "&7Kembali ke menu Party."));
        player.openInventory(inv);
    }

    private void openMemberActions(Player actor, UUID target) {
        if (!canManageTarget(actor.getUniqueId(), target)) return;
        PartyService.Role targetRole = parties.role(target);
        Inventory inv = Bukkit.createInventory(null, 27, Util.color("&8Manage Member"));
        inv.setItem(13, taggedItem(Material.PLAYER_HEAD, "&b" + memberName(target), target, "&7Role: &f" + targetRole));

        if (parties.role(actor.getUniqueId()) == PartyService.Role.OWNER) {
            if (targetRole == PartyService.Role.MEMBER) {
                inv.setItem(11, taggedItem(Material.LIME_DYE, "&aPromote Officer", target, "&7Jadikan member sebagai Officer."));
            } else if (targetRole == PartyService.Role.OFFICER) {
                inv.setItem(11, taggedItem(Material.YELLOW_DYE, "&eDemote Member", target, "&7Turunkan Officer menjadi Member."));
            }
        }
        inv.setItem(15, taggedItem(Material.BARRIER, "&cKick Member", target, "&7Keluarkan dari Party."));
        inv.setItem(22, Util.item(Material.ARROW, "&eKembali", "&7Kembali ke roster."));
        actor.openInventory(inv);
    }

    private boolean canManageTarget(UUID actor, UUID target) {
        String party = parties.partyOf(actor);
        if (party == null || !party.equals(parties.partyOf(target)) || actor.equals(target) || target.equals(parties.owner(party))) return false;
        PartyService.Role actorRole = parties.role(actor);
        PartyService.Role targetRole = parties.role(target);
        if (actorRole == PartyService.Role.OWNER) return true;
        return actorRole == PartyService.Role.OFFICER && targetRole == PartyService.Role.MEMBER;
    }

    private ItemStack taggedItem(Material material, String name, UUID uuid, String... lore) {
        String[] taggedLore = Arrays.copyOf(lore, lore.length + 1);
        taggedLore[lore.length] = "&0ID:" + uuid;
        return Util.item(material, name, taggedLore);
    }

    private UUID taggedUuid(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        var meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return null;
        for (String line : meta.getLore()) {
            String clean = Util.strip(line);
            if (clean != null && clean.startsWith("ID:")) {
                try { return UUID.fromString(clean.substring(3)); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private int roleOrder(PartyService.Role role) {
        if (role == PartyService.Role.OWNER) return 0;
        if (role == PartyService.Role.OFFICER) return 1;
        return 2;
    }

    private String memberName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }
}
