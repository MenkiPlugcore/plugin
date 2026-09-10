package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminRepairPlannerTest {

    @Test
    void deterministicPlanMirrorsConservativeRepairRules() {
        AdminRepairPlanner.Plan plan = AdminRepairPlanner.plan(
                "paradox",
                "owner-uuid",
                false,
                "MEMBER",
                "wrong-party",
                List.of(
                        new AdminRepairPlanner.MemberState("member-a", false, "OWNER", "wrong-party"),
                        new AdminRepairPlanner.MemberState("member-b", false, "MEMBER", "paradox")
                ),
                List.of(
                        new AdminRepairPlanner.IndexState("orphan-uuid", true, false),
                        new AdminRepairPlanner.IndexState("not-a-uuid", false, false)
                ));

        assertTrue(plan.safe());
        assertEquals(7, plan.changeCount());
        assertTrue(plan.actions().stream().anyMatch(v -> v.startsWith("ADD owner")));
        assertTrue(plan.actions().stream().anyMatch(v -> v.startsWith("SET owner role")));
        assertTrue(plan.actions().stream().anyMatch(v -> v.startsWith("FIX owner player index")));
        assertTrue(plan.actions().stream().anyMatch(v -> v.contains("member-a")));
        assertTrue(plan.actions().stream().anyMatch(v -> v.startsWith("DEMOTE extra OWNER")));
        assertTrue(plan.actions().stream().anyMatch(v -> v.startsWith("REMOVE orphan")));
        assertTrue(plan.actions().stream().anyMatch(v -> v.startsWith("REMOVE invalid")));
    }

    @Test
    void missingOwnerIsUnsafeAndNeverGuessed() {
        AdminRepairPlanner.Plan plan = AdminRepairPlanner.plan(
                "lunar", null, false, "", "", List.of(), List.of());

        assertFalse(plan.safe());
        assertEquals(0, plan.changeCount());
        assertFalse(plan.warnings().isEmpty());
        assertTrue(plan.warnings().get(0).toLowerCase().contains("owner"));
    }

    @Test
    void healthyPartyNeedsNoChanges() {
        AdminRepairPlanner.Plan plan = AdminRepairPlanner.plan(
                "moon", "owner", true, "OWNER", "moon",
                List.of(new AdminRepairPlanner.MemberState("member", false, "MEMBER", "moon")),
                List.of(new AdminRepairPlanner.IndexState("owner", true, true),
                        new AdminRepairPlanner.IndexState("member", true, true)));
        assertTrue(plan.safe());
        assertEquals(0, plan.changeCount());
    }
}
