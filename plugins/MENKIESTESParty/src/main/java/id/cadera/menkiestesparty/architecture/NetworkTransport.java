package id.cadera.menkiestesparty.architecture;

/**
 * Internal v2 transport seam. v2.0 ships LOCAL only; distributed transports
 * can implement this contract later without changing Party gameplay managers.
 */
public interface NetworkTransport extends AutoCloseable {
    String id();
    boolean distributed();
    boolean healthy();
    long publishedCount();
    NetworkEnvelope lastEnvelope();
    void publish(NetworkEnvelope envelope) throws Exception;

    @Override
    default void close() throws Exception {
    }
}
