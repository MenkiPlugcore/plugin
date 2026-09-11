package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CdrJobsBridgePolicyTest {

    @Test
    void mapsFivePathsToPartyActivityTypes() {
        assertEquals("mining", CdrJobsBridgePolicy.questType("MINER"));
        assertEquals("farmer", CdrJobsBridgePolicy.questType("farmer"));
        assertEquals("hunter", CdrJobsBridgePolicy.questType("HUNTER"));
        assertEquals("lumberjack", CdrJobsBridgePolicy.questType("LUMBERJACK"));
        assertEquals("fisher", CdrJobsBridgePolicy.questType("FISHER"));
        assertEquals("cdr_miner", CdrJobsBridgePolicy.projectType("MINER"));
        assertEquals("cdr_fisher", CdrJobsBridgePolicy.projectType("fisher"));
        assertEquals("", CdrJobsBridgePolicy.questType("UNKNOWN"));
    }

    @Test
    void distinctConvergenceRequiresFiveAssignableMembers() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();

        Map<UUID, List<String>> roster = new LinkedHashMap<>();
        roster.put(a, List.of("MINER", "FARMER"));
        roster.put(b, List.of("FARMER"));
        roster.put(c, List.of("HUNTER"));
        roster.put(d, List.of("LUMBERJACK"));
        roster.put(e, List.of("FISHER"));
        assertTrue(CdrJobsBridgePolicy.hasDistinctFivePathCoverage(roster));

        roster.remove(e);
        assertFalse(CdrJobsBridgePolicy.hasDistinctFivePathCoverage(roster));
    }

    @Test
    void oneOmniProfessionPlayerCannotFillAllDistinctSlots() {
        UUID omni = UUID.randomUUID();
        Map<UUID, List<String>> roster = Map.of(omni, CdrJobsBridgePolicy.PATHS);
        assertFalse(CdrJobsBridgePolicy.hasDistinctFivePathCoverage(roster));
    }

    @Test
    void eventAmountIsClampedSafely() {
        assertEquals(0, CdrJobsBridgePolicy.safeEventAmount(0));
        assertEquals(1, CdrJobsBridgePolicy.safeEventAmount(1));
        assertEquals(Integer.MAX_VALUE, CdrJobsBridgePolicy.safeEventAmount(Long.MAX_VALUE));
    }
}
