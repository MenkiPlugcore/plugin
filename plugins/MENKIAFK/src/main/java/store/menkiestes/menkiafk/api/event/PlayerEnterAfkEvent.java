package store.menkiestes.menkiafk.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import store.menkiestes.menkiafk.api.AfkSessionSnapshot;

import java.util.Objects;

/**
 * Fired after MENKIAFK has placed a player into AFK state.
 * This event is informational and is not cancellable.
 */
public final class PlayerEnterAfkEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AfkSessionSnapshot session;

    public PlayerEnterAfkEvent(Player player, AfkSessionSnapshot session) {
        this.player = Objects.requireNonNull(player, "player");
        this.session = Objects.requireNonNull(session, "session");
    }

    public Player getPlayer() {
        return player;
    }

    public AfkSessionSnapshot getSession() {
        return session;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
