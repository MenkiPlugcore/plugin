# MENKIESTESParty v2.0.0 Storage Guide

MENKIESTESParty keeps the optional YAML/SQLite/MySQL persistence engine introduced in v1.5.x and adds a separate **Document Schema v2** architecture layer.

## Two version numbers in v2

Do not confuse these values:

```text
Document Schema:          v2
Storage backend protocol: v1
```

Document Schema describes the logical contents of the five Party documents. Storage backend protocol describes how those complete documents are persisted to YAML/SQLite/MySQL.

v2.0.0 deliberately keeps the proven backend/table protocol unchanged. No SQL table migration is required merely because the plugin version becomes 2.0.0.

Use:

```text
/partyarchitecture status
/partystorage status
```

to inspect both layers.

## Storage modes

`plugins/MENKIESTESParty/storage.yml`:

```yaml
storage:
  backend: YAML # YAML | SQLITE | MYSQL
```

- `YAML` — zero-setup local default.
- `SQLITE` — optional local SQL database (`storage.db`).
- `MYSQL` — optional external SQL backend.

SQLite/MySQL JDBC drivers remain bundled. There is no runtime driver download.

## Document Schema v2 migration

Before Party managers are initialized, Architecture v2 checks these existing logical documents:

```text
parties
wars
season
hall
interactions
```

A v1 document is upgraded by adding reserved `_menkiestesparty_*` metadata. Existing gameplay/social/admin data paths are not reorganized.

Before meaningful legacy data is changed, v2 creates:

```text
backups/pre-schema-v2-<timestamp>/
```

with all five YAML documents and a SHA-256 manifest. The migrated documents are structurally verified and then saved with the same durable storage path used by normal critical writes.

If the migration cannot be durably saved, startup is aborted and the captured pre-migration state is restored when possible.

See `ARCHITECTURE.md` for the full schema contract.

## Verified backend migration

Backend switching remains the v1.5.1 mechanism:

```text
/partystorage migrate SQLITE
/partystorage migrate MYSQL
/partystorage migrate YAML
```

The flow still performs durable source flush, pre-migration snapshot, target initialization, conflict guard, target write/readback checksum verification, atomic backend setting update and migration journal update.

A target containing different data is rejected by default. The explicit emergency override remains:

```text
/partystorage migrate MYSQL force
```

Do not use `force` as the normal migration path.

Document Schema v2 metadata moves together with the rest of each document, so later YAML/SQLite/MySQL backend migrations remain schema-preserving.

## Backend rollback

```text
/partystorage rollback
```

This is a **backend rollback**, not a Document Schema downgrade. It copies the current live state to the previous storage backend and verifies it before switching.

The `pre-schema-v2-*` snapshot is retained separately for manual schema-upgrade recovery/reference.

## SQL health and automatic recovery

Existing defaults remain:

```yaml
storage:
  health-check:
    interval-seconds: 30
    failures-before-fallback: 2
    auto-recover-sql: true
```

Repeated active SQL failure can switch the runtime to `YAML_FALLBACK`. Recovery reconnects to configured SQL, writes current state, reads it back, verifies checksums and only then restores SQL as the active backend.

Bounded SQL retry remains:

```yaml
storage:
  sql:
    retry:
      max-attempts: 2
      delay-millis: 250
```

## Async writer

Normal dirty writes continue through the single coalescing storage worker. There is no unbounded historical snapshot queue.

Critical reward receipts, schema migration and shutdown paths use durable blocking flush semantics where required.

`/partystorage status` reports backend configuration/health, queue counters, fallback state, backend migration state, **backend protocol**, and the current **Document Schema**.

## Backup types

Depending on operations, `plugins/MENKIESTESParty/backups/` may contain:

```text
manual-*
pre-migration-*
pre-import-*
recovery-*
pre-schema-v2-*
```

The first four are managed by the established storage backup/rotation system. `pre-schema-v2-*` is the one-time architecture upgrade safety snapshot and includes its own checksum manifest.

## MySQL example

```yaml
storage:
  backend: MYSQL
  mysql:
    host: 127.0.0.1
    port: 3306
    database: menkiestesparty
    username: menkiparty
    password: 'change-me'
```

MySQL remains optional. The release pipeline continues to run a real MySQL 8.4 service-container round-trip/upsert test.

## Commands

```text
/partystorage status
/partystorage verify
/partystorage flush
/partystorage backup
/partystorage migrate <YAML|SQLITE|MYSQL> [force]
/partystorage rollback

/partyarchitecture status
/partyarchitecture verify
```

## Folia

Storage I/O remains isolated behind its worker and scheduler boundary, but the complete gameplay stack is not Folia-certified in v2.0.0.

```yaml
compatibility:
  folia:
    experimental: false
```

Keep experimental Folia disabled for production unless you are intentionally testing unsupported compatibility behavior.
