package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminAuditQueryTest {

    @Test
    void searchMatchesAllUsefulAuditFieldsCaseInsensitively() {
        AdminAuditQuery.Entry entry = new AdminAuditQuery.Entry(
                "42", 123L, "CADERA(0000)", "FREEZE", "paradox", "SUCCESS", "reason=maintenance");

        assertTrue(AdminAuditQuery.matchesSearch(entry, "cadera"));
        assertTrue(AdminAuditQuery.matchesSearch(entry, "FREEZE"));
        assertTrue(AdminAuditQuery.matchesSearch(entry, "PARADOX"));
        assertTrue(AdminAuditQuery.matchesSearch(entry, "success"));
        assertTrue(AdminAuditQuery.matchesSearch(entry, "maintenance"));
        assertFalse(AdminAuditQuery.matchesSearch(entry, "missing-value"));
    }

    @Test
    void fieldFiltersStayScopedToRequestedColumn() {
        AdminAuditQuery.Entry entry = new AdminAuditQuery.Entry(
                "42", 123L, "CADERA(0000)", "DISBAND", "lunar", "FAILED", "archive failed");

        assertTrue(AdminAuditQuery.matchesFilter(entry, "party", "LUN"));
        assertTrue(AdminAuditQuery.matchesFilter(entry, "staff", "cadera"));
        assertTrue(AdminAuditQuery.matchesFilter(entry, "actor", "0000"));
        assertTrue(AdminAuditQuery.matchesFilter(entry, "action", "band"));
        assertTrue(AdminAuditQuery.matchesFilter(entry, "result", "fail"));
        assertFalse(AdminAuditQuery.matchesFilter(entry, "party", "cadera"));
        assertFalse(AdminAuditQuery.matchesFilter(entry, "unknown", "lunar"));
        assertTrue(AdminAuditQuery.supportedField("RESULT"));
        assertFalse(AdminAuditQuery.supportedField("detail"));
    }

    @Test
    void nullValuesAreSafe() {
        AdminAuditQuery.Entry entry = new AdminAuditQuery.Entry(null, 0L, null, null, null, null, null);
        assertTrue(AdminAuditQuery.matchesSearch(entry, ""));
        assertFalse(AdminAuditQuery.matchesSearch(entry, "anything"));
    }
}
