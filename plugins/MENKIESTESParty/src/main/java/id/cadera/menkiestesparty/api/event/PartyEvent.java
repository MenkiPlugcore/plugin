package id.cadera.menkiestesparty.api.event;

import org.bukkit.event.Event;

public abstract class PartyEvent extends Event {
    private final String partyKey;

    protected PartyEvent(String partyKey) {
        this.partyKey = partyKey;
    }

    public final String partyKey() {
        return partyKey;
    }
}
