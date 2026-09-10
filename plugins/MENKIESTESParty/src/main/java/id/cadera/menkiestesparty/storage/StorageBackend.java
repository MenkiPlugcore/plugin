package id.cadera.menkiestesparty.storage;

import java.util.Map;
import java.util.Set;

/**
 * Raw document persistence boundary used by MENKIESTESParty.
 *
 * Core managers continue to operate on Bukkit YamlConfiguration instances in
 * memory. Backends only persist serialized YAML documents, which lets storage
 * change without leaking backend details into gameplay code or the public API.
 */
public interface StorageBackend extends AutoCloseable {
    String type();

    void initialize() throws Exception;

    Map<String, String> load(Set<String> documentKeys) throws Exception;

    void save(Map<String, String> documents) throws Exception;

    boolean ping();

    String diagnostics();

    @Override
    default void close() throws Exception {
    }
}
