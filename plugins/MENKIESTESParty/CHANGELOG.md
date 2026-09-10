# Changelog

## 1.2.0 - 2026-09-10

Party Progression Update.

### Added

- Party Projects with shared progress and configurable definitions.
- Default projects: Mining Expedition, Monster Hunt, Harvest Drive.
- Party Skill Tree with configurable nodes, prerequisites and level requirements.
- Default skill branches: COMBAT, LABOR and COMMAND.
- Party Divisions as an optional specialization layer independent from Owner/Officer/Member roles.
- Default divisions: Combat, Resource and Support.
- Dynamic Party Identity calculated automatically from recent combat, mining, farming and Project activity.
- Rolling Identity activity window, minimum activity threshold and configurable dominance percentage.
- `/party profile` combined progression overview.
- Direct aliases: `/partyproject`, `/partyskill`, `/partydivision`, `/partyidentity`, `/partyprofile`.
- Nested progression commands under `/party` are handled without replacing the existing core Party command executor.
- PlaceholderAPI: `%mparty_identity%`, `%mparty_division%`, `%mparty_skill_points%`, `%mparty_project%`, `%mparty_project_progress%`.
- Module toggles for Projects, Skill Tree, Divisions and Identity.
- v1.2 config migration that merges missing defaults into existing server configs without resetting Party data.

### Compatibility

- Existing v1.1.0 `parties.yml`, `wars.yml` and `season.yml` remain valid.
- Weekly Quest, Daily Mission, Party Relic, Party War and Season behavior remain available.
- Party Hall and automatic War item rewards remain removed.

## 1.1.0 - 2026-09-09

### Added

- Daily Party Mission shared per Party.
- Daily Mining: 150 target, +40 Party XP.
- Daily Hunter: 30 target, +50 Party XP.
- Daily Farmer: 80 target, +35 Party XP.
- `/party daily`, `/partydaily`, `/partydaily resetall`.

### Changed

- Party War reward otomatis disederhanakan menjadi Party XP saja.
- Default Party War XP: 250.
- Reward senjata/custom item Party War diberikan manual oleh admin ke perwakilan team.

### Removed

- Party Hall sebagai sistem reward.
- War Chest reward otomatis.
- Item reward queue/claim dari Party War.

## 1.0.4

- Party Level 1 member cap menjadi 5.
- Party War, Weekly Quest, Party Relic, Party GUI dan local YAML storage.
