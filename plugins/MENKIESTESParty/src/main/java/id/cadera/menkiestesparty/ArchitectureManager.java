package id.cadera.menkiestesparty;

import id.cadera.menkiestesparty.api.event.PartyContractStatusEvent;
import id.cadera.menkiestesparty.api.event.PartyCreateEvent;
import id.cadera.menkiestesparty.api.event.PartyDisbandEvent;
import id.cadera.menkiestesparty.api.event.PartyLevelChangeEvent;
import id.cadera.menkiestesparty.api.event.PartyMemberChangeEvent;
import id.cadera.menkiestesparty.api.event.PartyProjectCompleteEvent;
import id.cadera.menkiestesparty.api.event.PartyRelationChangeEvent;
import id.cadera.menkiestesparty.api.event.PartyWarStateEvent;
import id.cadera.menkiestesparty.architecture.LocalNetworkTransport;
import id.cadera.menkiestesparty.architecture.NetworkEnvelope;
import id.cadera.menkiestesparty.architecture.NetworkTransport;
import id.cadera.menkiestesparty.architecture.StorageDocumentSchema;
import id.cadera.menkiestesparty.storage.StorageIntegrity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MENKIESTESParty v2 architecture boundary.
 *
 * Responsibilities are deliberately narrow:
 * - migrate the five existing logical documents to Document Schema v2;
 * - own stable node identity and revision envelopes;
 * - provide architecture diagnostics without introducing a polling loop;
 * - keep the default transport LOCAL/no-I/O.
 *
 * This class does not claim distributed state replication. v2.0 establishes
 * the contract required for a future transport without making Redis or a proxy
 * plugin mandatory for standalone servers.
 */
public final class ArchitectureManager implements Listener, CommandExecutor, TabCompleter, AutoCloseable {
    private static final List<String> DOCUMENT_KEYS = List.of(
            "parties", "wars", "season", "hall", "interactions"
    );
    private static final DateTimeFormatter BACKUP_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final MENKIESTESPartyPlugin plugin;
    private final StorageBundle storage;
    private final Path configPath;
    private final Path statePath;
    private final NetworkTransport transport = new LocalNetworkTransport();
    private final Map<String, Long> sessionRevisions = new ConcurrentHashMap<>();

    private YamlConfiguration config;
    private String nodeId;
    private String configuredNetworkMode = "LOCAL";
    private String activeNetworkMode = "LOCAL";
    private String schemaState = "UNKNOWN";
    private int schemaFrom = 1;
    private Path schemaBackup;
    private String lastSchemaError = "";
    private String lastNetworkError = "";

    public ArchitectureManager(MENKIESTESPartyPlugin plugin, StorageBundle storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.configPath = plugin.getDataFolder().toPath().resolve("architecture.yml");
        this.statePath = plugin.getDataFolder().toPath().resolve("architecture-state.yml");
        loadConfig();
        this.nodeId = ensureNodeId();
        resolveNetworkMode();
        migrateDocumentSchemaIfRequired();

        PluginCommand command = plugin.getCommand("partyarchitecture");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    public void reload() {
        loadConfig();
        this.nodeId = ensureNodeId();
        resolveNetworkMode();
        if (StorageDocumentSchema.requiresMigration(documents(), DOCUMENT_KEYS)) {
            migrateDocumentSchemaIfRequired();
        }
    }

    private void loadConfig() {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            if (!Files.isRegularFile(configPath)) plugin.saveResource("architecture.yml", false);
            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(configPath.toFile());
            try (InputStream input = plugin.getResource("architecture.yml")) {
                if (input != null) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(input, StandardCharsets.UTF_8));
                    loaded.setDefaults(defaults);
                    loaded.options().copyDefaults(true);
                }
            }
            this.config = loaded;
            writeAtomic(configPath, loaded.saveToString());
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to load architecture.yml: " + safe(failure), failure);
        }
    }

    private String ensureNodeId() {
        String configured = config.getString("architecture.network.node-id", "auto");
        String value = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        if (value.equals("auto") || value.isBlank()) {
            value = "node-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            config.set("architecture.network.node-id", value);
            try {
                writeAtomic(configPath, config.saveToString());
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to persist generated architecture node-id", failure);
            }
        }
        if (!validNodeId(value)) {
            throw new IllegalStateException("architecture.network.node-id must match [a-z0-9._-]{3,48}");
        }
        return value;
    }

    private void resolveNetworkMode() {
        configuredNetworkMode = config.getString("architecture.network.mode", "LOCAL");
        configuredNetworkMode = configuredNetworkMode == null
                ? "LOCAL" : configuredNetworkMode.trim().toUpperCase(Locale.ROOT);
        if (configuredNetworkMode.equals("LOCAL")) {
            activeNetworkMode = "LOCAL";
            lastNetworkError = "";
            return;
        }

        activeNetworkMode = "LOCAL_FALLBACK";
        lastNetworkError = "unsupported transport mode " + configuredNetworkMode + " in v2.0.0";
        boolean strict = config.getBoolean("architecture.network.fail-on-unsupported-mode", false);
        if (strict) throw new IllegalStateException(lastNetworkError);
        plugin.getLogger().warning("Architecture network mode '" + configuredNetworkMode
                + "' is not bundled in v2.0.0; using LOCAL transport. "
                + "No distributed replication is being claimed.");
    }

    // ---------------------------------------------------------------------
    // Document Schema v2
    // ---------------------------------------------------------------------

    private void migrateDocumentSchemaIfRequired() {
        Map<String, String> before = documents();
        schemaFrom = StorageDocumentSchema.minimumVersion(before, DOCUMENT_KEYS);
        if (!StorageDocumentSchema.requiresMigration(before, DOCUMENT_KEYS)) {
            schemaState = "CURRENT";
            lastSchemaError = "";
            writeState();
            return;
        }

        boolean auto = config.getBoolean("architecture.storage.document-schema.auto-migrate", true);
        boolean blockLegacy = config.getBoolean("architecture.storage.document-schema.block-legacy-startup", true);
        if (!auto) {
            schemaState = "LEGACY";
            lastSchemaError = "Document Schema v2 migration is required but auto-migrate=false";
            writeState();
            if (blockLegacy) throw new IllegalStateException(lastSchemaError);
            plugin.getLogger().warning(lastSchemaError + "; continuing because block-legacy-startup=false.");
            return;
        }

        try {
            if (StorageDocumentSchema.hasMeaningfulLegacyData(before, DOCUMENT_KEYS)) {
                schemaBackup = createSchemaBackup(before, schemaFrom);
            }
            long now = System.currentTimeMillis();
            Map<String, String> migrated = StorageDocumentSchema.migrate(
                    before, DOCUMENT_KEYS, now, plugin.getDescription().getVersion());
            if (!StorageDocumentSchema.verify(migrated, DOCUMENT_KEYS)) {
                throw new IllegalStateException("generated schema-v2 documents failed structural verification");
            }

            loadDocuments(migrated);
            if (!storage.saveAllBlocking()) {
                throw new IllegalStateException("durable storage write failed during Document Schema v2 migration");
            }
            Map<String, String> after = documents();
            if (!StorageDocumentSchema.verify(after, DOCUMENT_KEYS)) {
                throw new IllegalStateException("in-memory post-write schema verification failed");
            }

            schemaState = "MIGRATED";
            lastSchemaError = "";
            writeState();
            plugin.getLogger().info("Document Schema migrated v" + schemaFrom + " -> v"
                    + StorageDocumentSchema.CURRENT_VERSION
                    + (schemaBackup == null ? "" : "; backup=" + schemaBackup.toAbsolutePath()));
        } catch (Exception failure) {
            schemaState = "FAILED";
            lastSchemaError = safe(failure);
            try {
                loadDocuments(before);
                if (!storage.saveAllBlocking()) {
                    plugin.getLogger().severe("Document Schema rollback write also failed. Use backup: "
                            + (schemaBackup == null ? "none" : schemaBackup.toAbsolutePath()));
                }
            } catch (Exception restoreFailure) {
                plugin.getLogger().severe("Unable to restore pre-schema-v2 in-memory documents: " + safe(restoreFailure));
            }
            writeState();
            throw new IllegalStateException("Document Schema v2 migration aborted: " + lastSchemaError, failure);
        }
    }

    private Path createSchemaBackup(Map<String, String> documents, int fromVersion) throws Exception {
        Path root = plugin.getDataFolder().toPath().resolve("backups").resolve(
                "pre-schema-v2-" + BACKUP_STAMP.format(LocalDateTime.now()));
        Files.createDirectories(root);
        for (String key : DOCUMENT_KEYS) {
            writeAtomic(root.resolve(key + ".yml"), documents.getOrDefault(key, ""));
        }

        YamlConfiguration manifest = new YamlConfiguration();
        manifest.set("backup-format", 1);
        manifest.set("reason", "pre-schema-v2");
        manifest.set("document-schema-from", fromVersion);
        manifest.set("document-schema-to", StorageDocumentSchema.CURRENT_VERSION);
        manifest.set("storage-backend-protocol", storage.schemaVersion());
        manifest.set("source-backend", storage.activeBackend());
        manifest.set("plugin-version", plugin.getDescription().getVersion());
        manifest.set("created-at", System.currentTimeMillis());
        for (Map.Entry<String, String> entry : StorageIntegrity.checksums(documents).entrySet()) {
            manifest.set("checksums." + entry.getKey(), entry.getValue());
        }
        writeAtomic(root.resolve("manifest.yml"), manifest.saveToString());
        return root;
    }

    private Map<String, String> documents() {
        Map<String, String> documents = new LinkedHashMap<>();
        documents.put("parties", storage.parties.saveToString());
        documents.put("wars", storage.wars.saveToString());
        documents.put("season", storage.season.saveToString());
        documents.put("hall", storage.hall.saveToString());
        documents.put("interactions", storage.interactions.saveToString());
        return Map.copyOf(documents);
    }

    private void loadDocuments(Map<String, String> documents) throws InvalidConfigurationException {
        load(storage.parties, documents.get("parties"));
        load(storage.wars, documents.get("wars"));
        load(storage.season, documents.get("season"));
        load(storage.hall, documents.get("hall"));
        load(storage.interactions, documents.get("interactions"));
    }

    private static void load(YamlConfiguration target, String payload) throws InvalidConfigurationException {
        for (String key : new ArrayList<>(target.getKeys(false))) target.set(key, null);
        if (payload != null && !payload.isBlank()) target.loadFromString(payload);
    }

    private void writeState() {
        try {
            YamlConfiguration state = Files.isRegularFile(statePath)
                    ? YamlConfiguration.loadConfiguration(statePath.toFile())
                    : new YamlConfiguration();
            state.set("architecture-version", 2);
            state.set("plugin-version", plugin.getDescription().getVersion());
            state.set("document-schema.current", documentSchemaVersion());
            state.set("document-schema.target", StorageDocumentSchema.CURRENT_VERSION);
            state.set("document-schema.state", schemaState);
            state.set("document-schema.from", schemaFrom);
            state.set("document-schema.last-error", lastSchemaError);
            state.set("document-schema.backup", schemaBackup == null ? null : schemaBackup.toAbsolutePath().toString());
            state.set("node.id", nodeId);
            state.set("network.configured-mode", configuredNetworkMode);
            state.set("network.active-mode", activeNetworkMode);
            state.set("network.distributed", transport.distributed());
            state.set("updated-at", System.currentTimeMillis());
            writeAtomic(statePath, state.saveToString());
        } catch (Exception failure) {
            plugin.getLogger().warning("Unable to write architecture-state.yml: " + safe(failure));
        }
    }

    // ---------------------------------------------------------------------
    // Event-driven revision envelopes. No polling and no history queue.
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreate(PartyCreateEvent event) {
        publish(event.partyKey(), "PARTY_CREATE");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisband(PartyDisbandEvent event) {
        publish(event.partyKey(), "PARTY_DISBAND");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMember(PartyMemberChangeEvent event) {
        publish(event.partyKey(), "MEMBER_" + event.action().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PartyLevelChangeEvent event) {
        publish(event.partyKey(), "LEVEL_CHANGE");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProject(PartyProjectCompleteEvent event) {
        publish(event.partyKey(), "PROJECT_COMPLETE");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onContract(PartyContractStatusEvent event) {
        publish(event.contract().targetParty(), "CONTRACT_" + event.newStatus());
        if (!event.contract().sourceParty().equalsIgnoreCase(event.contract().targetParty())) {
            publish(event.contract().sourceParty(), "CONTRACT_" + event.newStatus());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRelation(PartyRelationChangeEvent event) {
        publish(event.relation().firstParty(), "RELATION_CHANGE");
        if (!event.relation().secondParty().equalsIgnoreCase(event.relation().firstParty())) {
            publish(event.relation().secondParty(), "RELATION_CHANGE");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWar(PartyWarStateEvent event) {
        publish(event.winnerParty() == null ? "_global" : event.winnerParty(), "WAR_" + event.newPhase());
    }

    private void publish(String rawPartyKey, String rawType) {
        if (!config.getBoolean("architecture.network.publish-event-envelopes", true)) return;
        String partyKey = pathKey(rawPartyKey);
        String type = rawType == null || rawType.isBlank()
                ? "UNKNOWN" : rawType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        long next = Math.max(0L, revision(partyKey)) + 1L;
        sessionRevisions.put(partyKey, next);
        long now = System.currentTimeMillis();
        NetworkEnvelope envelope = NetworkEnvelope.create(nodeId, partyKey, next, type, now);

        try {
            transport.publish(envelope);
            lastNetworkError = "";
        } catch (Exception failure) {
            lastNetworkError = safe(failure);
            plugin.getLogger().warning("Architecture transport publish failed: " + lastNetworkError);
        }

        if (config.getBoolean("architecture.network.persist-revisions", true)) {
            String root = "architecture.network";
            storage.interactions.set(root + ".revisions." + partyKey, next);
            storage.interactions.set(root + ".sequence",
                    Math.max(0L, storage.interactions.getLong(root + ".sequence", 0L)) + 1L);
            storage.interactions.set(root + ".last.event-id", envelope.eventId().toString());
            storage.interactions.set(root + ".last.node-id", envelope.nodeId());
            storage.interactions.set(root + ".last.party-key", envelope.partyKey());
            storage.interactions.set(root + ".last.revision", envelope.revision());
            storage.interactions.set(root + ".last.type", envelope.eventType());
            storage.interactions.set(root + ".last.created-at", envelope.createdAt());
            plugin.saveDataSoon();
        }
    }

    public long revision(String rawPartyKey) {
        String partyKey = pathKey(rawPartyKey);
        long persisted = storage.interactions.getLong("architecture.network.revisions." + partyKey, 0L);
        return Math.max(persisted, sessionRevisions.getOrDefault(partyKey, 0L));
    }

    public long networkSequence() {
        return storage.interactions.getLong("architecture.network.sequence", transport.publishedCount());
    }

    public NetworkEnvelope lastEnvelope() {
        return transport.lastEnvelope();
    }

    // ---------------------------------------------------------------------
    // Diagnostics command
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("menkiestesparty.architecture.inspect")
                && !sender.hasPermission("menkiestesparty.admin")) {
            plugin.messages().send(sender, "common.no-permission");
            return true;
        }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "status" -> status(sender);
            case "verify" -> verify(sender);
            case "reload" -> reloadCommand(sender);
            default -> {
                sender.sendMessage(Util.color("&7Usage: /partyarchitecture <status|verify|reload>"));
                yield true;
            }
        };
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage(Util.color("&8&m--------------------------------"));
        sender.sendMessage(Util.color("&b&lMENKIESTESParty ARCHITECTURE v2"));
        sender.sendMessage(Util.color("&7Plugin: &f" + plugin.getDescription().getVersion()
                + " &8| &7API v1: &f1.0 &8| &7API v2: &f2.0"));
        sender.sendMessage(Util.color("&7Document schema: &fv" + documentSchemaVersion()
                + " &8| &7State: &f" + schemaState));
        sender.sendMessage(Util.color("&7Storage backend protocol: &fv" + storage.schemaVersion()
                + " &8| &7Backend: &f" + storage.activeBackend()));
        sender.sendMessage(Util.color("&7Node: &f" + nodeId
                + " &8| &7Network: &f" + activeNetworkMode));
        sender.sendMessage(Util.color("&7Transport: &f" + transport.id()
                + " &8| &7Distributed: &f" + transport.distributed()
                + " &8| &7Healthy: &f" + transport.healthy()));
        sender.sendMessage(Util.color("&7Revision sequence: &f" + networkSequence()
                + " &8| &7Emitted this boot: &f" + transport.publishedCount()));
        sender.sendMessage(Util.color("&7Scheduler: &f" + plugin.schedulerCompat().mode()
                + " &8| &7Folia certified: &cfalse"));
        if (schemaBackup != null) sender.sendMessage(Util.color("&7Schema backup: &f" + schemaBackup.toAbsolutePath()));
        if (!lastSchemaError.isBlank()) sender.sendMessage(Util.color("&cSchema error: &f" + lastSchemaError));
        if (!lastNetworkError.isBlank()) sender.sendMessage(Util.color("&eNetwork note: &f" + lastNetworkError));
        return true;
    }

    private boolean verify(CommandSender sender) {
        boolean structure = architectureHealthy();
        sender.sendMessage(Util.color("&7Architecture structure: " + (structure ? "&aOK" : "&cFAILED")
                + " &8| &7checking active storage backend..."));
        storage.verifyAsync().whenComplete((healthy, failure) -> plugin.schedulerCompat().runGlobal(() -> {
            boolean backendHealthy = failure == null && Boolean.TRUE.equals(healthy);
            sender.sendMessage(Util.color("&7Storage backend: " + (backendHealthy ? "&aOK" : "&cFAILED")));
            sender.sendMessage(Util.color("&7v2 verification: "
                    + (structure && backendHealthy ? "&aPASS" : "&cFAIL")));
        }));
        return true;
    }

    private boolean reloadCommand(CommandSender sender) {
        if (!sender.hasPermission("menkiestesparty.admin")) {
            plugin.messages().send(sender, "common.no-permission");
            return true;
        }
        reload();
        sender.sendMessage(Util.color("&aarchitecture.yml reloaded. Node=&f" + nodeId
                + " &aNetwork=&f" + activeNetworkMode));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("menkiestesparty.architecture.inspect")
                && !sender.hasPermission("menkiestesparty.admin")) return List.of();
        if (args.length != 1) return List.of();
        String typed = args[0].toLowerCase(Locale.ROOT);
        return List.of("status", "verify", "reload").stream()
                .filter(value -> value.startsWith(typed)).toList();
    }

    public boolean architectureHealthy() {
        return documentSchemaVersion() == StorageDocumentSchema.CURRENT_VERSION
                && StorageDocumentSchema.verify(documents(), DOCUMENT_KEYS)
                && validNodeId(nodeId)
                && transport.healthy();
    }

    public boolean apiV2Enabled() {
        return config.getBoolean("architecture.api-v2.enabled", true);
    }

    public int documentSchemaVersion() {
        return StorageDocumentSchema.minimumVersion(documents(), DOCUMENT_KEYS);
    }

    public String schemaState() { return schemaState; }
    public int schemaFromVersion() { return schemaFrom; }
    public Path schemaBackup() { return schemaBackup; }
    public String nodeId() { return nodeId; }
    public String configuredNetworkMode() { return configuredNetworkMode; }
    public String activeNetworkMode() { return activeNetworkMode; }
    public boolean distributedTransport() { return transport.distributed(); }
    public boolean transportHealthy() { return transport.healthy(); }
    public long emittedThisBoot() { return transport.publishedCount(); }
    public String lastNetworkError() { return lastNetworkError; }
    public boolean eventBridgeEnabled() {
        return plugin.getConfig().getBoolean("developer.events.enabled", true);
    }

    @Override
    public void close() {
        try {
            transport.close();
        } catch (Exception failure) {
            plugin.getLogger().warning("Architecture transport close warning: " + safe(failure));
        }
        writeState();
    }

    public static boolean validNodeId(String value) {
        return value != null && value.matches("[a-z0-9._-]{3,48}");
    }

    private static String pathKey(String value) {
        if (value == null || value.isBlank()) return "_global";
        return value.trim().toLowerCase(Locale.ROOT).replace('.', '_').replace(' ', '_');
    }

    private static String safe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static void writeAtomic(Path target, String content) throws Exception {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content == null ? "" : content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
