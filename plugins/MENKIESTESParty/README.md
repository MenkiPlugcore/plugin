# MENKIESTESParty v1.0.4

Native Paper plugin port of the MENKIESTES Party system.

## Documentation

- [Command Wiki](docs/COMMANDS.md) — player, Owner, Officer, admin, Party War, Season, and Reward Hall commands.
- [Changelog](CHANGELOG.md) — patch history and behavior changes.

## Requirements

- Paper 1.21.x (designed for Paper 1.21.11 / Java 21)
- PlaceholderAPI optional
- GriefPrevention optional
- Skript NOT required
- Database NOT required

## Current core features

- Party create / invite / accept / leave / disband
- Owner / Officer / Member roles
- GUI Invite Player and Manage Members
- Party Home with delayed teleport
- Party Chat
- Reputation + Party Level 1-5 + member slot scaling
- Weekly Mining / Hunter / Farmer quests
- Member contribution tracking
- Simple Party Relic Lv.1-5 from completed missions (3 / 9 / 18 / 30)
- Open-world Party War in configured `world`; no special War world and no forced teleport
- Kill points, Owner kill bonus, same-victim cooldown, combat-logout scoring
- OP exclusion from War scoring / PvP interference
- War tracker compass
- War reward chest tickets and `/party claimchest`
- Party Season points / wins / champion
- Reward Hall with contribution eligibility and future reward queue
- Reward Hall queue continues across month changes
- PlaceholderAPI expansion `%mparty_*%`
- Local YAML: `parties.yml`, `wars.yml`, `season.yml`, `hall.yml`

## Party roles

### Owner

Full Party management. Can invite, kick, promote, demote, set Party Home, and disband.

### Officer

Can invite players, set Party Home, and kick regular Members. Cannot kick Owner / another Officer and cannot promote or demote.

### Member

Normal Party member access.

## Member cap

| Level | Member cap |
|---:|---:|
| 1 | 5 |
| 2 | 10 |
| 3 | 12 |
| 4 | 15 |
| 5 | 20 |

## Important migration note

The plugin does not automatically parse old Skript `variables.csv`. Back up `plugins/Skript/` before removing the legacy Party scripts. Existing plugin YAML data under `plugins/MENKIESTESParty/` should be preserved when replacing JAR versions.

## Commands

See the complete command and permission reference:

**[docs/COMMANDS.md](docs/COMMANDS.md)**

Main commands:

- `/party`
- `/pchat <message>`
- `/partywar`
- `/partyseason`
- `/partyhall`

## Admin permission

```text
menkiestesparty.admin
```

Default: OP.

## Build from source

```bash
gradle clean build
```

Output JAR is generated under `build/libs/`.

This source is maintained in the `MenkiPlugcore/plugin` monorepo so future fixes, command changes, and version patches can be tracked through commits and changelog entries.
