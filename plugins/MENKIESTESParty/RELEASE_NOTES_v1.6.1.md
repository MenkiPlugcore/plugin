# MENKIESTESParty v1.6.1 — Administration Stability & Safety Patch

Release date: 2026-09-10

## Added

- Six-character per-staff confirmation tokens for dangerous administration actions.
- Exactly-once confirmation consumption with replay/concurrency protection.
- Configurable wrong-token attempt cap; three failures cancel a ticket by default.
- Pending confirmation cleanup when staff disconnects.
- Failed/cancelled/expired safety outcomes in the bounded moderation audit log.
- `/partyadmin health` administration health summary.
- `/partyadmin search <query> [page]` Party search.
- `/partyadmin inspect <party> verbose` integrity and cross-index diagnostics.
- Verified post-repair integrity report.
- Collision-safe export/archive filenames.
- Post-write YAML snapshot verification.
- SHA-256 sidecar for v1.6.1 admin exports/archives.
- Independent verified pre-disband safety snapshot.
- Regression tests for confirmation exactly-once behavior, token errors/expiry, archive collision avoidance, checksum tamper detection and release/API metadata.

## Safety

- Dangerous v1.6.1 tickets are bound to the staff actor and target/action metadata.
- Replaying the same token cannot intentionally execute a mutation twice.
- Transfer Owner is prevalidated so the target must already be a Party member.
- Reset Project requires an active Project before staging.
- Reset Contract requires ACTIVE status before staging.
- Disband remains blocked while Party War roster locking is active.
- Disband will not enter the v1.6.0 deletion path unless a separate v1.6.1 safety snapshot has already been written, content-verified and checksummed.
- Dangerous actions are post-condition checked after the existing v1.6.0 mutation implementation executes.
- Existing freeze guards, Party slot limits and owner-removal protections remain intact.

## Compatibility

- Public `MenkiPartyAPI.API_VERSION` remains **1.0**.
- Existing v1.6.0 Party, interaction, administration and storage data remains compatible.
- YAML remains the default local backend.
- SQLite/MySQL remain optional.
- No new required runtime dependency or external service is introduced.
- Java target remains Java 21 / Paper 1.21.11.
- Java 25 remains covered by the release runtime probe.
- MySQL 8.4 integration remains part of the production gate even though SQL is optional.

## Documentation

- `ADMINISTRATION.md` updated for token confirmation, health/search/verbose diagnostics, verified snapshots and audit outcomes.
- `CHANGELOG.md` updated with v1.6.1.
- `RELEASE_NOTES_v1.6.1.md` is required by the automatic GitHub Release job.

## Upgrade

Stop the server, replace the previous MENKIESTESParty JAR, keep the existing `plugins/MENKIESTESParty/` directory, and perform a full start. Existing `administration.yml` files remain valid; missing v1.6.1 confirmation keys use the safe defaults (`require-token: true`, `max-attempts: 3`).
