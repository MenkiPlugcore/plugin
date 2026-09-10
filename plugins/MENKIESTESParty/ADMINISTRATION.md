# MENKIESTESParty Administration & Moderation

Applies to **MENKIESTESParty v1.6.1**.

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
/partyadmin audit [page]
```

`inspect` shows Party key/display, owner, member count, level/XP, current Project, recruitment mode, applications, Contracts, freeze state and active storage backend.

`inspect ... verbose` additionally checks owner/player indexes, duplicate OWNER role fields, applications, Contracts, Diplomacy pairs, Project state and storage synchronization indicators. This diagnostic runs only when requested; it does not add a periodic heavy scan.

`health` reports Party count, frozen count, pending confirmations, audit count, archive-directory writability, storage/API health and a command-driven cross-index warning count.

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

v1.6.1 no longer reuses an existing filename. If the generated name already exists, a numeric suffix is added instead of overwriting it.

Each v1.6.1 snapshot is checked after writing for its Party key, format marker, owner and member count. A SHA-256 sidecar is then written next to it:

```text
PARADOX-20260910-143000-123-export.yml
PARADOX-20260910-143000-123-export.yml.sha256
```

The snapshot contains Party data, member indexes, related Contracts, applications, Diplomacy state and relevant moderation audit entries.

## Common administration

```text
/partyadmin forcejoin <player> <party>
/partyadmin remove <player>
/partyadmin rename <party> <displayName>
/partyadmin freeze <party> [reason]
/partyadmin unfreeze <party>
/partyadmin xp <party> <set|add|remove> <amount>
/partyadmin repair <party>
```

### Force join/remove

Staff can bypass normal recruitment flow, but normal Party slot limits still apply by default. A player who owns another Party cannot be moved until ownership is transferred or that Party is disbanded.

Roster mutation is refused while Party War is PREPARE/ACTIVE to protect scoring/session integrity. The Party owner cannot be force-removed; ownership must be transferred first.

### Rename

Rename changes only the public/display name. The stable internal Party key does **not** change. This avoids cascading migrations across Contracts, Diplomacy, War history and API consumers.

### Freeze

Freeze is reversible. By default it blocks invite/accept/leave/kick/promote/demote/disband roster changes, Contract mutations, Diplomacy mutations, recruitment changes, application accept/deny, and new applications/OPEN joins into the frozen Party.

The current recruitment mode is saved, recruitment becomes `CLOSED`, and the previous mode is restored on unfreeze. Project/Skill/Division management remains allowed by default unless changed in `administration.yml`.

### Repair

`repair` remains conservative. It repairs only deterministic owner/member/player-index problems. If the Party owner UUID is missing or invalid, the command stops instead of guessing a new owner.

v1.6.1 adds a post-repair integrity pass and reports the warning count before and after the repair, including residual warnings that still need manual review.

## Dangerous actions — v1.6.1 token flow

These commands are staged behind the v1.6.1 confirmation gate:

```text
/partyadmin transfer <party> <player>
/partyadmin resetproject <party>
/partyadmin resetcontract <id>
/partyadmin disband <party>
```

The command returns a six-character token, for example:

```text
Confirmation token: 7KH3QW
```

Confirm with the exact token:

```text
/partyadmin confirm 7KH3QW
```

or cancel it:

```text
/partyadmin cancel
```

The default confirmation window is 30 seconds. A ticket belongs to the staff actor that created it and is consumed exactly once. Replays cannot execute the mutation twice. Missing/wrong/expired tokens do not execute the action. Three wrong attempts cancel the pending action by default. Pending player confirmations are cleared when that staff member disconnects.

Only one v1.6.1 dangerous ticket is kept per staff actor. Staging a new dangerous action replaces the previous ticket and records the replacement in the audit log.

### Transfer Owner

The target is validated before the ticket is staged and must already be a member of the Party. The previous Owner becomes Officer. Transfer is refused during Party War roster lock. After execution, v1.6.1 verifies that the target UUID is actually the stored Owner.

### Reset Project

The Party must have an active Project before the ticket is staged. After execution, v1.6.1 verifies the active Project progress is zero.

### Reset Contract

Only an `ACTIVE` Contract can be staged for reset. Progress becomes `0` and contributor counters are cleared. Terminal Contract history is never reopened automatically. A post-condition verifies the Contract is still ACTIVE with zero progress.

### Disband

Before the existing v1.6.0 archive-first disband implementation runs, v1.6.1 creates an **independent verified safety snapshot plus SHA-256 sidecar**. If this safety snapshot cannot be written and verified, disband is aborted before Party data is removed.

The final post-condition requires the Party key to no longer exist. Related active/pending Contracts are closed by the v1.6.0 disband implementation and Party indexes/interactions are cleaned as before.

## Audit log

Administration audit entries live at:

```text
interactions.yml -> moderation.audit
```

v1.6.1 additionally records `result` for its safety events. Important outcomes include:

```text
PENDING
SUCCESS
FAILED
PARTIAL
CANCELLED
```

Wrong-token attempts, expiry, replacement, logout cleanup, archive verification failure and dangerous-action post-condition failure are therefore visible instead of silently disappearing.

Retention and maximum entries remain controlled by `administration.yml`.

## Permissions

| Permission | Purpose |
| --- | --- |
| `menkiestesparty.admin` | Full legacy/root admin; bypasses granular checks |
| `menkiestesparty.admin.inspect` | List, search, health, inspect, verbose inspect and export |
| `menkiestesparty.admin.modify` | Common/reversible administration and archive |
| `menkiestesparty.admin.dangerous` | Stage/token-confirm owner/reset/disband actions |
| `menkiestesparty.admin.audit` | Read staff audit history |
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
```

The v1.6.1 safety layer is command/GUI driven. It does not add a new per-tick administration scan, external database requirement or public API breaking change.
