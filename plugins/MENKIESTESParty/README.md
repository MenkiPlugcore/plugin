# MENKIESTESParty v1.0.0

Native Paper plugin port of the MENKIESTES Party Skript system.

## Requirements
- Paper 1.21.x (designed for 1.21.11 / Java 21)
- PlaceholderAPI optional
- GriefPrevention optional
- Skript NOT required
- Database NOT required

## Included in v1.0.0
- Party create/invite/accept/leave/disband
- Owner / Officer / Member roles
- Kick / promote / demote
- Party Home with delayed teleport
- Party Chat
- Party GUI
- Reputation + Party Level 1-5 + slot scaling
- Weekly Mining / Hunter / Farmer quests
- Anti player-placed ore counting (runtime journal)
- Member contribution board
- Simple Party Relic Lv.1-5 from total completed quests (3/9/18/30)
- Open-world Party War in configured `world`; no special war world and no forced teleport
- Kill points, Owner kill bonus, same-victim cooldown, combat-logout scoring
- OP exclusion from War scoring/PvP interference
- War tracker compass
- War reward chest tickets and `/party claimchest`
- Party Season points/wins/champion
- Reward Hall basic weekly Main Artifact flow
- PlaceholderAPI expansion `%mparty_*%`
- Local YAML: `parties.yml`, `wars.yml`, `season.yml`, `hall.yml`

## Important migration note
The plugin does not automatically parse Skript `variables.csv`. Back up `plugins/Skript/` before removing the old scripts. For first production test, run the plugin on a staging copy or migrate party data manually.

## Commands
- `/party`
- `/party help`
- `/pchat <message>`
- `/partywar status|top|hunt`
- Admin: `/partywar start [minutes] [target] [prepare]`, `/partywar finish`, `/partywar cancel`
- `/partyseason status|top`
- Admin: `/partyseason start <name>`, `/partyseason end`
- `/partyhall`

## Admin permission
`menkiestesparty.admin`

## Build from source

```bash
gradle clean build
```

Output JAR: `build/libs/MENKIESTESParty-1.0.0.jar`.

This source is maintained in the MenkiPlugcore plugin monorepo so future fixes can be tracked as normal commits and version tags.
