# MENKIESTESParty v2.0.0 — Major Architecture Release

Release date: 2026-09-10

## Added

- Document Schema v2 for the five existing logical Party documents.
- Automatic v1 -> v2 document migration with pre-migration YAML snapshot and SHA-256 manifest.
- `architecture.yml` and generated `architecture-state.yml`.
- Stable per-installation node identity.
- Internal network transport boundary with zero-I/O `LOCAL` default transport.
- Event-driven Party revision envelopes using the existing public post-state event stream.
- Persisted monotonic Party revision counters plus a bounded last-envelope summary.
- `/partyarchitecture status|verify|reload` (`/parch`) diagnostics.
- Additive public `MenkiPartyAPIv2.API_VERSION = "2.0"` service.
- API v2 Runtime, Social Profile and richer Member/Party snapshots.
- API v2 Party revision values for version/cache comparison.
- New PlaceholderAPI values for API v1/v2 versions, document schema, storage protocol, node/network state and Party revision.
- `ARCHITECTURE.md` and `API_V2.md`.
- Pure-Java Document Schema/network-envelope/API v2 contract tests.

## Storage architecture

v2.0.0 intentionally separates **Document Schema v2** from the proven **storage backend protocol v1**.

The existing YAML/SQLite/MySQL backend contract and SQL table format remain unchanged. Existing gameplay/social/admin paths remain in place; v2 adds reserved `_menkiestesparty_*` metadata to each logical document.

Before meaningful legacy data is upgraded, a `backups/pre-schema-v2-*` directory is created containing all five documents and a checksum manifest. A failed durable schema migration aborts startup rather than intentionally continuing with mixed schema state.

No external database becomes mandatory. YAML remains the default.

## Public API compatibility

The original API remains:

```text
MenkiPartyAPI.API_VERSION = "1.0"
```

The new additive API is:

```text
MenkiPartyAPIv2.API_VERSION = "2.0"
```

Both services can coexist through Bukkit `ServicesManager`. Existing v1 consumer plugins do not need to rewrite immediately.

API v2 adds architecture/runtime state, social profile data, joined-at/activity member information and Party revisions while delegating controlled mutations to the existing validated core.

## Network foundation

v2.0.0 creates a transport/revision contract without forcing distributed infrastructure.

Default:

```yaml
architecture:
  network:
    mode: LOCAL
```

`LOCAL` does no external network I/O. Revision envelopes are emitted only from existing Party post-state events; v2.0.0 adds no network polling task.

Only a revision counter per Party and one last-envelope summary are persisted. No unbounded event history is introduced.

## Performance

- No Redis dependency.
- No proxy dependency.
- No network polling loop.
- No additional per-tick architecture scanner.
- LOCAL transport performs no external I/O.
- Existing coalesced async storage writer remains in use.
- Existing YAML/SQLite/MySQL backend protocol remains unchanged.

## Folia

Architecture v2 remains aware of the existing Folia scheduler boundary, but full gameplay region-thread safety is still not production-certified in v2.0.0. Folia remains blocked by default unless experimental compatibility is explicitly enabled.

## Not included / not claimed in v2.0.0

- Redis transport
- actual cross-server Party state replication
- Velocity-side Party module
- distributed locks/leader election
- proxy-wide online presence aggregation
- full Folia production support
- Web Inspector/Web Editor

The v2 architecture is designed so these can be added later behind stable boundaries instead of requiring another monolithic core rewrite.

## Upgrade from v1.7.x

1. Stop the server.
2. Keep the existing `plugins/MENKIESTESParty/` directory.
3. Replace the old JAR with v2.0.0.
4. Start normally and allow the one-time Document Schema migration.
5. Run `/partyarchitecture verify`.
6. Run `/partystorage verify`.
7. Run `/partyapi verify` to confirm legacy API v1 health.
8. Smoke-test Party roster, social profile, Projects, Contracts, War/Season and admin tools.

Do not delete existing data files before the upgrade.

## Compatibility

- Paper target remains 1.21.11.
- Java target remains Java 21 bytecode.
- Java 25 runtime probe remains a release gate.
- MySQL 8.4 integration remains a release gate.
- YAML remains the default backend; SQLite/MySQL remain optional.
- Existing administration safety, storage failover, social profile and Dynamic Identity contracts remain.
- No new required runtime dependency or service is introduced.
