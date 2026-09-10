package id.cadera.menkiestesparty.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class YamlStorageBackend implements StorageBackend {
    private final Path directory;

    public YamlStorageBackend(Path directory) {
        this.directory = directory;
    }

    @Override
    public String type() {
        return "YAML";
    }

    @Override
    public void initialize() throws IOException {
        Files.createDirectories(directory);
    }

    @Override
    public Map<String, String> load(Set<String> documentKeys) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : documentKeys) {
            Path file = fileFor(key);
            if (Files.isRegularFile(file)) {
                result.put(key, Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    @Override
    public void save(Map<String, String> documents) throws IOException {
        Files.createDirectories(directory);
        for (Map.Entry<String, String> entry : documents.entrySet()) {
            writeAtomic(fileFor(entry.getKey()), entry.getValue() == null ? "" : entry.getValue());
        }
    }

    @Override
    public boolean ping() {
        try {
            Files.createDirectories(directory);
            return Files.isDirectory(directory) && Files.isWritable(directory);
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public String diagnostics() {
        return "directory=" + directory.toAbsolutePath();
    }

    private Path fileFor(String key) {
        return directory.resolve(key + ".yml");
    }

    private static void writeAtomic(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
