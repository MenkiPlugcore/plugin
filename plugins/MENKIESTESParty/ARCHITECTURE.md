# MENKIESTESParty v2.0.0 — Architecture

MENKIESTESParty v2.0.0 introduces an architecture boundary for future storage/network evolution while keeping standalone installations lightweight.

## What changed

v2.0 separates three contracts that were previously easy to confuse:

1. **Document Schema v2** — the logical format of `parties`, `wars`, `season`, `hall`, and `interactions` documents.
2. **Storage backend protocol v1** — the proven raw-document persistence contract used by YAML, SQLite, and MySQL.
3. **Network envelope schema v1** — a small revision signal contract for future distributed transports.

The storage backend protocol intentionally remains v1. SQLite/MySQL table layout does not need to change for v2.0.0.

## Document Schema v2

On first v2.0 start, each existing logical document receives reserved top-level metadata:

```yaml
_menkiestesparty_document_schema: 2
_menkiestesparty_document_key: parties
_menkiestesparty_schema_migrated_at: 0
_menkiestesparty_schema_plugin_version: '2.0.0'
```

Existing Party/progression/social/admin/interaction fields remain at their old paths. The migration does not reorganize Party records.

Before migrating meaningful legacy data, v2 creates:

```text
plugins/MENKIESTESParty/backups/pre-schema-v2-<timestamp>/
```

The directory contains all five logical YAML documents plus `manifest.yml` with SHA-256 checksums, source backend, source/target document schema, plugin version, and creation time.

Migration flow:

```text
load current backend
 -> detect document schema
 -> create pre-schema-v2 snapshot
 -> build v2 documents in memory
 -> structural verify
 -> durable save to active backend
 -> post-write verify
 -> record architecture-state.yml
```

If the durable migration fails, MENKIESTESParty attempts to restore the captured pre-migration documents and aborts startup instead of knowingly continuing with a half-migrated state.

Defaults in `architecture.yml`:

```yaml
architecture:
  storage:
    document-schema:
      auto-migrate: true
      block-legacy-startup: true
```

## Node identity

Every installation gets a stable node id. The default:

```yaml
architecture:
  network:
    node-id: auto
```

is replaced on first start with a persisted value similar to:

```text
node-a18f42b931de
```

Valid custom node ids use lowercase letters, digits, `.`, `_`, and `-`, length 3-48.

## Network foundation

v2.0.0 ships only the `LOCAL` transport:

```yaml
architecture:
  network:
    mode: LOCAL
```

`LOCAL` performs no external network I/O. It is not Redis and it does not replicate Party state to another server.

The architecture layer listens to the existing post-state Party event stream and emits immutable revision envelopes for transitions such as Party create/disband, roster changes, level changes, Project completion, Contract status, Diplomacy changes, and War transitions.

An envelope contains:

```text
schemaVersion
eventId
nodeId
partyKey
revision
eventType
createdAt
```

Only a monotonic revision counter per Party and one last-envelope summary are persisted under `interactions.yml`. There is no unbounded network-event history.

No additional polling scheduler is introduced. When `developer.events.enabled=false`, the existing post-state event bridge is disabled and architecture envelopes therefore do not advance.

## What v2.0.0 does NOT claim

v2.0.0 does not include:

- Redis client/runtime dependency
- Velocity plugin/module
- cross-server Party state replication
- distributed locking or leader election
- proxy-aware online presence aggregation
- full Folia production certification

Those features can now be added behind the architecture/transport boundary without requiring another core Party rewrite.

## Folia

The existing scheduler compatibility boundary remains. Folia is detected explicitly, but default production behavior still blocks Folia unless experimental mode is enabled in `storage.yml`.

Architecture v2 is Folia-aware; the entire gameplay stack is **not** certified region-thread-safe in v2.0.0.

## Commands

```text
/partyarchitecture status
/partyarchitecture verify
/partyarchitecture reload
```

Alias:

```text
/parch
```

Permission:

```text
menkiestesparty.architecture.inspect
```

`status` shows document schema, backend protocol, backend state, node id, configured/active network mode, transport health, revision sequence, scheduler mode, and the explicit Folia certification state.

`verify` performs structural v2 checks and then verifies the active storage backend.

## Files

```text
architecture.yml        v2 architecture configuration
architecture-state.yml  generated architecture/schema state
storage.yml             backend/storage settings from v1.5+
```

Social settings remain in `social.yml`. Administration settings remain in `administration.yml`.

## Upgrade from v1.7.x

1. Stop the server completely.
2. Keep the entire `plugins/MENKIESTESParty/` directory.
3. Replace the old JAR with `MENKIESTESParty-2.0.0.jar`.
4. Start the server normally.
5. Check console for the Document Schema v1 -> v2 migration message.
6. Run `/partyarchitecture verify`.
7. Run `/partystorage verify` and `/partyapi verify`.
8. Confirm Party roster, social profile, Projects, Contracts, War/Season, and admin tooling before deleting any old backup.

Do not delete existing YAML/SQLite/MySQL data before upgrading.

## Manual recovery reference

If a schema migration fails, use the reported `pre-schema-v2-*` snapshot for forensic/manual recovery. v2.0.0 does not automatically overwrite a live installation from that backup after startup failure.

The migration is deliberately additive: previous gameplay keys are preserved, while reserved `_menkiestesparty_*` fields identify the new document contract.
