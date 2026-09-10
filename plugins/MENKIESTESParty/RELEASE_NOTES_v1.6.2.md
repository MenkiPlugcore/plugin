# MENKIESTESParty v1.6.2 — Admin Recovery & Observability Update

Release date: 2026-09-10

## Added

- `/partyadmin pending` to inspect the calling staff member's staged dangerous action without redisplaying the confirmation token.
- `/partyadmin repair <party> dryrun` to preview deterministic repair operations without mutating Party data.
- `/partyadmin audit search <keyword> [page]` across actor, action, Party, result and detail fields.
- `/partyadmin audit filter <party|staff|action|result> <value> [page]` for scoped audit inspection.
- `/partyadmin snapshot verify <archive|export> <filename.yml>` with approved-directory/basename path enforcement.
- `/partyadmin recovery <party>` read-only live-state and snapshot recovery report.
- Enhanced `/partyadmin health` with storage queue/probe diagnostics and recent failed administration actions.
- Bukkit-free audit-query, snapshot-verification and deterministic-repair planning helpers with regression tests.

## Snapshot classifications

v1.6.2 distinguishes historical snapshots from actual corruption:

- `VERIFIED`: SHA-256 sidecar matches and the v1 snapshot metadata marker is valid.
- `LEGACY_UNVERIFIED`: an older snapshot has no checksum sidecar, or uses a legacy/unknown format. It is not automatically labelled corrupt.
- `CORRUPT`: checksum sidecar is malformed, references another filename, or SHA-256 does not match the snapshot.
- `METADATA_INVALID`: the YAML exists but required Party metadata is missing.
- `MISSING`: requested snapshot file does not exist.

Snapshot verification only accepts basename `.yml` files inside `archives/` or `admin-exports/`; traversal/nested paths are rejected.

## Recovery safety

Recovery in v1.6.2 is intentionally **read-only**. The command can locate and classify snapshots, report whether the live Party still exists, show deterministic repair availability and identify the newest usable candidate, but it never restores or overwrites Party data automatically.

`repair ... dryrun` also performs no Party mutation. It mirrors the conservative v1.6.0 repair rules and refuses to guess a replacement Owner when the stored Owner UUID is missing/invalid.

## Compatibility

- Public `MenkiPartyAPI.API_VERSION` remains **1.0**.
- Existing v1.6.1 Party, interaction, administration and storage data remains compatible.
- YAML remains the default local backend.
- SQLite/MySQL remain optional.
- No new required runtime dependency or external service is introduced.
- Java target remains Java 21 / Paper 1.21.11.
- Java 25 runtime compatibility and MySQL 8.4 integration remain production release gates.
- Full Folia support is still not claimed; experimental compatibility remains opt-in.

## Documentation

- `README.md` updated to v1.6.2.
- `CHANGELOG.md` updated with v1.6.2.
- `ADMINISTRATION.md` documents pending inspection, dry-run repair, audit queries, snapshot classifications, recovery reports and observability configuration.
- This file is required by the automatic GitHub Release job.

## Upgrade

Stop the server, replace the previous MENKIESTESParty JAR, keep the existing `plugins/MENKIESTESParty/` directory, and perform a full start. Existing `administration.yml` files remain valid; v1.6.2 observability settings use safe defaults when the new keys are absent.
