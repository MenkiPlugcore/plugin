# Changelog

## 1.3.0 - 2026-09-10

Party Interaction Update.

### Added

- Party Contracts between Parties with built-in `mining`, `hunter`, and `farmer` objective types.
- Contract proposal/accept/deny/cancel/abandon lifecycle.
- Contract proposal expiry, active deadline, per-pair cooldown, open/active limits, history retention and placed-block anti-abuse.
- In-memory active Contract index so normal gameplay events do not scan all Contract history.
- Diplomacy relations: `NEUTRAL`, `ALLY`, and `RIVAL`.
- Symmetric Trust Score from `-100` to `100`.
- Mutual Alliance request flow with automatic completion when both Parties request each other.
- Contract completion/failure Trust integration.
- Recruitment modes: `OPEN`, `APPLICATION`, and `CLOSED`.
- Party Browser and Join Applications with optional applicant note.
- Application expiry, per-player application limit, accept/deny/cancel flow and automatic stale-application cleanup.
- Configurable interaction capability lists for Officer and Member roles with wildcard support.
- `/party interaction`, `/party contract`, `/party diplomacy`, `/party browse`, `/party apply`, `/party applications`, `/party recruitment`, `/party rankperms`.
- Direct aliases: `/partyinteraction`, `/partycontract`, `/partydiplomacy`, `/partybrowse`, `/partyapply`, `/partyapplications`, `/partyrecruitment`, `/partyrankperms`.
- Dedicated `interactions.yml` storage.
- PlaceholderAPI: `%mparty_recruitment%`, `%mparty_contracts_active%`, `%mparty_contracts_completed%`, `%mparty_applications_pending%`.
- v1.3 config migration that merges Interaction defaults without resetting existing Party data.

### Safety / Public Plugin Defaults

- Automatic Contract Party XP defaults to `0` to avoid alt-Party XP farming.
- Party limits and Party War membership locks are respected by OPEN recruitment and application acceptance.
- Contract mining ignores blocks placed after startup using a bounded in-memory anti-abuse set.
- Owner always keeps all v1.3 interaction capabilities to prevent configuration lockout.
- No Vault, database, proxy network, or server-specific dependency is required.

### Compatibility

- Existing v1.2.2 `parties.yml`, `wars.yml`, `season.yml`, and progression data remain valid.
- New cross-Party state is isolated in `interactions.yml`.
- No database migration is required.

## 1.2.2 - 2026-09-10

GUI & Safety Polish.

### Added

- Pagination for custom Party Projects, Skill Tree nodes, Divisions, Division member management and member Division assignment.
- Confirmation screen before cancelling an active Party Project.
- Confirmation screen before spending a Party Skill Point.
- Visual progress bars for Party Level, active Projects and Dynamic Identity activity shares.
- Clickable Party Level progression screen from Party Profile.
- Granular GUI permission nodes for Progression, Projects, Skill Tree, Divisions and Identity.
- Separate action permissions for Project management, Skill unlock, Division self-select and Division management.
- `gui.progression.progress-bar-width` config.
- `gui.progression.confirmations.project-cancel` config.
- `gui.progression.confirmations.skill-unlock` config.
- v1.2.2 config migration that merges new GUI defaults without resetting Party data.

### Changed

- Progression Hub now visually marks modules or GUI sections that the player cannot access.
- Division management buttons respect both Party role rules and Bukkit permissions.
- Existing progression commands remain unchanged and continue working independently from GUI permissions.

### Compatibility

- Existing v1.2.1 Party data remains valid.
- No database migration is required.
- No new required dependency is added.

## 1.2.1 - 2026-09-10

Progression GUI Update.

### Added

- New Progression Hub accessible from the main `/party` GUI.
- Inventory GUI for Party Profile.
- Inventory GUI for Party Projects, including project start and cancel actions.
- Inventory GUI for Party Skill Tree with clickable unlock nodes.
- Inventory GUI for Party Divisions with self-select support.
- Owner/Officer GUI flow to assign Divisions to Party members.
- Inventory GUI for Dynamic Party Identity with activity breakdown.
- Division information is now visible in the Party member roster.
- New `gui.progression.enabled` toggle. Commands remain available when the GUI is disabled.
- v1.2.1 config migration merges the new GUI defaults into existing configs.

### Changed

- Removed the obsolete Party Reward Hall button from the main Party GUI.
- The old Hall slot is now used by Party Progression.
- Existing v1.2.0 progression data remains fully compatible.

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
