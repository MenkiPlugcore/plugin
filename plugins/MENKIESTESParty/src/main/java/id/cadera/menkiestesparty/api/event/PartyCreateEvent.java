package id.cadera.menkiestesparty.api.event;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PartyCreateEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MenkiPartyAPI.PartySnapshot party;

    public PartyCreateEvent(MenkiPartyAPI.PartySnapshot party) {
        super(party.key());
        this.party = party;
    }

    public MenkiPartyAPI.PartySnapshot party() { return party; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
