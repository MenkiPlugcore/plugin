package id.cadera.menkiestesparty.architecture;

import java.util.concurrent.atomic.AtomicLong;

/** Default no-I/O transport used by standalone installations. */
public final class LocalNetworkTransport implements NetworkTransport {
    private final AtomicLong published = new AtomicLong();
    private volatile NetworkEnvelope last;

    @Override public String id() { return "LOCAL"; }
    @Override public boolean distributed() { return false; }
    @Override public boolean healthy() { return true; }
    @Override public long publishedCount() { return published.get(); }
    @Override public NetworkEnvelope lastEnvelope() { return last; }

    @Override
    public void publish(NetworkEnvelope envelope) {
        if (envelope == null) throw new IllegalArgumentException("envelope cannot be null");
        last = envelope;
        published.incrementAndGet();
    }
}
