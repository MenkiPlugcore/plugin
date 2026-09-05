package id.cadera.menkiestesparty;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PartyPlaceholderExpansion extends PlaceholderExpansion {
    private final MENKIESTESPartyPlugin plugin;
    public PartyPlaceholderExpansion(MENKIESTESPartyPlugin plugin){this.plugin=plugin;}
    @Override public @NotNull String getIdentifier(){return "mparty";}
    @Override public @NotNull String getAuthor(){return "CADERA";}
    @Override public @NotNull String getVersion(){return plugin.getDescription().getVersion();}
    @Override public boolean persist(){return true;}

    @Override public @Nullable String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if(player==null)return "";
        String party=plugin.parties().partyOf(player.getUniqueId());
        if(identifier.equalsIgnoreCase("name"))return party==null?"None":plugin.parties().display(party);
        if(identifier.equalsIgnoreCase("role"))return party==null?"None":String.valueOf(plugin.parties().role(player.getUniqueId()));
        if(identifier.equalsIgnoreCase("level"))return party==null?"0":String.valueOf(plugin.parties().level(party));
        if(identifier.equalsIgnoreCase("reputation")||identifier.equalsIgnoreCase("rep"))return party==null?"0":String.valueOf(plugin.parties().rep(party));
        if(identifier.equalsIgnoreCase("members"))return party==null?"0":String.valueOf(plugin.parties().memberCount(party));
        if(identifier.equalsIgnoreCase("memberlimit"))return party==null?"0":String.valueOf(plugin.parties().memberLimit(party));
        if(identifier.equalsIgnoreCase("nextrep"))return party==null?"0":String.valueOf(plugin.parties().nextLevelRep(party));
        if(identifier.equalsIgnoreCase("quest_mining"))return party==null?"0/0":plugin.parties().questProgress(party,"mining")+"/"+plugin.parties().questGoal("mining");
        if(identifier.equalsIgnoreCase("quest_hunter"))return party==null?"0/0":plugin.parties().questProgress(party,"hunter")+"/"+plugin.parties().questGoal("hunter");
        if(identifier.equalsIgnoreCase("quest_farmer"))return party==null?"0/0":plugin.parties().questProgress(party,"farmer")+"/"+plugin.parties().questGoal("farmer");
        if(identifier.equalsIgnoreCase("reliclevel")||identifier.equalsIgnoreCase("relic_level"))return party==null?"0":String.valueOf(plugin.parties().relicLevel(party));
        if(identifier.equalsIgnoreCase("relicmissions")||identifier.equalsIgnoreCase("relic_missions"))return party==null?"0":String.valueOf(plugin.parties().relicMissions(party));
        if(identifier.equalsIgnoreCase("war_score"))return party==null?"0":String.valueOf(plugin.war().score(party));
        if(identifier.equalsIgnoreCase("war_phase"))return plugin.war().phase().name();
        if(identifier.equalsIgnoreCase("season_points"))return party==null?"0":String.valueOf(plugin.season().points(party));
        if(identifier.equalsIgnoreCase("season_wins"))return party==null?"0":String.valueOf(plugin.season().wins(party));
        if(identifier.equalsIgnoreCase("season_name"))return plugin.season().name();
        return null;
    }
}
