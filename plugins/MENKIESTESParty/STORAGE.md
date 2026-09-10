# MENKIESTESParty v1.5.1 Storage Guide

MENKIESTESParty v1.5.1 keeps `MenkiPartyAPI.API_VERSION = 1.0` and hardens the optional YAML/SQLite/MySQL persistence layer introduced in v1.5.0.

## Storage modes

`plugins/MENKIESTESParty/storage.yml`:

```yaml
storage:
  backend: YAML # YAML | SQLITE | MYSQL
```

- `YAML` is the zero-setup local default.
- `SQLITE` is a local SQL database in `storage.db` by default.
- `MYSQL` is an external SQL backend for deployments that want shared/managed database infrastructure.

SQLite and MySQL are optional. Their JDBC drivers are bundled in the production JAR so no driver download is performed during server boot.

## Verified live migration

Use:

```text
/partystorage migrate SQLITE
/partystorage migrate MYSQL
/partystorage migrate YAML
```

The migration pipeline is:

1. capture the current in-memory Party documents,
2. perform a durable flush to the currently active backend,
3. create a pre-migration YAML snapshot under `backups/`,
4. calculate SHA-256 for all five documents,
5. initialize the target backend,
6. refuse a non-empty different target by default,
7. write the captured snapshot,
8. read the target back and compare every checksum,
9. atomically persist `storage.backend`,
10. switch the live backend,
11. record the completed migration in `storage-migration.yml`.

If target write or verification fails before the commit point, the old backend remains active.

A target that already contains different data is intentionally rejected. An administrator may explicitly override this after reviewing backups:

```text
/partystorage migrate MYSQL force
```

Do not use `force` as a normal migration path.

## Rollback

```text
/partystorage rollback
```

Rollback does **not** silently restore stale pre-migration data. It takes the current live in-memory state, creates another safety snapshot, writes/verifies it into the previous backend, then switches back. This preserves changes made after the original migration.

The old pre-migration snapshot remains available for manual disaster recovery.

## Migration journal

`storage-migration.yml` stores:

- migration state,
- operation (`MIGRATE` or `ROLLBACK`),
- source and target backend,
- pre-migration backup path,
- SHA-256 document checksums,
- error state,
- last successful migration.

If the process stops during a migration, the next boot detects the `IN_PROGRESS` journal. Because `storage.backend` is changed only after target verification, startup can safely remain on the old backend or recover the already-verified target.

## SQL health and automatic recovery

v1.5.1 periodically checks the active backend. Defaults:

```yaml
storage:
  health-check:
    interval-seconds: 30
    failures-before-fallback: 2
    auto-recover-sql: true
```

When active SQL repeatedly fails and YAML fallback is enabled, MENKIESTESParty switches to `YAML_FALLBACK`.

While running in fallback, the storage worker periodically attempts to reconnect to the configured SQL backend. A recovery is accepted only after current data is written to SQL and read back with matching checksums. The plugin then switches back to SQL without requiring a restart.

SQL operations also have a bounded retry policy:

```yaml
storage:
  sql:
    retry:
      max-attempts: 2
      delay-millis: 250
```

Retries execute on the dedicated storage worker for normal persistence. Keep retry counts and connection timeouts bounded.

## Async queue

Normal dirty-state writes are coalesced. There is never an unbounded queue of historical snapshots.

`/partystorage status` reports:

- submitted snapshots,
- coalesced snapshots,
- successful/failed writes,
- current pending age,
- last queue delay,
- health failures,
- SQL recovery attempts,
- migration state.

A warning is logged when pending data remains above `storage.queue.warn-after-millis`.

Reward receipts from API hardening and server shutdown continue to use durable blocking flushes.

## Backup rotation

Every manual/migration/import/recovery snapshot contains the five YAML documents plus `manifest.yml` with SHA-256 checksums.

```yaml
storage:
  backups:
    max-count: 12
```

Oldest snapshot directories are pruned automatically after a new backup is created.

## Unclean shutdown detection

`storage-state.yml` records storage schema/version and whether the previous server session completed a clean shutdown.

If an unclean shutdown is detected, v1.5.1 preserves the successfully loaded backend state as a `recovery-*` snapshot before normal operation. YAML file replacement remains atomic and JDBC writes remain transactional.

This does not claim to recover RAM changes that were never persisted.

## MySQL

Example:

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

The v1.5.1 CI pipeline runs an actual MySQL 8.4 service-container round-trip/upsert test in addition to SQLite tests.

## Admin commands

```text
/partystorage status
/partystorage verify
/partystorage flush
/partystorage backup
/partystorage migrate <YAML|SQLITE|MYSQL> [force]
/partystorage rollback
```

## Folia

The scheduler compatibility boundary remains present, but full region-thread correctness is not production-certified in v1.5.1.

```yaml
compatibility:
  folia:
    experimental: false
```

Keep this disabled for production Folia servers until a dedicated compatibility release certifies every gameplay listener and mutation path.
