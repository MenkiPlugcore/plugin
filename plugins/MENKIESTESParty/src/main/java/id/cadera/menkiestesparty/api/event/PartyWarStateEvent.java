package id.cadera.menkiestesparty.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PartyWarStateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String oldPhase;
    private final String newPhase;
    private final String winnerParty;

    public PartyWarStateEvent(String oldPhase, String newPhase, @Nullable String winnerParty) {
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
        this.winnerParty = winnerParty;
    }

    public String oldPhase() { return oldPhase; }
    public String newPhase() { return newPhase; }
    public @Nullable String winnerParty() { return winnerParty; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
