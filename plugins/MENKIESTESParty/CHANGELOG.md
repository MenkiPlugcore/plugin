# Changelog

## 1.0.2 - 2026-09-06

Reward Hall participation and item-management update.

- Reward Hall item is now configurable in-game with `/partyhall setitem` while holding the desired item.
- Added `/partyhall item` and `/partyhall clearitem`.
- Hall reward item is snapshotted when the War finishes so later admin edits do not alter an already-awarded Week.
- Removed the single-recipient Owner selection flow.
- Every eligible member of the winning Party can claim the Hall reward once.
- Eligibility now requires the configured minimum participation time and an actual combat record against another Party during the War.
- War history now persists per-player minutes, kills, points, deaths, combat participation and eligibility before runtime data is cleared.
- `/partyhall` shows the player's eligibility, claim state and recorded War contribution.

## 1.0.1 - 2026-09-06

Hotfix for Paper 1.21.11 runtime compatibility.

- Fixed `/party` GUI crash caused by an incompatible `ItemStack#setItemMeta` method signature in the original v1.0.0 local build.
- GUI item creation now uses Paper/Bukkit `ItemStack#editMeta` and is built against the real Paper 1.21.11 API.
- Added `/partychat` as an alias of `/pchat`.

## 1.0.0 - 2026-09-06

Initial native Paper core release.

- Port Party core from Skript to Java/Paper.
- Local YAML storage; no database dependency.
- Party roles, home, chat, GUI, reputation and level progression.
- Weekly Mining/Hunter/Farmer quests with contribution tracking.
- Simple Party Relic Lv.1-5 based on completed missions (3/9/18/30).
- Open-world Party War in `world` with no forced teleport or special war world.
- Kill scoring, Owner bonus, anti-farm cooldown and combat-logout handling.
- Reward chest tickets, Season progression and basic Reward Hall.
- Optional PlaceholderAPI and GriefPrevention integrations.

### Not yet full parity

Party Duel/Training, advanced inventory recovery and the full Reward Hall editor/history remain planned for v1.1.0.
