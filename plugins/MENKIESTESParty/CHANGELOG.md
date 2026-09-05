# Changelog

## 1.0.4 - 2026-09-06

Balance and Reward Hall month-rollover update.

- Party Level 1 member cap is reduced from 8 to 5.
- Existing config files are migrated once on startup: any Level 1 slot value above 5 is capped to 5.
- Existing Level 1 parties that already have more than 5 members are not auto-kicked; they cannot add more members until they are within the cap or level up.
- Reward Hall planned-item queue remains persistent across month changes. September rewards can continue directly into October without rebuilding the queue.
- Hall entry IDs still roll by month/week automatically, for example `2026-09-W4` -> `2026-10-W1`.

## 1.0.3 - 2026-09-06

Reward planning and member-management update.

- Reward Hall now supports a future War reward queue instead of only one active template.
- `/partyhall setitem [slot]` sets/replaces a planned reward; slot `1` is the next War.
- `/partyhall additem` appends the held item to the next free future War slot.
- `/partyhall plan` shows the planned reward queue.
- `/partyhall removeitem <slot>` removes a planned reward and compacts the queue.
- `/partyhall clearitems` clears all future Hall rewards.
- `/partyhall setcurrent` can recover/replace the item for the already-awarded current Hall entry.
- Each completed Party War consumes only the next planned item and snapshots it into that War's Hall entry; later rewards remain queued.
- Existing v1.0.2 `reward-item` data is treated as a one-time legacy next reward for migration compatibility.
- Added GUI Invite Player and Manage Members flows, injected directly into `/party`.
- Owner can promote/demote and kick non-owner members from the GUI.
- Officer can invite players and kick regular Members, but cannot manage Owner or another Officer.
- `/party invite` without a player opens the invite GUI; `/party members` and `/party manage` open the roster GUI.
- Direct invite/accept commands are blocked while Party War roster locking is active.

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
