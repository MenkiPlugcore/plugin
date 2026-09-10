# MENKIESTESParty v1.7.0

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
- Social Party Profiles + privacy
- Party Achievements + active badges
- Social Party leaderboards
- Party Contracts
- Diplomacy + Trust
- Recruitment + Join Applications
- Interaction GUI + Notification Inbox + Recent Activity
- Party War + Season
- Administration, Recovery & Observability tooling
- Public developer API v1.0

## Social & Party Identity — v1.7.0

Dynamic Party Identity remains automatic and activity-derived. v1.7.0 adds a separate player-facing Social Identity layer around it:

```text
/party profile [party|player]
/party social ...
/party badges
/party top [reputation|level|members|projects|activity|age] [page]
```

Parties can configure a plain-text description, short unique tag, allowlisted color/icon, public/private visibility, and an active badge selected from earned achievements. Member profiles reuse existing joined-at data and report lightweight online/activity status.

The social layer is intentionally lightweight: no new per-tick social scheduler, no new database/table, and no duplicate activity tracker. Social data lives under the existing `parties.<party>.social.*` record and `social.yml` is configuration only.

See [`SOCIAL_IDENTITY.md`](SOCIAL_IDENTITY.md).

## Storage

Available backends:

```text
YAML    - default, local, simplest
SQLite  - optional local SQL database
MySQL   - optional external SQL database
```

SQL is **not required**. YAML remains a first-class supported backend. v1.7.0 does not add a storage migration.

See [`STORAGE.md`](STORAGE.md).

## Administration

```text
/partyadmin
/party admin ...
```

v1.6.0 introduced the Admin Browser/Inspect GUI and live moderation controls. v1.6.1 hardened high-impact actions with exactly-once token confirmation, failed-action auditing and verified SHA-256 snapshots. v1.6.2 added command-driven Admin Recovery & Observability, repair dry-run, audit search/filter, snapshot verification and read-only recovery reports.

Those safety contracts remain intact in v1.7.0.

See [`ADMINISTRATION.md`](ADMINISTRATION.md).

## Developer API

The public API is registered through Bukkit `ServicesManager` and remains:

```text
MenkiPartyAPI.API_VERSION = 1.0
```

v1.7.0 deliberately keeps the Java API contract at v1.0. Social profile data is exposed to server UIs through new PlaceholderAPI values without breaking existing Java API consumers.

See:

- [`API.md`](API.md)
- [`API_COMPATIBILITY.md`](API_COMPATIBILITY.md)

## PlaceholderAPI — social additions

```text
%mparty_social_tag%
%mparty_social_description%
%mparty_social_color%
%mparty_social_icon%
%mparty_social_visibility%
%mparty_social_badge%
%mparty_social_badge_id%
%mparty_social_profile_name%
%mparty_social_achievements%
%mparty_social_activity%
%mparty_member_since%
%mparty_member_status%
```

Existing `%mparty_identity%` / `%mparty_identity_raw%` continue to represent the automatic Dynamic Party Identity.

## Documentation

Every MENKIESTESParty version must be documented in GitHub as part of the release process.

Release documentation currently includes:

- [`CHANGELOG.md`](CHANGELOG.md) — chronological version history
- [`RELEASE_NOTES_v1.7.0.md`](RELEASE_NOTES_v1.7.0.md) — current release notes
- [`SOCIAL_IDENTITY.md`](SOCIAL_IDENTITY.md) — social profile/achievement/leaderboard wiki
- [`ADMINISTRATION.md`](ADMINISTRATION.md) — admin/moderation/recovery wiki
- [`STORAGE.md`](STORAGE.md) — storage/migration guide
- [`API.md`](API.md) — public API guide
- [`API_COMPATIBILITY.md`](API_COMPATIBILITY.md) — API compatibility policy
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) — bundled dependency notices

## Production defaults

- Storage: YAML
- SQL: optional
- Social profiles: enabled
- Social profile visibility: PUBLIC
- Private Parties in social leaderboard: excluded
- Party tags: unique, 2-5 uppercase alphanumeric characters
- Social achievements: configuration-driven and permanent after unlock
- Dynamic Identity: automatic; not manually selectable
- Administration: enabled for permitted staff only
- Dangerous admin actions: exactly-once token confirmation required
- Admin recovery: read-only; no automatic restore
- Folia: experimental only; not production-certified in v1.7.0
- Public API: v1.0
- Web panel: deferred until after v2.0

## Build

```bash
gradle clean build
```

The GitHub Actions release gate verifies Java 21 compilation/tests, social/admin/API/storage contracts, the production JAR contents, MySQL integration, Java 25 runtime compatibility, release documentation, and then publishes the verified JAR plus SHA-256 to GitHub Releases.

## License

MENKIESTESParty is part of the MENKIESTES software projects created by **CADERA**. See the repository license and third-party notices for applicable terms.
