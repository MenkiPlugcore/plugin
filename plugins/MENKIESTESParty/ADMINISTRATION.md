# MENKIESTESParty Administration & Moderation

Applies to **MENKIESTESParty v1.6.0**.

The administration layer is designed for live-server support and moderation. It does not replace normal Party role rules and it is not exposed through the public `MenkiPartyAPI v1.0`.

## Entry points

```text
/partyadmin
/padmin
/mpartyadmin
/party admin ...
```

`/partyadmin` opens the Admin Browser for players with GUI permission. Console can use all text commands.

## Read-only tools

```text
/partyadmin list [page]
/partyadmin inspect <party>
/partyadmin export <party>
/partyadmin audit [page]
```

`inspect` shows Party key/display, owner, member count, level/XP, current Project, recruitment mode, applications, Contracts, freeze state and active storage backend.

`export` creates a YAML support snapshot under:

```text
plugins/MENKIESTESParty/admin-exports/
```

The export contains Party data, member indexes, related Contracts, applications, diplomacy state and relevant moderation audit entries.

## Common administration

```text
/partyadmin forcejoin <player> <party>
/partyadmin remove <player>
/partyadmin rename <party> <displayName>
/partyadmin freeze <party> [reason]
/partyadmin unfreeze <party>
/partyadmin xp <party> <set|add|remove> <amount>
/partyadmin repair <party>
/partyadmin archive <party>
```

### Force join/remove

Staff can bypass normal recruitment flow, but normal Party slot limits still apply by default. A player who owns another Party cannot be moved until ownership is transferred or that Party is disbanded.

Roster mutation is refused while Party War is PREPARE/ACTIVE to protect scoring/session integrity.

### Rename

Rename changes only the public/display name. The stable internal Party key does **not** change. This avoids cascading migrations across Contracts, Diplomacy, War history and API consumers.

### Freeze

Freeze is reversible. By default it blocks:

- invite/accept/leave/kick/promote/demote/disband roster changes;
- Contract mutations;
- Diplomacy mutations;
- recruitment changes;
- application accept/deny;
- new applications/OPEN joins into the frozen Party.

The current recruitment mode is saved, recruitment becomes `CLOSED`, and the previous mode is restored on unfreeze.

Project/Skill/Division management is not blocked by default. It can be enabled in `administration.yml`.

### Repair

`repair` is deliberately conservative. It repairs only deterministic owner/member/player-index problems. If the Party owner UUID is missing or invalid, the command stops instead of guessing a new owner.

## Dangerous actions

These commands are staged and require a second confirmation:

```text
/partyadmin transfer <party> <player>
/partyadmin resetproject <party>
/partyadmin resetcontract <id>
/partyadmin disband <party>
```

After issuing one of them:

```text
/partyadmin confirm
```

or cancel it:

```text
/partyadmin cancel
```

The default confirmation window is 30 seconds.

### Transfer owner

The target must already be a member of the Party. The previous Owner becomes Officer. Transfer is refused during Party War roster lock.

### Reset Project

Only resets the currently active Project's progress and member Project-contribution counters. It does not delete Project definitions or completed history.

### Reset Contract

Only an `ACTIVE` Contract can be reset safely. Progress becomes `0` and contributor counters are cleared. Terminal Contract history is never reopened automatically.

### Disband

Admin disband is archive-first. Before deleting live Party data, MENKIESTESParty creates a YAML snapshot under:

```text
plugins/MENKIESTESParty/archives/
```

If archive creation fails, disband is aborted. Related active/pending Contracts are closed as `CANCELLED`, diplomacy/application state is cleaned, member indexes are removed, and the staff action is audited.

## Audit log

Every administration mutation writes a bounded entry to:

```text
interactions.yml -> moderation.audit
```

Entries include timestamp, staff actor, action, Party key and detail. Retention and maximum entries are controlled by `administration.yml`.

## Permissions

| Permission | Purpose |
| --- | --- |
| `menkiestesparty.admin` | Full legacy/root admin; bypasses granular checks |
| `menkiestesparty.admin.inspect` | List, inspect and export |
| `menkiestesparty.admin.modify` | Common/reversible administration |
| `menkiestesparty.admin.dangerous` | Stage/confirm owner/reset/disband actions |
| `menkiestesparty.admin.audit` | Read staff audit history |
| `menkiestesparty.admin.gui` | Open Admin Browser/Inspect GUI |

All granular permissions default to OP.

## Configuration

`plugins/MENKIESTESParty/administration.yml`:

```yaml
administration:
  enabled: true
  confirmation-seconds: 30

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
```

The module is command/GUI driven; it does not add a new per-tick scan or database requirement.
