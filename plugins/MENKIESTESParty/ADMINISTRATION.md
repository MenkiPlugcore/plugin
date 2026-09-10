# MENKIESTESParty Administration & Moderation

Applies to **MENKIESTESParty v1.6.2**.

The administration layer is designed for live-server support and moderation. It does not replace normal Party role rules and it is not exposed through the public `MenkiPartyAPI v1.0`.

## Entry points

```text
/partyadmin
/padmin
/mpartyadmin
/party admin ...
```

`/partyadmin` opens the Admin Browser for players with GUI permission. Console can use the text commands.

## Read-only and diagnostic tools

```text
/partyadmin list [page]
/partyadmin search <query> [page]
/partyadmin inspect <party>
/partyadmin inspect <party> verbose
/partyadmin health
/partyadmin pending
/partyadmin recovery <party>
```

`inspect` shows Party key/display, owner, member count, level/XP, current Project, recruitment mode, applications, Contracts, freeze state and active storage backend.

`inspect ... verbose` additionally checks owner/player indexes, duplicate OWNER role fields, applications, Contracts, Diplomacy pairs, Project state and storage synchronization indicators. The diagnostic runs only when requested.

`health` in v1.6.2 additionally reports pending storage writes/age, failed storage writes, last storage health probe, recent failed administration actions, archive/export directory writability and a command-driven cross-index warning count.

### Pending dangerous action

```text
/partyadmin pending
```

Shows the calling staff member's most recent staged dangerous action as reconstructed from the durable moderation audit trail: action, target, request time, expiry state, remaining time and known wrong-token attempts. The global in-memory pending count is also shown.

The command intentionally **does not redisplay the confirmation token**. The original token remains visible only when the action is staged. If the audit trail indicates a terminal event such as cancellation, expiry, replay, logout cleanup or execution, the report does not present that action as pending.

## Party search

```text
/partyadmin search <query> [page]
```

Searches stable Party keys and display names. This is command-driven and does not add a background index scanner.

## Repair and dry-run

Normal deterministic repair remains:

```text
/partyadmin repair <party>
```

v1.6.2 adds a no-write preview:

```text
/partyadmin repair <party> dryrun
```

Dry-run mirrors the conservative repair rules and reports planned deterministic changes such as restoring the stored Owner to the member roster, correcting Owner/member player indexes, fixing Owner role fields, demoting extra `OWNER` role fields to `OFFICER`, and removing invalid/orphan player indexes.

If the stored Owner UUID is missing/invalid, the plan is marked unsafe and **does not guess a replacement Owner**. Dry-run does not modify Party data and does not apply the plan automatically.

## Verified export and archive

```text
/partyadmin export <party>
/partyadmin archive <party>
```

Exports are written under:

```text
plugins/MENKIESTESParty/admin-exports/
plugins/MENKIESTESParty/archives/
```

v1.6.1+ snapshots use collision-safe filenames. Each current-format snapshot is checked after writing for its Party key, format marker, owner and member count, then receives a SHA-256 sidecar:

```text
PARADOX-20260910-143000-123-export.yml
PARADOX-20260910-143000-123-export.yml.sha256
```

The snapshot contains Party data, member indexes, related Contracts, applications, Diplomacy state and relevant moderation audit entries.

## Snapshot verification — v1.6.2

```text
/partyadmin snapshot verify <archive|export> <filename.yml>
```

Only basename `.yml` files directly inside the approved `archives/` or `admin-exports/` directory are accepted. Directory traversal, nested paths and unsupported buckets are rejected before filesystem access.

Verification statuses:

| Status | Meaning |
| --- | --- |
| `VERIFIED` | SHA-256 sidecar matches and the current `MENKIESTESParty-admin-snapshot-v1` marker is present |
| `LEGACY_UNVERIFIED` | Snapshot exists but has no sidecar, or uses an older/unknown format; this is not automatically corruption |
| `CORRUPT` | Sidecar is malformed, references another filename, or SHA-256 mismatches |
| `METADATA_INVALID` | YAML exists but required Party metadata such as `meta.party-key` is missing |
| `MISSING` | Requested snapshot file does not exist |

`LEGACY_UNVERIFIED` exists for backward compatibility with v1.6.0-era snapshots that predate mandatory sidecars.

## Read-only recovery report — v1.6.2

```text
/partyadmin recovery <party>
```

The command scans recent `.yml` files in `archives/` and `admin-exports/` only when requested. It can resolve a live Party or, when the Party has already been deleted, match snapshot metadata by stable Party key/display name.

The report includes:

- whether the live Party still exists;
- whether deterministic live repair is available;
- counts of verified, legacy-unverified and corrupt/invalid snapshots;
- the newest usable snapshot candidate;
- candidate purpose, timestamp and SHA-256 when available.

Recovery is intentionally **read-only in v1.6.2**. It never restores, merges or overwrites live Party data. Automatic restore is deferred until snapshot schema/version migration has a stronger formal contract.

## Audit log

Administration audit entries live at:

```text
interactions.yml -> moderation.audit
```

Standard pagination remains:

```text
/partyadmin audit [page]
```

v1.6.2 adds:

```text
/partyadmin audit search <keyword> [page]
/partyadmin audit filter <party|staff|action|result> <value> [page]
```

`search` checks actor, action, Party, result and detail. `filter` restricts matching to the selected field. Entries remain newest-first and bounded by the normal retention/max-entry settings.

Important v1.6.1+ result states include `PENDING`, `SUCCESS`, `FAILED`, `PARTIAL` and `CANCELLED`. Wrong-token attempts, expiry, replacement, logout cleanup, archive verification failure and dangerous-action post-condition failure are therefore queryable instead of silently disappearing.

## Common administration

```text
/partyadmin forcejoin <player> <party>
/partyadmin remove <player>
/partyadmin rename <party> <displayName>
/partyadmin freeze <party> [reason]
/partyadmin unfreeze <party>
/partyadmin xp <party> <set|add|remove> <amount>
```

Staff can bypass normal recruitment flow, but normal Party slot limits still apply by default. A player who owns another Party cannot be moved until ownership is transferred or that Party is disbanded. Roster mutation is refused while Party War is PREPARE/ACTIVE, and the Party owner cannot be force-removed.

Rename changes only the public/display name. The stable internal Party key does **not** change, avoiding cascading migrations across Contracts, Diplomacy, War history and API consumers.

Freeze is reversible. By default it blocks roster changes, Contract/Diplomacy mutations, recruitment changes, application accept/deny, and new applications/OPEN joins into the frozen Party. The previous recruitment mode is restored on unfreeze. Project/Skill/Division management remains allowed by default unless changed in `administration.yml`.

## Dangerous actions — token flow

These commands remain staged behind the v1.6.1 exactly-once confirmation gate:

```text
/partyadmin transfer <party> <player>
/partyadmin resetproject <party>
/partyadmin resetcontract <id>
/partyadmin disband <party>
```

The command returns a six-character token. Confirm with:

```text
/partyadmin confirm <token>
```

or cancel with:

```text
/partyadmin cancel
```

Default confirmation lifetime is 30 seconds. The ticket belongs to the staff actor, is consumed exactly once, and three wrong attempts cancel it by default. Pending player confirmations are cleared when that staff member disconnects.

Transfer requires the target to already be a Party member. Reset Project requires an active Project. Reset Contract only accepts `ACTIVE` status. Disband is refused while Party War roster locking is active and creates an independent verified safety snapshot before the existing archive-first deletion path executes.

## Permissions

| Permission | Purpose |
| --- | --- |
| `menkiestesparty.admin` | Full legacy/root admin; bypasses granular checks |
| `menkiestesparty.admin.inspect` | List/search/health/inspect, snapshot verification and read-only recovery |
| `menkiestesparty.admin.modify` | Common/reversible administration, archive, repair and repair dry-run |
| `menkiestesparty.admin.dangerous` | Stage/token-confirm high-impact actions and inspect own pending state |
| `menkiestesparty.admin.audit` | Read/search/filter staff audit history |
| `menkiestesparty.admin.gui` | Open Admin Browser/Inspect GUI |

All granular permissions default to OP.

## Configuration

`plugins/MENKIESTESParty/administration.yml`:

```yaml
administration:
  enabled: true
  confirmation-seconds: 30

  confirmation:
    require-token: true
    max-attempts: 3

  audit:
    max-entries: 500
    retention-days: 90

  freeze:
    block-roster: true
    block-interactions: true
    block-progression-management: false
    force-recruitment-closed: true

  forcejoin:
    allow-over-cap: false

  snapshots:
    format: MENKIESTESParty-admin-snapshot-v1
    checksum: SHA-256

  observability:
    enabled: true
    audit-page-size: 10
    failed-action-window-hours: 24
    snapshot-scan-limit: 200
    repair-dryrun-max-lines: 20
```

Existing v1.6.1 `administration.yml` files remain valid. Missing v1.6.2 observability keys use the defaults shown above.

The recovery/observability layer is command-driven. It does not add a per-tick administration scan, new external database requirement, automatic restore path or public API breaking change.
