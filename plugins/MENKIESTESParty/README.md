# MENKIESTESParty v1.2.2

Native Paper Party/Guild framework by CADERA. Designed for Paper 1.21.11 / Java 21 with local YAML storage and no required database.

## v1.2.2 — GUI & Safety Polish

v1.2.2 improves the v1.2 progression interface for public/server-wide use without changing existing Party data.

### Pagination

Progression menus now support large custom configurations instead of showing only the first inventory page:

- Party Projects
- Party Skill Tree nodes
- Party Divisions
- Division member manager
- Division assignment menu

Project, Skill and Division definitions can therefore grow beyond one GUI page while remaining configurable through `config.yml`.

### Confirmation screens

Risky GUI actions now have optional confirmation:

- Cancel active Party Project
- Unlock Party Skill / spend Skill Point

```yaml
gui:
  progression:
    confirmations:
      project-cancel: true
      skill-unlock: true
```

Commands are unchanged; these confirmations only protect inventory clicks.

### Visual progression

The GUI now shows progress bars for:

- Party Level progress
- Active Party Project progress
- Dynamic Identity activity share

Progress-bar width is configurable:

```yaml
gui:
  progression:
    progress-bar-width: 20
```

Effective range is 5-40 characters.

Party Profile's Level item is now clickable and opens a Level Progression screen showing Level 1-5 requirements and member-slot limits.

### Granular GUI permissions

Role rules remain authoritative. Permissions are an additional layer that server owners can control with a permission plugin such as LuckPerms.

- `menkiestesparty.gui.progression` — open Progression Hub
- `menkiestesparty.gui.projects` — view Projects GUI
- `menkiestesparty.gui.projects.manage` — start/cancel Projects from GUI; Owner/Officer rule still applies
- `menkiestesparty.gui.skills` — view Skill Tree GUI
- `menkiestesparty.gui.skills.unlock` — unlock Skills from GUI; Owner rule still applies
- `menkiestesparty.gui.divisions` — view Divisions GUI
- `menkiestesparty.gui.divisions.self` — self-select Division when enabled in config
- `menkiestesparty.gui.divisions.manage` — assign member Divisions; Owner/Officer rule still applies
- `menkiestesparty.gui.identity` — view Dynamic Identity GUI

These permissions default to `true` for backwards compatibility and can be explicitly denied per player/group. `menkiestesparty.admin` bypasses progression GUI permission checks.

## Progression Hub

Open `/party` and use **Party Progression** to access:

- Party Profile
- Party Projects
- Party Skill Tree
- Party Divisions
- Dynamic Party Identity

The GUI remains optional:

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

Core Party, Weekly Quest, Daily Mission, Party Relic, Party War and Season remain compatible with earlier v1.2 data.

## PlaceholderAPI

Existing `%mparty_*%` placeholders remain. Progression placeholders include:

- `%mparty_identity%`
- `%mparty_division%`
- `%mparty_skill_points%`
- `%mparty_project%`
- `%mparty_project_progress%`

## Upgrade from v1.2.1

Replace the JAR and restart the server. Do not delete `plugins/MENKIESTESParty/`. Existing `parties.yml`, `wars.yml`, and `season.yml` remain valid. Missing v1.2.2 GUI defaults are merged into the existing `config.yml` on first startup.

## Requirements

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional
- GriefPrevention optional
- No Skript required
- No database required

## License

MENKIESTES SOFTWARE LICENSE v1.0 — MENKIESTES dibuat oleh CADERA. See repository `LICENSE`.
