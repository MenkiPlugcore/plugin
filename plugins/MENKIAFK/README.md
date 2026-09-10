# MENKIAFK v1.5.1 Universal — Production Candidate

Standalone native AFK plugin by MENKIESTES. MENKIAFK does not require a specific server setup, economy plugin, AFK world, crate plugin, or external database.

v1.5.1 is a **feature-freeze production candidate**. It does not add gameplay features; it adds automated regression and artifact verification gates before MENKIAFK v1.6.0 Production Stable.

## Compatibility target

- Paper 1.21.11 -> 26.2: one JAR target
- Spigot 1.21.11: supported through Bukkit/Spigot API usage
- Build bytecode: Java 21 (`--release 21`)
- CI regression matrix: Java 21 and Java 25
- No NMS, CraftBukkit internals, reflection into Minecraft internals, or paperweight/reobf output
- PlaceholderAPI is optional (`softdepend`)
- EssentialsX compatible; MENKIAFK can own the bare `/afk` label while `/essentials:afk` remains untouched

The plugin intentionally compiles against the lowest target API, Paper 1.21.11, so the universal JAR does not accidentally reference newer-only APIs.

## v1.5.1 Production Candidate gates

Every pull request and production build now verifies:

- Maven regression tests on Java 21 and Java 25
- public API v1 method signatures
- immutable snapshot behavior and legacy-session accounting
- lifecycle events remain notification-only/non-cancellable
- `plugin.yml` name, version, main class, and API baseline
- default config remains standalone and production-candidate versioned
- required public API classes are present inside the final JAR
- Java classfile baseline remains Java 21 (major version 65)
- Bukkit/Paper and PlaceholderAPI provided dependencies are not bundled into the JAR
- no NMS or CraftBukkit bytecode dependency is detected by the release verification step
- release channel is explicitly `candidate` or `stable`

The `candidate` channel is published as a GitHub **Prerelease**, not as Latest stable. v1.6.0 can switch the same pipeline to `stable` after manual smoke testing is complete.

See [`PRODUCTION-CANDIDATE.md`](PRODUCTION-CANDIDATE.md) for the remaining live-server checklist.

## Features

- `/afk [reason]` manual AFK
- optional or required manual reason through config
- configurable default reason when `/afk` is used without arguments
- `/afk` again returns from AFK
- configurable manual cooldown and maximum reason length
- `menki.afk.silent` permission to suppress AFK/return broadcasts without changing AFK state or statistics
- auto AFK with configurable timeout/check interval
- MANUAL/AUTO AFK type
- `/afkcheck [player]`
- `/afklist` for currently online AFK players
- chat mention warning for AFK players
- private-message warning for configured message commands
- bounded remembered-message inbox while the target is AFK
- configurable return-on activity triggers
- persistent AFK statistics stored locally in `plugins/MENKIAFK/stats.yml`
- `/afkstats [player]` for today, week, total, session counters, longest session, and last AFK
- `/afktop [total|today|week|longest|sessions] [page]`
- `/afkleaderboard` alias
- configurable minimum AFK duration before a session enters statistics
- Manual vs Auto session counters
- autosave checkpoints for active valid sessions
- `/menkiafk resetstats <player>`
- PlaceholderAPI support when installed
- read-only public API for other Bukkit/Paper plugins
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

The public API remains intentionally read-only. External plugins cannot force AFK/return or access mutable internal managers/YAML through the supported API contract.

See [`API.md`](API.md) for integration details.

## Commands

- `/afk [reason]` - enter AFK manually; use `/afk` again to return
- `/afkcheck [player]` - check AFK state
- `/afklist` - list online AFK players, duration, type, and reason
- `/afkstats [player]` - view persistent AFK statistics
- `/afktop [total|today|week|longest|sessions] [page]` - view leaderboard
- `/afkleaderboard ...` - alias of `/afktop`
- `/menkiafk status` - runtime status including statistics I/O health
- `/menkiafk reload` - checkpoint statistics and reload configuration
- `/menkiafk resetstats <player>` - reset one player's saved statistics

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

Numeric total placeholders return plain integer values for conditions, sorting, math, and external integrations.

## Permissions

- `menki.afk` - use `/afk` (default: true)
- `menki.afk.list` - use `/afklist` (default: true)
- `menki.afk.stats` - use `/afkstats` (default: true)
- `menki.afk.top` - use `/afktop` / `/afkleaderboard` (default: true)
- `menki.afk.admin` - admin status/reload/reset/checking other players (default: op)
- `menki.afk.auto.bypass` - bypass automatic AFK (default: op)
- `menki.afk.color` - allow `&` color codes in AFK reasons (default: op)
- `menki.afk.silent` - suppress own AFK/return broadcasts while keeping state/stats (default: false)

## Statistics design

Persistent storage uses Bukkit YAML (`stats.yml`). There is no MySQL, Redis, SQLite driver, Vault, packet library, GUI framework, or server-specific dependency.

- activity detection does not write statistics to disk
- leaderboard sorting runs only when `/afktop` is requested
- `/afklist` scans online players only when used
- daily history is bounded by `stats.keep-daily-days`
- active sessions are projected into autosave checkpoints without mutating RAM totals
- sessions shorter than `stats.minimum-session-seconds` do not enter duration/session statistics
- `last-afk-at` is a single timestamp per player
- `stats.yml` uses temp-file + atomic replace when supported
- corrupt YAML is quarantined instead of silently overwritten
- newer unsupported schemas are read best-effort with writes blocked to protect downgrade data
- schema remains version 3 and is backward compatible with v1.2.0-v1.5.0 data

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
MENKIAFK-1.5.1-Universal.jar
```

JUnit is test-scope only and is not included in the production JAR.

## Installation / upgrade

1. Stop the server.
2. Back up `plugins/MENKIAFK/` before candidate testing.
3. Put `MENKIAFK-1.5.1-Universal.jar` in `plugins/`.
4. Remove/rename older MENKIAFK JARs so only one version loads.
5. Start the server.
6. Run `/menkiafk status` and confirm `Stats I/O: OK`.
7. Optional: install PlaceholderAPI for `%menkiafk_*%` placeholders.

Existing v1.2.0-v1.5.0 config and `stats.yml` data remain compatible. v1.5.1 adds no required runtime configuration key and keeps `stats.yml` schema version 3.

## Performance design

v1.5.1 adds no runtime scheduler, gameplay listener, database, network I/O, or integration dependency. New regression code is test-scope/build-time only. Runtime behavior remains the v1.5.0 core: RAM AFK state, existing auto-AFK/statistics tasks, bounded YAML persistence, and read-only API/event hooks.
