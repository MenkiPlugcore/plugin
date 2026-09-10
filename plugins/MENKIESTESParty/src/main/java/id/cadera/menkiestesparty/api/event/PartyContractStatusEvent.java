package id.cadera.menkiestesparty.api.event;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PartyContractStatusEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MenkiPartyAPI.ContractSnapshot contract;
    private final String oldStatus;
    private final String newStatus;

    public PartyContractStatusEvent(MenkiPartyAPI.ContractSnapshot contract, String oldStatus, String newStatus) {
        super(contract.targetParty());
        this.contract = contract;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public MenkiPartyAPI.ContractSnapshot contract() { return contract; }
    public String oldStatus() { return oldStatus; }
    public String newStatus() { return newStatus; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
