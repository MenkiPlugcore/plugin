# MENKIESTESParty v1.5.1 — Storage Stability & Migration Hardening

v1.5.1 hardens the optional storage architecture from v1.5.0 without changing `MenkiPartyAPI v1.0`.

## Added

- Verified live backend migration with `/partystorage migrate <backend>`.
- Pre-migration YAML snapshot and per-document SHA-256 verification.
- Non-empty target protection with explicit `force` override.
- `/partystorage rollback` that preserves current live data.
- Persistent `storage-migration.yml` journal.
- Incomplete migration detection after abnormal shutdown.
- `storage-state.yml` clean/unclean shutdown marker.
- Automatic recovery snapshot after unclean shutdown.
- SQL health checks, bounded retry policy and automatic SQL reconnect/resync.
- Queue counters, pending-age diagnostics and backlog warnings.
- Rotating backup retention with checksum manifests.
- Non-destructive default merging for existing `storage.yml` and `messages.yml`.
- MySQL 8.4 service-container integration test in CI.

## Compatibility

- Public `MenkiPartyAPI.API_VERSION` remains `1.0`.
- Existing YAML files remain valid.
- Existing v1.5.0 SQLite/MySQL table format remains valid.
- SQL drivers remain bundled in the production JAR.
- Java target remains 21 and Java 25 runtime probe remains a release gate.
- Folia scheduler mode remains experimental, not production-certified.

## Safety model

Migration does not switch the live backend until the target has been written and read back with matching SHA-256 checksums. If verification fails, the current backend remains active.

Normal writes stay asynchronous/coalesced. Reward-receipt and shutdown writes remain durable/blocking.
