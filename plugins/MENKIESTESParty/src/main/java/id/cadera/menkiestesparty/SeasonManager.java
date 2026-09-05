package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class SeasonManager {
    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;
    private final StorageBundle db;

    public SeasonManager(MENKIESTESPartyPlugin plugin, PartyService parties, StorageBundle db) {
        this.plugin=plugin; this.parties=parties; this.db=db;
        if (!db.season.contains("active")) {
            db.season.set("active", plugin.getConfig().getBoolean("season.enabled-default", true));
            db.season.set("name", plugin.getConfig().getString("season.default-name", "Season_1"));
        }
    }

    public boolean active() { return db.season.getBoolean("active", true); }
    public String name() { return db.season.getString("name", "Season_1"); }
    public int points(String party) { return db.season.getInt("parties."+party+".points",0); }
    public int wins(String party) { return db.season.getInt("parties."+party+".wins",0); }

    public void recordWarWin(String party, int addPoints) {
        if (!active() || party == null) return;
        db.season.set("parties."+party+".points", points(party)+addPoints);
        db.season.set("parties."+party+".wins", wins(party)+1);
    }

    public void start(String newName) {
        db.season.set("active", true); db.season.set("name", newName); db.season.set("started-at", System.currentTimeMillis()); db.season.set("parties", null);
        Bukkit.broadcastMessage(parties.prefix()+Util.color(" &dSeason &f"+newName+" &ddimulai!")); plugin.saveDataSoon();
    }

    public void end() {
        if (!active()) return;
        String winner = topParty();
        int id = db.season.getInt("history-seq",0)+1; db.season.set("history-seq",id);
        String base="history."+id;
        db.season.set(base+".name",name()); db.season.set(base+".ended-at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); db.season.set(base+".champion",winner); db.season.set(base+".points", winner==null?0:points(winner));
        if (winner != null) {
            int rep = plugin.getConfig().getInt("season.champion-reputation",1000); parties.addRep(winner,rep);
            int chests = plugin.getConfig().getInt("season.champion-chests-per-member",2); parties.addWarChestTickets(winner,chests);
            Bukkit.broadcastMessage(parties.prefix()+Util.color(" &d&lSEASON SELESAI! &fChampion: &b"+parties.display(winner)+" &8| &d"+points(winner)+" pts &8| &a+"+rep+" Rep"));
        }
        db.season.set("active",false); plugin.saveDataSoon();
    }

    public String topParty() {
        ConfigurationSection sec=db.season.getConfigurationSection("parties"); if(sec==null)return null;
        return sec.getKeys(false).stream().max(Comparator.comparingInt(this::points)).orElse(null);
    }

    public void showTop(Player p) {
        ConfigurationSection sec=db.season.getConfigurationSection("parties");
        p.sendMessage(Util.color("&d&lSEASON TOP &7- &f"+name()));
        if(sec==null||sec.getKeys(false).isEmpty()){p.sendMessage(Util.color("&7Belum ada poin."));return;}
        List<String> keys=new ArrayList<>(sec.getKeys(false)); keys.sort((a,b)->Integer.compare(points(b),points(a)));
        int i=1;for(String key:keys.subList(0,Math.min(10,keys.size()))) p.sendMessage(Util.color("&e#"+i+++" &b"+parties.display(key)+" &7- &f"+points(key)+" pts &8| &7"+wins(key)+" wins"));
    }
}
