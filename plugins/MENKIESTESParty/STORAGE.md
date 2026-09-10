# MENKIESTESParty v1.5.0 Storage Guide

MENKIESTESParty v1.5.0 keeps the public `MenkiPartyAPI` at **API v1.0** while moving persistence behind a raw-document storage boundary. Gameplay managers still use in-memory Bukkit `YamlConfiguration` objects; only persistence changes.

## Backends

Set `storage.backend` in `plugins/MENKIESTESParty/storage.yml`:

```yaml
storage:
  backend: YAML # YAML | SQLITE | MYSQL
```

### YAML

Default and safest drop-in upgrade. Existing `parties.yml`, `wars.yml`, `season.yml`, `hall.yml`, and `interactions.yml` remain the source of truth.

### SQLite

```yaml
storage:
  backend: SQLITE
  sqlite:
    file: storage.db
```

The database is created inside the plugin data folder unless an absolute path is supplied.

### MySQL

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

Create the database/user first and grant that user normal DDL/DML permissions for the configured database. The table defaults to `menkiestesparty_storage`.

## Safe migration

When SQLite/MySQL is selected and the SQL table contains no MENKIESTESParty documents, v1.5.0 imports the existing YAML documents once. It never imports over non-empty SQL data by default.

While SQL is primary, `mirror-yaml-backup: true` keeps the legacy YAML files warm as a local fallback/export copy.

If SQL initialization or a runtime SQL write fails and `fallback-to-yaml-on-error: true`, the current server session switches to YAML fallback. Fix SQL and perform a full restart to retry the configured SQL backend.

## Async persistence

Normal dirty saves are serialized on the main/global thread, then the immutable YAML strings are written by one dedicated storage worker. Pending writes are coalesced so a slow database cannot build an unbounded queue.

`ApiHardeningManager` reward receipts still call the plugin's durable blocking flush before Vault/console side effects. Shutdown also performs a durable save.

## Admin diagnostics

```text
/partystorage status
/partystorage verify
/partystorage flush
/partystorage backup
```

`backup` writes an independent YAML snapshot under `plugins/MENKIESTESParty/backups/` regardless of the active backend.

## Folia

v1.5.0 introduces a scheduler compatibility boundary and Folia runtime detection. **Full Folia support is not certified in v1.5.0.** Production should keep:

```yaml
compatibility:
  folia:
    experimental: false
```

The public API/storage abstraction is designed so later Folia work does not require changing API v1.0.
