package id.cadera.menkiestesparty;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v1.6.1 confirmation registry for dangerous administration actions.
 *
 * The registry is intentionally Bukkit-free so its exactly-once behavior can
 * be regression-tested without a server runtime.
 */
final class AdminConfirmationGate {
    enum Status {
        ACCEPTED,
        MISSING,
        TOKEN_REQUIRED,
        TOKEN_MISMATCH,
        TOO_MANY_ATTEMPTS,
        EXPIRED,
        ALREADY_CONSUMED
    }

    static final class Ticket {
        private final String actorKey;
        private final String token;
        private final String actionKey;
        private final String targetKey;
        private final String description;
        private final String[] commandArgs;
        private final long createdAt;
        private final long expiresAt;
        private final AtomicBoolean consumed = new AtomicBoolean(false);
        private final AtomicInteger failedAttempts = new AtomicInteger(0);

        Ticket(String actorKey, String token, String actionKey, String targetKey,
               String description, String[] commandArgs, long createdAt, long expiresAt) {
            this.actorKey = Objects.requireNonNull(actorKey, "actorKey");
            this.token = Objects.requireNonNull(token, "token").toUpperCase(Locale.ROOT);
            this.actionKey = Objects.requireNonNull(actionKey, "actionKey");
            this.targetKey = targetKey == null ? "" : targetKey;
            this.description = description == null ? actionKey : description;
            this.commandArgs = commandArgs == null ? new String[0] : commandArgs.clone();
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        String actorKey() { return actorKey; }
        String token() { return token; }
        String actionKey() { return actionKey; }
        String targetKey() { return targetKey; }
        String description() { return description; }
        String[] commandArgs() { return commandArgs.clone(); }
        long createdAt() { return createdAt; }
        long expiresAt() { return expiresAt; }
        int failedAttempts() { return failedAttempts.get(); }
    }

    record ConsumeResult(Status status, Ticket ticket, int failedAttempts) {}

    private static final char[] TOKEN_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    Ticket create(String actorKey, String actionKey, String targetKey, String description,
                  String[] commandArgs, long now, long ttlMillis) {
        long expiresAt = now + Math.max(1L, ttlMillis);
        Ticket ticket = new Ticket(actorKey, nextToken(), actionKey, targetKey,
                description, commandArgs, now, expiresAt);
        tickets.put(actorKey, ticket);
        return ticket;
    }

    ConsumeResult consume(String actorKey, String providedToken, long now,
                          boolean requireToken, int maxAttempts) {
        Ticket ticket = tickets.get(actorKey);
        if (ticket == null) return new ConsumeResult(Status.MISSING, null, 0);

        if (now > ticket.expiresAt()) {
            tickets.remove(actorKey, ticket);
            return new ConsumeResult(Status.EXPIRED, ticket, ticket.failedAttempts());
        }

        String normalized = normalize(providedToken);
        if (requireToken && normalized.isBlank()) {
            return new ConsumeResult(Status.TOKEN_REQUIRED, ticket, ticket.failedAttempts());
        }

        if (!normalized.isBlank() && !constantTimeEquals(ticket.token(), normalized)) {
            int attempts = ticket.failedAttempts.incrementAndGet();
            int cap = Math.max(1, maxAttempts);
            if (attempts >= cap) {
                tickets.remove(actorKey, ticket);
                return new ConsumeResult(Status.TOO_MANY_ATTEMPTS, ticket, attempts);
            }
            return new ConsumeResult(Status.TOKEN_MISMATCH, ticket, attempts);
        }

        if (!ticket.consumed.compareAndSet(false, true)) {
            tickets.remove(actorKey, ticket);
            return new ConsumeResult(Status.ALREADY_CONSUMED, ticket, ticket.failedAttempts());
        }

        tickets.remove(actorKey, ticket);
        return new ConsumeResult(Status.ACCEPTED, ticket, ticket.failedAttempts());
    }

    Ticket cancel(String actorKey) {
        return tickets.remove(actorKey);
    }

    Ticket peek(String actorKey) {
        return tickets.get(actorKey);
    }

    int size() {
        return tickets.size();
    }

    void clear() {
        tickets.clear();
    }

    private String nextToken() {
        char[] out = new char[6];
        for (int i = 0; i < out.length; i++) out[i] = TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)];
        return new String(out);
    }

    static String normalize(String token) {
        return token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] a = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }
}
