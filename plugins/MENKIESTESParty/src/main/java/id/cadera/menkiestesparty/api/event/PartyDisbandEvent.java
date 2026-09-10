package id.cadera.menkiestesparty.api.event;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PartyDisbandEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MenkiPartyAPI.PartySnapshot previousParty;

    public PartyDisbandEvent(MenkiPartyAPI.PartySnapshot previousParty) {
        super(previousParty.key());
        this.previousParty = previousParty;
    }

    public MenkiPartyAPI.PartySnapshot previousParty() { return previousParty; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
