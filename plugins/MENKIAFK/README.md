# MENKIAFK v1.6.0 Universal — Production Stable

Standalone native AFK plugin by MENKIESTES. MENKIAFK does not require a specific server setup, economy plugin, AFK world, crate plugin, or external database.

v1.6.0 is the first **Production Stable** release line. It preserves the v1.5.x feature set and promotes the release channel from candidate to stable after the automated regression and final-artifact gates introduced in v1.5.1.

## Compatibility target

- Paper 1.21.11 -> 26.2: one universal JAR target
- Spigot 1.21.11: supported through Bukkit/Spigot API usage
- Build bytecode: Java 21 (`--release 21`)
- CI regression matrix: Java 21 and Java 25
- No NMS, CraftBukkit internals, reflection into Minecraft internals, or paperweight/reobf output
- PlaceholderAPI is optional (`softdepend`)
- EssentialsX compatible; MENKIAFK can own bare `/afk` while `/essentials:afk` remains untouched

## Production release gates

Every pull request and production build verifies:

- Maven regression tests on Java 21 and Java 25
- public API v1 method signatures
- immutable snapshot behavior and legacy-session accounting
- lifecycle events remain notification-only/non-cancellable
- `plugin.yml` name, version, main class, and API baseline
- default config remains standalone and versioned correctly
- required public API classes exist in the final JAR
- Java classfile baseline remains Java 21 / major version 65
- Bukkit/Paper and PlaceholderAPI provided dependencies are not bundled
- `jdeps` finds no NMS/CraftBukkit bytecode dependency
- release channel is explicitly `stable`

The stable channel publishes a normal GitHub release and marks it Latest. The automated gates validate build/runtime compatibility assumptions, but they do not replace a deployment smoke test on each operator's real server stack. See [`PRODUCTION-CANDIDATE.md`](PRODUCTION-CANDIDATE.md) for the recommended live-server checklist.

## Features

- `/afk [reason]` manual AFK with optional/default reason configuration
- `/afk` again returns from AFK
- configurable cooldown and maximum reason length
- `menki.afk.silent` to suppress own AFK/return broadcasts without changing state/statistics
- auto AFK with configurable timeout/check interval and bypass permission
- MANUAL/AUTO AFK type
- `/afkcheck [player]`
- `/afklist` for currently online AFK players
- chat mention warning for AFK players
- private-message warning for configured message commands
- bounded remembered-message inbox while target is AFK
- configurable return-on activity triggers
- persistent local statistics in `plugins/MENKIAFK/stats.yml`
- `/afkstats [player]` for today, week, total, sessions, Manual/Auto counts, longest, and last AFK
- `/afktop [total|today|week|longest|sessions] [page]`
- `/afkleaderboard` alias
- minimum AFK duration before a session enters statistics
- autosave checkpoints for active valid sessions
- `/menkiafk resetstats <player>`
- PlaceholderAPI support when installed
- stable read-only public API for other Bukkit/Paper plugins
- `PlayerEnterAfkEvent` and `PlayerLeaveAfkEvent`

## Public API v1

MENKIAFK registers `store.menkiestes.menkiafk.api.MenkiAfkAPI` through Bukkit `ServicesManager`.

```java
import store.menkiestes.menkiafk.api.MenkiAfkAPI;

MenkiAfkAPI afkApi = MenkiAfkAPI.get();
boolean afk = afkApi.isAfk(player.getUniqueId());
long total = afkApi.getTotalAfkTime(player.getUniqueId());
```

Public immutable models:

- `AfkSessionSnapshot`
- `AfkStatisticsSnapshot`
- `AfkSessionType`

Public events:

- `PlayerEnterAfkEvent`
- `PlayerLeaveAfkEvent`

The v1 API is intentionally read-only. External plugins cannot force AFK/return or access mutable internal managers/YAML through the supported contract. See [`API.md`](API.md) for integration details.

## Commands

- `/afk [reason]`
- `/afkcheck [player]`
- `/afklist`
- `/afkstats [player]`
- `/afktop [total|today|week|longest|sessions] [page]`
- `/afkleaderboard ...`
- `/menkiafk status`
- `/menkiafk reload`
- `/menkiafk resetstats <player>`

## PlaceholderAPI

Current AFK state:

- `%menkiafk_status%`
- `%menkiafk_reason%`
- `%menkiafk_time%`
- `%menkiafk_type%`
- `%menkiafk_last_afk%`

Persistent statistics:

- `%menkiafk_stats_today%`
- `%menkiafk_stats_week%`
- `%menkiafk_stats_total%`
- `%menkiafk_stats_total_seconds%`
- `%menkiafk_stats_total_minutes%`
- `%menkiafk_stats_total_hours%`
- `%menkiafk_stats_sessions%`
- `%menkiafk_stats_manual_sessions%`
- `%menkiafk_stats_auto_sessions%`
- `%menkiafk_stats_longest%`

## Permissions

- `menki.afk` — use `/afk` (default: true)
- `menki.afk.list` — use `/afklist` (default: true)
- `menki.afk.stats` — use `/afkstats` (default: true)
- `menki.afk.top` — use `/afktop` / `/afkleaderboard` (default: true)
- `menki.afk.admin` — admin status/reload/reset/checking other players (default: op)
- `menki.afk.auto.bypass` — bypass automatic AFK (default: op)
- `menki.afk.color` — allow `&` color codes in AFK reasons (default: op)
- `menki.afk.silent` — suppress own AFK/return broadcasts while keeping state/stats (default: false)

## Statistics and performance design

Persistent storage uses Bukkit YAML (`stats.yml`). There is no MySQL, Redis, SQLite driver, Vault, packet library, GUI framework, or server-specific dependency.

- runtime AFK state remains RAM-based
- movement/chat paths do not write statistics to disk
- leaderboard sorting runs only when `/afktop` is requested
- `/afklist` scans online players only when used
- daily history is bounded by `stats.keep-daily-days`
- active sessions are projected into autosave checkpoints without mutating RAM totals
- sessions shorter than `stats.minimum-session-seconds` do not enter duration/session statistics
- `stats.yml` uses temp-file + atomic replace when supported
- corrupt YAML is quarantined instead of silently overwritten
- newer unsupported schemas are read best-effort with writes blocked to protect downgrade data
- schema remains version 3 and is backward compatible with v1.2.0-v1.5.1 data

## Build and tests

Requirements: JDK 21+ and Maven.

```bash
mvn clean package
```

Regression tests only:

```bash
mvn clean test
```

Output:

```text
MENKIAFK-1.6.0-Universal.jar
```

JUnit is test-scope only and is not included in the production JAR.

## Installation / upgrade

1. Stop the server.
2. Back up `plugins/MENKIAFK/`.
3. Put `MENKIAFK-1.6.0-Universal.jar` in `plugins/`.
4. Remove/rename older MENKIAFK JARs so only one version loads.
5. Start the server.
6. Run `/menkiafk status` and confirm `Stats I/O: OK`.
7. Optional: install PlaceholderAPI for `%menkiafk_*%` placeholders.

Existing v1.2.0-v1.5.1 config and `stats.yml` data remain compatible. v1.6.0 adds no required runtime configuration key and keeps `stats.yml` schema version 3.

## Release policy after v1.6.0

The 1.6.x line is maintenance-first. Patch releases should focus on bug fixes, compatibility, performance, documentation, and security hardening. New breaking API/storage behavior belongs in a future major release rather than silently changing the v1 contract.
