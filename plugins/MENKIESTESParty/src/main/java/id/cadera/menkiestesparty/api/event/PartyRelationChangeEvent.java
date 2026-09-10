package id.cadera.menkiestesparty.api.event;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PartyRelationChangeEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MenkiPartyAPI.RelationSnapshot relation;
    private final String oldRelation;
    private final String newRelation;
    private final int oldTrust;
    private final int newTrust;

    public PartyRelationChangeEvent(MenkiPartyAPI.RelationSnapshot relation,
                                    String oldRelation, String newRelation,
                                    int oldTrust, int newTrust) {
        super(relation.firstParty());
        this.relation = relation;
        this.oldRelation = oldRelation;
        this.newRelation = newRelation;
        this.oldTrust = oldTrust;
        this.newTrust = newTrust;
    }

    public MenkiPartyAPI.RelationSnapshot relation() { return relation; }
    public String oldRelation() { return oldRelation; }
    public String newRelation() { return newRelation; }
    public int oldTrust() { return oldTrust; }
    public int newTrust() { return newTrust; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
