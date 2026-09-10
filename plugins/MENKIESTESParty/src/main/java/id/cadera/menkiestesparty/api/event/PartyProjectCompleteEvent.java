package id.cadera.menkiestesparty.api.event;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PartyProjectCompleteEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String projectId;
    private final String projectName;

    public PartyProjectCompleteEvent(String partyKey, String projectId, String projectName) {
        super(partyKey);
        this.projectId = projectId;
        this.projectName = projectName;
    }

    public String projectId() { return projectId; }
    public String projectName() { return projectName; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
