# MENKIESTESParty v1.6.1

Modular Paper Party/Guild framework by **CADERA** for Paper 1.21.11 / Java 21.

MENKIESTESParty is designed as a standalone public plugin. A basic server can keep the lightweight local YAML setup, while larger installations can optionally use SQLite/MySQL and developer integrations.

## Core systems

- Party create/invite/roster/roles/home/chat
- Party Level and shared progression
- Weekly Quest + Party Relic
- Daily Party Mission
- Party Projects
- Party Skill Tree
- Party Divisions
- Dynamic Party Identity
- Party Contracts
- Diplomacy + Trust
- Recruitment + Join Applications
- Interaction GUI + Notification Inbox + Recent Activity
- Party War + Season
- Administration & Moderation tooling
- Public developer API v1.0

## Storage

Available backends:

```text
YAML    - default, local, simplest
SQLite  - optional local SQL database
MySQL   - optional external SQL database
```

SQL is **not required**. YAML remains a first-class supported backend.

See [`STORAGE.md`](STORAGE.md).

## Administration — v1.6.1

```text
/partyadmin
/party admin ...
```

v1.6.0 introduced the Admin Browser/Inspect GUI, force join/remove, owner transfer, display rename, reversible Party freeze, XP controls, conservative repair, Project/Contract reset, export/archive, archive-first disband and bounded staff audit history.

v1.6.1 hardens that layer with per-staff one-time confirmation tokens, exactly-once dangerous-action consumption, failed-action auditing, verified SHA-256 admin snapshots, collision-safe exports, `/partyadmin health`, Party search, verbose integrity inspection and post-repair verification.

See [`ADMINISTRATION.md`](ADMINISTRATION.md).

## Developer API

The public API is registered through Bukkit `ServicesManager` and remains:

```text
MenkiPartyAPI.API_VERSION = 1.0
```

The API exposes immutable snapshots rather than Bukkit YAML internals, allowing storage internals to evolve without forcing consumer plugins to depend on the persistence layer.

See:

- [`API.md`](API.md)
- [`API_COMPATIBILITY.md`](API_COMPATIBILITY.md)

## Documentation

Every MENKIESTESParty version must be documented in GitHub as part of the release process.

Release documentation currently includes:

- [`CHANGELOG.md`](CHANGELOG.md) — chronological version history
- [`RELEASE_NOTES_v1.6.1.md`](RELEASE_NOTES_v1.6.1.md) — current release notes
- [`ADMINISTRATION.md`](ADMINISTRATION.md) — admin/moderation wiki
- [`STORAGE.md`](STORAGE.md) — storage/migration guide
- [`API.md`](API.md) — public API guide
- [`API_COMPATIBILITY.md`](API_COMPATIBILITY.md) — API compatibility policy
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) — bundled dependency notices

## Production defaults

- Storage: YAML
- SQL: optional
- Administration: enabled for permitted staff only
- Dangerous admin actions: token confirmation required
- Admin snapshots: content-verified + SHA-256 sidecar
- Contract automatic Party XP: 0 unless configured
- Folia: experimental only; not production-certified in v1.6.1
- Public API: v1.0

## Build

```bash
gradle clean build
```

The GitHub Actions release gate verifies Java 21 compilation/tests, the production JAR contents, MySQL integration, Java 25 runtime compatibility, release documentation, and then publishes the verified JAR plus SHA-256 to GitHub Releases.

## License

MENKIESTESParty is part of the MENKIESTES software projects created by **CADERA**. See the repository license and third-party notices for applicable terms.
