# MENKIESTESParty v1.6.0 — Administration & Moderation Update

Release date: 2026-09-10

## Added

- `/partyadmin` Admin Browser and Party Inspect GUI.
- Text/console Party list and inspect tools.
- Staff force join and force remove with Party War roster safety.
- Safe owner transfer with confirmation.
- Display-only Party rename; the internal Party key stays stable.
- Reversible Party freeze/unfreeze with recruitment-mode restoration.
- Party XP/Rep set, add and remove controls.
- Conservative owner/member/player-index repair.
- Active Project progress reset.
- Active Contract progress reset.
- Party support export and archive snapshots.
- Archive-first admin disband.
- Bounded staff audit log in `interactions.yml`.
- Granular admin permissions.
- Dedicated `administration.yml` configuration.
- Nested `/party admin ...` command route.

## Safety

- Dangerous operations use a short two-step `/partyadmin confirm` flow.
- Roster-changing staff operations refuse to run while Party War is PREPARE/ACTIVE.
- Admin disband is aborted when its pre-disband archive cannot be written.
- Freeze is enforced in the Party core for invite acceptance and roster mutations, not only at the command layer.
- Stale Interaction GUI mutation buttons are blocked when their Party becomes frozen.
- `repair` never guesses a replacement Owner when the stored Owner UUID is invalid.
- Terminal Contract history is not reopened by `resetcontract`.

## Compatibility

- Public `MenkiPartyAPI.API_VERSION` remains **1.0**.
- Existing YAML/SQLite/MySQL storage remains compatible.
- YAML remains the default backend; SQL remains optional.
- No new required dependency/database is introduced.
- Java target remains Java 21 / Paper 1.21.11.
- Existing Party keys are unchanged by admin rename.

## Documentation

- `ADMINISTRATION.md` — complete command/permission/safety guide.
- `CHANGELOG.md` — release history, including backfilled v1.3.2–v1.5.1 entries.
- `README.md` — refreshed current-version overview and documentation index.
