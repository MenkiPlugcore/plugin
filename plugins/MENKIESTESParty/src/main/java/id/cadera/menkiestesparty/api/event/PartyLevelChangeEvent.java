package id.cadera.menkiestesparty.api.event;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PartyLevelChangeEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int oldLevel;
    private final int newLevel;

    public PartyLevelChangeEvent(String partyKey, int oldLevel, int newLevel) {
        super(partyKey);
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public int oldLevel() { return oldLevel; }
    public int newLevel() { return newLevel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
