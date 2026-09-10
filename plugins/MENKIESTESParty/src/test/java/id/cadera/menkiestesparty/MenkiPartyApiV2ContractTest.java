package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import id.cadera.menkiestesparty.api.v2.MenkiPartyAPIv2;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MenkiPartyApiV2ContractTest {

    @Test
    void v1AndV2MajorContractsCoexist() {
        assertEquals("1.0", MenkiPartyAPI.API_VERSION);
        assertEquals("2.0", MenkiPartyAPIv2.API_VERSION);

        Set<String> methods = Arrays.stream(MenkiPartyAPIv2.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertTrue(methods.containsAll(Set.of(
                "apiVersion", "legacyApiVersion", "pluginVersion", "runtime",
                "partyKey", "party", "parties", "member", "currentProject",
                "contracts", "relation", "addPartyExperience", "broadcast",
                "hasCapability", "integrationAvailable"
        )));
    }

    @Test
    void partySnapshotCarriesRevisionAndSocialProfile() {
        List<String> components = Arrays.stream(MenkiPartyAPIv2.PartySnapshot.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertEquals(List.of(
                "key", "displayName", "owner", "level", "experience", "memberLimit",
                "members", "identity", "recruitment", "createdAt", "revision", "social"
        ), components);
    }

    @Test
    void defensiveCopiesAreKeptInV2Snapshots() {
        var social = new MenkiPartyAPIv2.SocialProfileSnapshot(
                "", "TAG", "AQUA", "PLAYER_HEAD", "PUBLIC", "", "",
                new java.util.ArrayList<>(List.of("first")), 0L);
        assertThrows(UnsupportedOperationException.class,
                () -> social.unlockedAchievements().add("second"));

        var party = new MenkiPartyAPIv2.PartySnapshot(
                "key", "Party", null, 1, 0, 5,
                new java.util.ArrayList<>(), "Developing", "CLOSED", 0L, 0L, social);
        assertThrows(UnsupportedOperationException.class,
                () -> party.members().add(null));
    }
}
