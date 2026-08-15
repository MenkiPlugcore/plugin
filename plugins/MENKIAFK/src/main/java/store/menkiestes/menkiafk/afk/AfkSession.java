package store.menkiestes.menkiafk.afk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class AfkSession {
    private final String reason;
    private final long startedAt;
    private final AfkType type;
    private final Deque<RememberedMessage> rememberedMessages = new ArrayDeque<>();

    public AfkSession(String reason, long startedAt, AfkType type) {
        this.reason = reason;
        this.startedAt = startedAt;
        this.type = type;
    }

    public String reason() {
        return reason;
    }

    public long startedAt() {
        return startedAt;
    }

    public AfkType type() {
        return type;
    }

    public synchronized void remember(RememberedMessage message, int max) {
        if (max <= 0) return;
        while (rememberedMessages.size() >= max) {
            rememberedMessages.pollFirst();
        }
        rememberedMessages.addLast(message);
    }

    public synchronized List<RememberedMessage> drainMessages() {
        List<RememberedMessage> copy = new ArrayList<>(rememberedMessages);
        rememberedMessages.clear();
        return copy;
    }
}
