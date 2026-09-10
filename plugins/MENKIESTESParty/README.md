# MENKIESTESParty v2.0.0

Modular Paper Party/Guild framework by **CADERA** for Paper 1.21.11 / Java 21.

MENKIESTESParty remains a standalone public plugin. YAML is still the default lightweight backend; SQLite/MySQL and developer integrations remain optional.

## Core systems

- Party create/invite/roster/roles/home/chat
- Party Level, Weekly Quest, Party Relic and Daily Missions
- Party Projects, Skill Tree and Divisions
- automatic Dynamic Party Identity
- Social Party Profiles, achievements, badges and leaderboards
- Party Contracts, Diplomacy/Trust and Recruitment/Applications
- Notification Inbox + Recent Activity
- Party War + Season
- hardened Administration, Recovery & Observability
- Document Schema v2 + architecture diagnostics
- Public API v1.0 and additive API v2.0

## Architecture v2

v2.0.0 introduces a clean architecture boundary without turning a normal single-server installation into a distributed system.

```text
Document Schema v2
Storage backend protocol v1 (unchanged)
Network envelope schema v1
LOCAL transport (default, no network I/O)
```

On first upgrade from v1.x, the five existing logical documents are migrated additively to Document Schema v2. Before meaningful legacy data is changed, MENKIESTESParty creates a `pre-schema-v2-*` YAML backup with SHA-256 checksums.

Existing Party/social/admin/progression data paths remain in place. SQLite/MySQL table layout is not rewritten for v2.0.0.

Architecture diagnostics:

```text
/partyarchitecture status
/partyarchitecture verify
/partyarchitecture reload
```

See [`ARCHITECTURE.md`](ARCHITECTURE.md).

## Network foundation

v2.0.0 ships `LOCAL` transport only. It emits Party revision envelopes from the existing post-state event stream and performs no external network I/O.

There is **no Redis dependency, no network polling loop and no cross-server state replication claim** in v2.0.0. The transport boundary exists so a future distributed module can be added without rewriting Party gameplay managers.

## Public APIs

Legacy API stays available:

```text
MenkiPartyAPI.API_VERSION = "1.0"
```

New additive API:

```text
MenkiPartyAPIv2.API_VERSION = "2.0"
```

API v2 adds runtime architecture information, Social Profile snapshots, richer Member snapshots and Party revision values. API v1 remains registered for existing integrations.

See:

- [`API.md`](API.md) — legacy API v1 guide
- [`API_V2.md`](API_V2.md) — API v2 guide
- [`API_COMPATIBILITY.md`](API_COMPATIBILITY.md) — coexistence/versioning policy

## Social & Party Identity

```text
/party profile [party|player]
/party social ...
/party badges
/party top [reputation|level|members|projects|activity|age] [page]
```

Dynamic Party Identity remains automatic/activity-derived. The Social Identity layer provides description, unique tag, color/icon, public/private visibility, achievements and active badges without creating a second gameplay activity tracker.

See [`SOCIAL_IDENTITY.md`](SOCIAL_IDENTITY.md).

## Storage

Available backends:

```text
YAML    default/local
SQLite  optional local SQL
MySQL   optional external SQL
```

SQL is not required. Existing v1.5+ verified backend migration, fallback/recovery, bounded async writer, backups and diagnostics remain.

In v2 terminology, `/partystorage` reports the storage backend protocol while `/partyarchitecture` reports the logical Document Schema.

See [`STORAGE.md`](STORAGE.md).

## Administration

The v1.6.x safety model remains: granular staff permissions, token-confirmed dangerous actions, bounded audit history, verified snapshots, repair dry-run and read-only recovery diagnostics.

See [`ADMINISTRATION.md`](ADMINISTRATION.md).

## PlaceholderAPI v2 additions

Existing placeholders remain. New architecture values include:

```text
%mparty_api_v1_version%
%mparty_api_v2_version%
%mparty_document_schema%
%mparty_storage_protocol%
%mparty_node_id%
%mparty_network_mode%
%mparty_network_distributed%
%mparty_party_revision%
```

For backward compatibility, `%mparty_api_version%` continues to mean API v1 (`1.0`).

## Production defaults

- Storage backend: YAML
- Document Schema: v2, auto-migrate from v1
- Schema migration: pre-migration backup + durable verification
- Network mode: LOCAL
- Distributed network: disabled/not bundled
- Redis: not required/not bundled
- Public APIs: v1.0 + v2.0
- Social profiles: enabled
- Dynamic Identity: automatic
- Dangerous admin actions: exactly-once token confirmation
- Folia: experimental only; not production-certified in v2.0.0

## Build and release gates

```bash
gradle clean build
```

GitHub Actions verifies Java 21 compilation/tests, API v1/v2 contracts, storage/admin/social/architecture tests, production JAR contents, MySQL 8.4 integration, Java 25 runtime compatibility, documentation and automatic GitHub Release publication.

## Documentation

- [`CHANGELOG.md`](CHANGELOG.md)
- [`RELEASE_NOTES_v2.0.0.md`](RELEASE_NOTES_v2.0.0.md)
- [`ARCHITECTURE.md`](ARCHITECTURE.md)
- [`API_V2.md`](API_V2.md)
- [`API_COMPATIBILITY.md`](API_COMPATIBILITY.md)
- [`SOCIAL_IDENTITY.md`](SOCIAL_IDENTITY.md)
- [`ADMINISTRATION.md`](ADMINISTRATION.md)
- [`STORAGE.md`](STORAGE.md)
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)

## License

MENKIESTESParty is part of the MENKIESTES software projects created by **CADERA**. Repository licensing and bundled third-party notices remain applicable.
