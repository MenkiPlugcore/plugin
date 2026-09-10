package store.menkiestes.menkiafk.api;

import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.Test;
import store.menkiestes.menkiafk.api.event.PlayerEnterAfkEvent;
import store.menkiestes.menkiafk.api.event.PlayerLeaveAfkEvent;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApiContractTest {

    @Test
    void sessionSnapshotNormalizesReasonAndClampsDuration() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AfkSessionSnapshot snapshot = new AfkSessionSnapshot(playerId, null, 1_000L, AfkSessionType.MANUAL);

        assertEquals(playerId, snapshot.playerId());
        assertEquals("", snapshot.reason());
        assertEquals(AfkSessionType.MANUAL, snapshot.type());
        assertEquals(0L, snapshot.durationMillis(500L));
        assertEquals(1_500L, snapshot.durationMillis(2_500L));
    }

    @Test
    void statisticsSnapshotKeepsLegacySessionAccountingStable() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        AfkStatisticsSnapshot snapshot = new AfkStatisticsSnapshot(
                playerId, "Candidate", 100L, 200L, 300L, 150L,
                10, 3, 4, 123_456L);

        assertEquals(3, snapshot.legacySessions());

        AfkStatisticsSnapshot overClassified = new AfkStatisticsSnapshot(
                playerId, "Candidate", 0L, 0L, 0L, 0L,
                2, 2, 2, 0L);
        assertEquals(0, overClassified.legacySessions());
    }

    @Test
    void publicApiV1MethodSignaturesRemainAvailable() throws Exception {
        assertTrue(MenkiAfkAPI.class.isInterface());
        assertEquals(boolean.class, MenkiAfkAPI.class.getMethod("isAfk", UUID.class).getReturnType());
        assertEquals(Optional.class, MenkiAfkAPI.class.getMethod("getCurrentSession", UUID.class).getReturnType());
        assertEquals(AfkStatisticsSnapshot.class, MenkiAfkAPI.class.getMethod("getStatistics", UUID.class).getReturnType());
        assertEquals(long.class, MenkiAfkAPI.class.getMethod("getTotalAfkTime", UUID.class).getReturnType());
        assertEquals(int.class, MenkiAfkAPI.class.getMethod("getAfkCount").getReturnType());
        assertEquals(MenkiAfkAPI.class, MenkiAfkAPI.class.getMethod("get").getReturnType());
        assertArrayEquals(new AfkSessionType[]{AfkSessionType.MANUAL, AfkSessionType.AUTO}, AfkSessionType.values());
    }

    @Test
    void lifecycleEventsStayNotificationOnly() {
        assertFalse(Cancellable.class.isAssignableFrom(PlayerEnterAfkEvent.class));
        assertFalse(Cancellable.class.isAssignableFrom(PlayerLeaveAfkEvent.class));
    }
}
