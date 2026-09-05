package id.cadera.menkiestesparty;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class PartyListener implements Listener {
    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final WarManager war;

    public PartyListener(MENKIESTESPartyPlugin plugin, PartyService parties, WarManager war) {
        this.plugin=plugin; this.parties=parties; this.war=war;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (parties.shouldTrackPlaced(e.getBlockPlaced().getType())) parties.trackPlacedMining(e.getBlockPlaced().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p=e.getPlayer(); String party=parties.partyOf(p.getUniqueId()); if(party==null)return;
        Block b=e.getBlock(); Material type=b.getType();
        boolean placed = parties.shouldTrackPlaced(type) && parties.consumePlacedMining(b.getLocation());
        if(parties.isMiningMaterial(type)) {
            if(!placed) parties.addQuestProgress(party,"mining",p.getUniqueId(),1);
            return;
        }
        if(isMatureCrop(b)) parties.addQuestProgress(party,"farmer",p.getUniqueId(),1);
    }

    private boolean isMatureCrop(Block block) {
        Material m=block.getType();
        if(m==Material.MELON||m==Material.PUMPKIN||m==Material.SUGAR_CANE)return true;
        if(m==Material.WHEAT||m==Material.CARROTS||m==Material.POTATOES||m==Material.BEETROOTS||m==Material.COCOA||m==Material.NETHER_WART) {
            if(block.getBlockData() instanceof Ageable age) return age.getAge()>=age.getMaximumAge();
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        if(!(e.getEntity() instanceof Monster)) return;
        Player killer=e.getEntity().getKiller(); if(killer==null)return;
        String party=parties.partyOf(killer.getUniqueId()); if(party!=null) parties.addQuestProgress(party,"hunter",killer.getUniqueId(),1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player victim))return;
        Player attacker=resolvePlayer(e.getDamager()); if(attacker==null)return;
        if(!war.active())return;
        if(attacker.isOp()||victim.isOp()) {
            String ap=parties.partyOf(attacker.getUniqueId()), vp=parties.partyOf(victim.getUniqueId());
            if((ap!=null||vp!=null) && war.scoreWorld(attacker) && war.scoreWorld(victim)) e.setCancelled(true);
            return;
        }
        if(war.isEnemy(attacker,victim)) {
            // Party War is the final PvP decision between enemy parties. This also bridges common claim plugins.
            if(plugin.getConfig().getBoolean("war.claim-pvp-override",true)) e.setCancelled(false);
            war.registerCombat(attacker,victim);
        }
    }

    private Player resolvePlayer(Entity damager) {
        if(damager instanceof Player p)return p;
        if(damager instanceof Projectile projectile) {
            ProjectileSource source=projectile.getShooter(); if(source instanceof Player p)return p;
        }
        return null;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim=e.getEntity(); Player killer=victim.getKiller(); if(killer!=null) war.onKill(killer,victim);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { war.onQuit(e.getPlayer()); }

    @EventHandler
    public void onInventory(InventoryClickEvent e) {
        if(!Util.strip(e.getView().getTitle()).equalsIgnoreCase("MENKIESTES Party"))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(e.getRawSlot()==16) {
            String party=parties.partyOf(p.getUniqueId());
            if(party!=null) {
                p.closeInventory();
                p.sendMessage(Util.color("&eWeekly Quest &8| "+parties.questLine(party,"mining")));
                p.sendMessage(Util.color("&eWeekly Quest &8| "+parties.questLine(party,"hunter")));
                p.sendMessage(Util.color("&eWeekly Quest &8| "+parties.questLine(party,"farmer")));
            }
        } else if(e.getRawSlot()==22) { p.closeInventory(); war.showStatus(p); }
    }
}
