package store.moonsign.menu.tp;

import java.util.UUID;

public record TeleportRequest(
        UUID requesterId,
        UUID targetId,
        TeleportMode mode,
        long createdAtMillis
) {}
