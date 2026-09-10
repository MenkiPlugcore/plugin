package id.cadera.menkiestesparty.architecture;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StorageDocumentSchemaTest {

    private static final List<String> KEYS = List.of("parties", "wars", "season", "hall", "interactions");

    @Test
    void legacyDocumentsMigrateWithoutLosingBody() {
        Map<String, String> old = new LinkedHashMap<>();
        old.put("parties", "parties:\n  alpha:\n    display: Alpha\n");
        old.put("wars", "phase: NONE\n");
        old.put("season", "active: false\n");
        old.put("hall", "daily: {}\n");
        old.put("interactions", "contracts: {}\n");

        assertTrue(StorageDocumentSchema.requiresMigration(old, KEYS));
        Map<String, String> migrated = StorageDocumentSchema.migrate(old, KEYS, 123456L, "2.0.0");

        assertTrue(StorageDocumentSchema.verify(migrated, KEYS));
        assertEquals(2, StorageDocumentSchema.minimumVersion(migrated, KEYS));
        assertTrue(migrated.get("parties").contains("display: Alpha"));
        assertTrue(migrated.get("wars").contains("phase: NONE"));
        assertTrue(migrated.get("parties").contains("_menkiestesparty_document_schema: 2"));
        assertTrue(migrated.get("parties").contains("_menkiestesparty_document_key: parties"));
    }

    @Test
    void migrationIsIdempotent() {
        String once = StorageDocumentSchema.migrateDocument("parties", "parties: {}\n", 100L, "2.0.0");
        String twice = StorageDocumentSchema.migrateDocument("parties", once, 999L, "2.0.0");
        assertEquals(once, twice);
    }

    @Test
    void wrongDocumentMarkerIsRejectedAndRepaired() {
        String wrong = "_menkiestesparty_document_schema: 2\n"
                + "_menkiestesparty_document_key: wars\n"
                + "parties: {}\n";
        assertNotEquals("parties", StorageDocumentSchema.detectDocumentKey(wrong));
        String repaired = StorageDocumentSchema.migrateDocument("parties", wrong, 10L, "2.0.0");
        assertEquals("parties", StorageDocumentSchema.detectDocumentKey(repaired));
        assertEquals(2, StorageDocumentSchema.detectVersion(repaired));
        assertTrue(repaired.contains("parties: {}"));
    }

    @Test
    void metadataOnlyDocumentsAreNotMeaningfulLegacyData() {
        Map<String, String> docs = new LinkedHashMap<>();
        for (String key : KEYS) docs.put(key, "");
        assertFalse(StorageDocumentSchema.hasMeaningfulLegacyData(docs, KEYS));
    }
}
