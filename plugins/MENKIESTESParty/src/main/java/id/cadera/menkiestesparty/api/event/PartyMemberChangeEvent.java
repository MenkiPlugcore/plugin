package id.cadera.menkiestesparty.api.event;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PartyMemberChangeEvent extends PartyEvent {
    public enum Action { JOIN, LEAVE }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Action action;
    private final MenkiPartyAPI.MemberSnapshot member;

    public PartyMemberChangeEvent(String partyKey, Action action, MenkiPartyAPI.MemberSnapshot member) {
        super(partyKey);
        this.action = action;
        this.member = member;
    }

    public Action action() { return action; }
    public MenkiPartyAPI.MemberSnapshot member() { return member; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
