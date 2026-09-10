# MENKIESTESParty v1.2.1

Native Paper Party/Guild framework by CADERA. Designed for Paper 1.21.11 / Java 21 with local YAML storage and no required database.

## v1.2.1 — Progression GUI Update

v1.2.1 adds an inventory-based interface for the v1.2 progression system while keeping every command available.

Open `/party` and use **Party Progression** to access:

- Party Profile
- Party Projects
- Party Skill Tree
- Party Divisions
- Dynamic Party Identity

Project start/cancel, Skill unlock, self-select Division, and Owner/Officer Division management can now be done from GUI.

The obsolete Party Reward Hall button has been removed from the main Party GUI and replaced with the Progression Hub.

The GUI is optional:

```yaml
gui:
  progression:
    enabled: true
```

If disabled, all progression commands continue working normally.

## Party Projects

One shared long-term objective can be active per Party. Default projects:

| ID | Type | Goal | Party XP |
| --- | --- | ---: | ---: |
| `mining_expedition` | Mining | 2500 | 300 |
| `monster_hunt` | Hunter | 500 | 350 |
| `harvest_drive` | Farmer | 1500 | 250 |

Owner/Officer can start or cancel projects. Member contributions are shared and saved in `parties.yml`.

Commands:

- `/party project`
- `/party project start <id>`
- `/party project cancel`
- `/partyproject` / `/pproject`

## Party Skill Tree

Party Level automatically provides Skill Points: 1 point for every level after Level 1 by default. Only the Owner spends Party Skill Points.

Default branches:

- COMBAT — improves Hunter Project progress.
- LABOR — improves Mining/Farming Project progress.
- COMMAND — improves Party XP rewarded by completed Projects.

Commands:

- `/party skill`
- `/party skill unlock <node>`
- `/partyskill` / `/pskill`

## Party Divisions

Divisions do not replace Owner/Officer/Member roles. They are an additional specialization layer.

Default divisions:

- Combat Division — Hunter Project contribution bonus.
- Resource Division — Mining/Farming Project contribution bonus.
- Support Division — small bonus to all Project contribution.

Commands:

- `/party division`
- `/party division join <id|none>`
- `/party division set <player> <id|none>` — Owner/Officer
- `/partydivision` / `/pdivision`

## Dynamic Party Identity

Identity is not selected manually. It is calculated from the Party's recent activity using a rolling 30-day window by default.

Available identities:

- Developing
- Warlike
- Industrial
- Agrarian
- Project Focused
- Balanced

Commands:

- `/party identity`
- `/partyidentity` / `/pidentity`

## Party Profile

`/party profile` or `/partyprofile` shows Party Level, XP, member count, Identity, Skill Points, member Division, and active Project. The same information is available from the Progression GUI.

## Modular configuration

Each progression feature can be disabled independently:

```yaml
modules:
  projects: true
  skill-tree: true
  divisions: true
  identity: true
```

Core Party, Weekly Quest, Daily Mission, Party Relic, Party War and Season remain compatible with v1.1.0/v1.2.0 data.

## PlaceholderAPI

Existing `%mparty_*%` placeholders remain. v1.2 adds:

- `%mparty_identity%`
- `%mparty_division%`
- `%mparty_skill_points%`
- `%mparty_project%`
- `%mparty_project_progress%`

## Upgrade from v1.2.0

Replace the JAR and restart the server. Do not delete `plugins/MENKIESTESParty/`. Existing `parties.yml`, `wars.yml`, and `season.yml` remain valid. Missing v1.2.1 GUI defaults are merged into the existing `config.yml` on first startup.

## Requirements

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional
- GriefPrevention optional
- No Skript required
- No database required

## License

MENKIESTES SOFTWARE LICENSE v1.0 — MENKIESTES dibuat oleh CADERA. See repository `LICENSE`.
