package store.menkiestes.menkiafk.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import store.menkiestes.menkiafk.api.AfkSessionSnapshot;

import java.util.Objects;

/**
 * Fired after MENKIAFK has removed a player from AFK state during normal runtime.
 * This event is informational and is not cancellable.
 */
public final class PlayerLeaveAfkEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AfkSessionSnapshot session;
    private final long endedAt;

    public PlayerLeaveAfkEvent(Player player, AfkSessionSnapshot session, long endedAt) {
        this.player = Objects.requireNonNull(player, "player");
        this.session = Objects.requireNonNull(session, "session");
        this.endedAt = Math.max(session.startedAt(), endedAt);
    }

    public Player getPlayer() {
        return player;
    }

    public AfkSessionSnapshot getSession() {
        return session;
    }

    public long getEndedAt() {
        return endedAt;
    }

    public long getDurationMillis() {
        return Math.max(0L, endedAt - session.startedAt());
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
