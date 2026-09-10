package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AdminConfirmationGateTest {

    @Test
    void correctTokenConsumesExactlyOnce() throws Exception {
        AdminConfirmationGate gate = new AdminConfirmationGate();
        long now = 1_000_000L;
        AdminConfirmationGate.Ticket ticket = gate.create(
                "actor", "DISBAND", "paradox", "test", new String[]{"disband", "paradox"}, now, 30_000L);

        int workers = 16;
        var pool = Executors.newFixedThreadPool(workers);
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        List<java.util.concurrent.Future<AdminConfirmationGate.Status>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return gate.consume("actor", ticket.token(), now + 1, true, 3).status();
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        long accepted = 0;
        for (var future : futures) if (future.get(5, TimeUnit.SECONDS) == AdminConfirmationGate.Status.ACCEPTED) accepted++;
        pool.shutdownNow();

        assertEquals(1L, accepted, "dangerous ticket must have exactly one successful consumer");
        assertEquals(0, gate.size());
    }

    @Test
    void missingAndWrongTokenDoNotExecuteTicket() {
        AdminConfirmationGate gate = new AdminConfirmationGate();
        long now = 2_000L;
        AdminConfirmationGate.Ticket ticket = gate.create(
                "actor", "TRANSFER_OWNER", "lunar", "test", new String[]{"transfer", "lunar", "Cadera"}, now, 30_000L);

        assertEquals(AdminConfirmationGate.Status.TOKEN_REQUIRED,
                gate.consume("actor", "", now + 1, true, 3).status());
        assertEquals(AdminConfirmationGate.Status.TOKEN_MISMATCH,
                gate.consume("actor", wrong(ticket.token()), now + 2, true, 3).status());
        assertNotNull(gate.peek("actor"));
        assertEquals(AdminConfirmationGate.Status.ACCEPTED,
                gate.consume("actor", ticket.token(), now + 3, true, 3).status());
    }

    @Test
    void maxWrongAttemptsCancelsTicket() {
        AdminConfirmationGate gate = new AdminConfirmationGate();
        long now = 5_000L;
        AdminConfirmationGate.Ticket ticket = gate.create(
                "actor", "RESET_PROJECT", "moon", "test", new String[]{"resetproject", "moon"}, now, 30_000L);
        String wrong = wrong(ticket.token());

        assertEquals(AdminConfirmationGate.Status.TOKEN_MISMATCH,
                gate.consume("actor", wrong, now + 1, true, 2).status());
        assertEquals(AdminConfirmationGate.Status.TOO_MANY_ATTEMPTS,
                gate.consume("actor", wrong, now + 2, true, 2).status());
        assertNull(gate.peek("actor"));
    }

    @Test
    void expiredTicketCannotExecute() {
        AdminConfirmationGate gate = new AdminConfirmationGate();
        AdminConfirmationGate.Ticket ticket = gate.create(
                "actor", "DISBAND", "party", "test", new String[]{"disband", "party"}, 100L, 10L);
        assertEquals(AdminConfirmationGate.Status.EXPIRED,
                gate.consume("actor", ticket.token(), 111L, true, 3).status());
        assertEquals(0, gate.size());
    }

    private static String wrong(String token) {
        char first = token.charAt(0) == '2' ? '3' : '2';
        return first + token.substring(1);
    }
}
