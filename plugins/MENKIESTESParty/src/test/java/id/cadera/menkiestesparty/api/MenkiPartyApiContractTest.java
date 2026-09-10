package id.cadera.menkiestesparty.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenkiPartyApiContractTest {

    @Test
    void apiVersionRemainsPinnedToV1() {
        assertEquals("1.0", MenkiPartyAPI.API_VERSION);
    }

    @Test
    void publicMethodContractHasNotDrifted() {
        Set<String> actual = new TreeSet<>();
        for (Method method : MenkiPartyAPI.class.getDeclaredMethods()) {
            if (!Modifier.isAbstract(method.getModifiers())) continue;
            actual.add(signature(method));
        }

        Set<String> expected = new TreeSet<>(Set.of(
                "addPartyExperience(java.lang.String,int,java.lang.String)->boolean",
                "apiVersion()->java.lang.String",
                "broadcast(java.lang.String,java.lang.String)->boolean",
                "contracts(java.lang.String)->java.util.List",
                "currentProject(java.lang.String)->java.util.Optional",
                "hasCapability(java.util.UUID,java.lang.String)->boolean",
                "integrationAvailable(java.lang.String)->boolean",
                "parties()->java.util.List",
                "party(java.lang.String)->java.util.Optional",
                "party(java.util.UUID)->java.util.Optional",
                "partyKey(java.util.UUID)->java.util.Optional",
                "pluginVersion()->java.lang.String",
                "relation(java.lang.String,java.lang.String)->id.cadera.menkiestesparty.api.MenkiPartyAPI$RelationSnapshot"
        ));

        assertEquals(expected, actual, "MenkiPartyAPI v1.0 method contract changed unexpectedly");
    }

    @Test
    void snapshotRecordShapesRemainStable() {
        assertRecord(MenkiPartyAPI.MemberSnapshot.class,
                "uuid:java.util.UUID", "name:java.lang.String", "role:java.lang.String",
                "division:java.lang.String", "online:boolean");

        assertRecord(MenkiPartyAPI.PartySnapshot.class,
                "key:java.lang.String", "displayName:java.lang.String", "owner:java.util.UUID",
                "level:int", "experience:int", "memberLimit:int", "members:java.util.List",
                "identity:java.lang.String", "recruitment:java.lang.String", "createdAt:long");

        assertRecord(MenkiPartyAPI.ProjectSnapshot.class,
                "id:java.lang.String", "displayName:java.lang.String", "type:java.lang.String",
                "progress:double", "goal:int", "startedAt:long");

        assertRecord(MenkiPartyAPI.ContractSnapshot.class,
                "id:java.lang.String", "sourceParty:java.lang.String", "targetParty:java.lang.String",
                "type:java.lang.String", "progress:int", "goal:int", "status:java.lang.String",
                "createdAt:long", "deadline:long");

        assertRecord(MenkiPartyAPI.RelationSnapshot.class,
                "firstParty:java.lang.String", "secondParty:java.lang.String",
                "relation:java.lang.String", "trust:int");
    }

    @Test
    void partySnapshotDefensivelyCopiesMembers() {
        UUID id = UUID.randomUUID();
        List<MenkiPartyAPI.MemberSnapshot> source = new ArrayList<>();
        source.add(new MenkiPartyAPI.MemberSnapshot(id, "Tester", "MEMBER", "none", true));

        MenkiPartyAPI.PartySnapshot snapshot = new MenkiPartyAPI.PartySnapshot(
                "test", "Test", id, 1, 0, 5, source,
                "Developing", "APPLICATION", 1L);

        source.clear();
        assertEquals(1, snapshot.members().size());
        assertEquals(1, snapshot.memberCount());
        assertEquals(1, snapshot.onlineMembers());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.members().clear());
    }

    @Test
    void projectPercentRemainsBounded() {
        MenkiPartyAPI.ProjectSnapshot zeroGoal =
                new MenkiPartyAPI.ProjectSnapshot("a", "A", "mining", 50.0, 0, 0L);
        MenkiPartyAPI.ProjectSnapshot overflow =
                new MenkiPartyAPI.ProjectSnapshot("b", "B", "mining", 150.0, 100, 0L);
        MenkiPartyAPI.ProjectSnapshot normal =
                new MenkiPartyAPI.ProjectSnapshot("c", "C", "mining", 25.0, 100, 0L);

        assertEquals(0.0, zeroGoal.percent());
        assertEquals(100.0, overflow.percent());
        assertEquals(25.0, normal.percent());
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return method.getName() + "(" + parameters + ")->" + method.getReturnType().getName();
    }

    private static void assertRecord(Class<?> type, String... expected) {
        assertTrue(type.isRecord(), type.getSimpleName() + " must remain a record");
        List<String> actual = Arrays.stream(type.getRecordComponents())
                .map(MenkiPartyApiContractTest::component)
                .toList();
        assertEquals(List.of(expected), actual, type.getSimpleName() + " record contract changed unexpectedly");
    }

    private static String component(RecordComponent component) {
        return component.getName() + ":" + component.getType().getName();
    }
}
