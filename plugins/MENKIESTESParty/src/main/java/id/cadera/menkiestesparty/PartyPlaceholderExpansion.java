package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
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
        if(identifier.equalsIgnoreCase("api_version"))return MenkiPartyAPI.API_VERSION;
        if(identifier.equalsIgnoreCase("plugin_version"))return plugin.getDescription().getVersion();
        if(identifier.equalsIgnoreCase("vault_available"))return plugin.developerApi().integrationAvailable("vault")?"true":"false";
        if(player==null)return "";

        String party=plugin.parties().partyOf(player.getUniqueId());
        if(identifier.equalsIgnoreCase("name"))return party==null?"None":plugin.parties().display(party);
        if(identifier.equalsIgnoreCase("role"))return party==null?"None":String.valueOf(plugin.parties().role(player.getUniqueId()));
        if(identifier.equalsIgnoreCase("level"))return party==null?"0":String.valueOf(plugin.parties().level(party));
        if(identifier.equalsIgnoreCase("reputation")||identifier.equalsIgnoreCase("rep"))return party==null?"0":String.valueOf(plugin.parties().rep(party));
        if(identifier.equalsIgnoreCase("members"))return party==null?"0":String.valueOf(plugin.parties().memberCount(party));
        if(identifier.equalsIgnoreCase("online_members")){
            if(party==null)return "0";
            return plugin.developerApi().party(party).map(p->String.valueOf(p.onlineMembers())).orElse("0");
        }
        if(identifier.equalsIgnoreCase("memberlimit"))return party==null?"0":String.valueOf(plugin.parties().memberLimit(party));
        if(identifier.equalsIgnoreCase("nextrep"))return party==null?"0":String.valueOf(plugin.parties().nextLevelRep(party));
        if(identifier.equalsIgnoreCase("owner")){
            if(party==null)return "None";
            java.util.UUID owner=plugin.parties().owner(party);
            if(owner==null)return "None";
            String name=Bukkit.getOfflinePlayer(owner).getName();
            return name==null?owner.toString():name;
        }

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

        if(identifier.equalsIgnoreCase("identity"))return party==null?"None":plugin.progression().identityDisplay(plugin.progression().identity(party));
        if(identifier.equalsIgnoreCase("identity_raw"))return party==null?"NONE":plugin.progression().identity(party);
        if(identifier.equalsIgnoreCase("division"))return party==null?"None":plugin.progression().divisionDisplay(plugin.progression().divisionOf(player.getUniqueId()));
        if(identifier.equalsIgnoreCase("skill_points"))return party==null?"0":String.valueOf(plugin.progression().availableSkillPoints(party));
        if(identifier.equalsIgnoreCase("project")){
            if(party==null)return "None";
            String id=plugin.progression().currentProject(party);
            return id==null?"None":plugin.progression().projectName(id);
        }
        if(identifier.equalsIgnoreCase("project_progress")){
            if(party==null)return "0/0";
            String id=plugin.progression().currentProject(party);
            return id==null?"0/0":((int)Math.floor(plugin.progression().projectProgress(party)))+"/"+plugin.progression().projectGoal(id);
        }
        if(identifier.equalsIgnoreCase("project_percent")){
            if(party==null)return "0";
            return plugin.developerApi().currentProject(party)
                    .map(p->String.format(java.util.Locale.US,"%.1f",p.percent()))
                    .orElse("0");
        }

        if(identifier.equalsIgnoreCase("recruitment"))return party==null?"None":plugin.interactions().recruitmentMode(party);
        if(identifier.equalsIgnoreCase("contracts_active"))return party==null?"0":String.valueOf(plugin.interactions().activeContractCount(party));
        if(identifier.equalsIgnoreCase("contracts_completed"))return party==null?"0":String.valueOf(plugin.interactions().completedContractCount(party));
        if(identifier.equalsIgnoreCase("applications_pending"))return party==null?"0":String.valueOf(plugin.interactions().pendingApplicationCount(party));
        if(identifier.equalsIgnoreCase("inbox_unread"))return String.valueOf(plugin.stability().unreadCount(player.getUniqueId()));

        if(identifier.regionMatches(true,0,"relation_",0,9)){
            if(party==null)return "NEUTRAL";
            String target=identifier.substring(9);
            return plugin.developerApi().relation(party,target).relation();
        }
        if(identifier.regionMatches(true,0,"trust_",0,6)){
            if(party==null)return "0";
            String target=identifier.substring(6);
            return String.valueOf(plugin.developerApi().relation(party,target).trust());
        }
        return null;
    }
}
