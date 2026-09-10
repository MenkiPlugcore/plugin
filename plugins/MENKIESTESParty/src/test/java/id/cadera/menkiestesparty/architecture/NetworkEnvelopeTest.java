package id.cadera.menkiestesparty.architecture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkEnvelopeTest {

    @Test
    void envelopeNormalizesEventTypeAndRequiresMonotonicRevision() {
        NetworkEnvelope envelope = NetworkEnvelope.create("node-a", "party_one", 7L, "member join", 1234L);
        assertEquals(NetworkEnvelope.CURRENT_SCHEMA, envelope.schemaVersion());
        assertEquals("node-a", envelope.nodeId());
        assertEquals("party_one", envelope.partyKey());
        assertEquals(7L, envelope.revision());
        assertEquals("MEMBER JOIN", envelope.eventType());
        assertNotNull(envelope.eventId());
    }

    @Test
    void invalidEnvelopeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> NetworkEnvelope.create("", "party", 1L, "create", 0L));
        assertThrows(IllegalArgumentException.class,
                () -> NetworkEnvelope.create("node-a", "party", 0L, "create", 0L));
    }

    @Test
    void localTransportHasNoExternalDistribution() throws Exception {
        LocalNetworkTransport transport = new LocalNetworkTransport();
        NetworkEnvelope envelope = NetworkEnvelope.create("node-a", "party", 1L, "create", 1L);
        transport.publish(envelope);
        assertEquals("LOCAL", transport.id());
        assertFalse(transport.distributed());
        assertTrue(transport.healthy());
        assertEquals(1L, transport.publishedCount());
        assertEquals(envelope, transport.lastEnvelope());
    }
}
