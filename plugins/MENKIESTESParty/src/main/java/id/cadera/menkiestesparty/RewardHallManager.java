package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.UUID;

public final class RewardHallManager {
    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final StorageBundle db;
    public RewardHallManager(MENKIESTESPartyPlugin plugin, PartyService parties, StorageBundle db){this.plugin=plugin;this.parties=parties;this.db=db;}

    public void awardWar(String party, String runId) {
        if (!plugin.getConfig().getBoolean("reward-hall.enabled",true)) return;
        LocalDate now=LocalDate.now(); String month=now.toString().substring(0,7);
        int week=Math.min(4, Math.max(1,(now.getDayOfMonth()-1)/7+1)); String id=month+"-W"+week;
        if(db.hall.getBoolean("entries."+id+".awarded",false)) return;
        db.hall.set("entries."+id+".awarded",true); db.hall.set("entries."+id+".party",party); db.hall.set("entries."+id+".run-id",runId); db.hall.set("entries."+id+".recipient",null); db.hall.set("entries."+id+".claimed",false);
        parties.broadcastParty(party, parties.prefix()+" &6Reward Hall Week "+week+" tersedia. Owner pilih penerima dengan &f/party hall choose <player>&6.");
    }

    private String currentId(){LocalDate now=LocalDate.now();int week=Math.min(4,Math.max(1,(now.getDayOfMonth()-1)/7+1));return now.toString().substring(0,7)+"-W"+week;}

    public void choose(Player owner, Player target){String party=parties.partyOf(owner.getUniqueId());if(party==null||!owner.getUniqueId().equals(parties.owner(party))){owner.sendMessage(parties.prefix()+Util.color(" &cHanya Owner Party pemenang yang dapat memilih recipient."));return;}String id=currentId();if(!party.equals(db.hall.getString("entries."+id+".party"))){owner.sendMessage(parties.prefix()+Util.color(" &cParty-mu tidak memiliki artifact aktif bulan/week ini."));return;}if(!party.equals(parties.partyOf(target.getUniqueId()))){owner.sendMessage(parties.prefix()+Util.color(" &cRecipient harus member Party."));return;}if(db.hall.getString("entries."+id+".recipient")!=null){owner.sendMessage(parties.prefix()+Util.color(" &cRecipient sudah dipilih."));return;}db.hall.set("entries."+id+".recipient",target.getUniqueId().toString());plugin.saveDataSoon();parties.broadcastParty(party,parties.prefix()+" &6Main Artifact dipilih untuk &f"+target.getName()+"&6. Claim: &f/party hall claim");}

    public void claim(Player p){String id=currentId();if(!p.getUniqueId().toString().equals(db.hall.getString("entries."+id+".recipient"))){p.sendMessage(parties.prefix()+Util.color(" &cKamu bukan recipient Main Artifact aktif."));return;}if(db.hall.getBoolean("entries."+id+".claimed",false)){p.sendMessage(parties.prefix()+Util.color(" &cArtifact sudah diclaim."));return;}ItemStack artifact=Util.item(Material.NETHER_STAR,"&6&lPARTY WAR ARTIFACT","&7Reward Hall: &f"+id,"&7Penerima: &f"+p.getName(),"&7Item unik satu copy.");var overflow=p.getInventory().addItem(artifact);for(ItemStack it:overflow.values())p.getWorld().dropItemNaturally(p.getLocation(),it);db.hall.set("entries."+id+".claimed",true);db.hall.set("entries."+id+".claimed-at",System.currentTimeMillis());plugin.saveDataSoon();Bukkit.broadcastMessage(parties.prefix()+Util.color(" &6"+p.getName()+" telah claim Main Artifact Reward Hall "+id+"."));}

    public void show(Player p){String id=currentId();p.sendMessage(Util.color("&8&m-----------------------------"));p.sendMessage(Util.color("&6&lPARTY REWARD HALL &7- &f"+id));String party=db.hall.getString("entries."+id+".party");if(party==null){p.sendMessage(Util.color("&7Belum ada artifact untuk Week ini."));return;}p.sendMessage(Util.color("&7Pemenang: &b"+parties.display(party)));String rec=db.hall.getString("entries."+id+".recipient");if(rec==null)p.sendMessage(Util.color("&7Recipient: &eBelum dipilih"));else{try{UUID u=UUID.fromString(rec);p.sendMessage(Util.color("&7Recipient: &f"+Bukkit.getOfflinePlayer(u).getName()+" &8| &7Claimed: &f"+db.hall.getBoolean("entries."+id+".claimed",false)));}catch(Exception ignored){}}}
}
