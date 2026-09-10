# MENKIAFK v1.3.0 Universal

Standalone native AFK plugin by MENKIESTES. MENKIAFK does not require a specific server setup, economy plugin, AFK world, crate plugin, or external database.

## Compatibility target

- Paper 1.21.11 -> 26.2: one JAR target
- Spigot 1.21.11: supported through Bukkit/Spigot API usage
- Build bytecode: Java 21 (`--release 21`)
- Runtime: Java 21 on Paper 1.21.11; Java 25 on Paper 26.1+
- No NMS, CraftBukkit internals, reflection into Minecraft internals, or paperweight/reobf output
- PlaceholderAPI is optional (`softdepend`)
- EssentialsX compatible; MENKIAFK can own the bare `/afk` label while `/essentials:afk` remains untouched

The plugin intentionally compiles against the **lowest target API (Paper 1.21.11)**. This keeps the bytecode from accidentally referencing newer 26.x-only APIs while remaining loadable by newer JVMs and Paper versions.

## Features

- `/afk <reason>` manual AFK
- `/afk` again returns from AFK
- configurable manual cooldown and maximum reason length
- auto AFK with configurable timeout/check interval
- MANUAL/AUTO AFK type
- `/afkcheck [player]`
- `/afklist` for currently online AFK players
- chat mention warning for AFK players
- `/msg`, `/tell`, `/w`, `/whisper`, `/pm`, `/m` warning without cancelling the original command
- bounded remembered-message inbox while the target is AFK
- return on movement, rotation, chat, command, interaction, inventory click, or death (configurable)
- plugin teleports do not count as activity by default
- runtime AFK sessions remain RAM-only and TPS-friendly
- persistent AFK statistics stored locally in `plugins/MENKIAFK/stats.yml`
- `/afkstats [player]` for today, current week, total AFK, session count, session type counters, and longest session
- `/afktop [total|today|week|longest|sessions] [page]`
- `/afkleaderboard` alias for `/afktop`
- configurable minimum AFK duration before a session enters statistics
- Manual vs Auto session counters
- active AFK sessions are included live in stats/leaderboards after they pass the configured minimum duration
- autosave checkpoints include active valid AFK time for better crash recovery
- admin reset with `/menkiafk resetstats <player>`
- PlaceholderAPI placeholders when PlaceholderAPI is installed

## Lightweight QoL design

MENKIAFK v1.3.0 intentionally avoids adding heavy systems:

- `/afklist` only scans currently online players when the command is used
- the AFK list output is bounded by `afk-list.max-entries`
- minimum session filtering is evaluated only when statistics are viewed or an AFK session ends/checkpoints
- Manual/Auto classification adds only two integer counters per player
- no new scheduler was added for v1.3.0
- no MySQL, Redis, SQLite driver, Vault, packet library, GUI framework, or server-specific dependency
- no disk writes occur in movement/chat hot paths

## Statistics design

Persistent storage uses Bukkit YAML (`stats.yml`). AFK activity detection remains RAM-based and leaderboard sorting happens only when `/afktop` is requested.

New v1.3.0 setting:

```yaml
stats:
  minimum-session-seconds: 10
```

Sessions shorter than this value are ignored by persistent statistics and leaderboards. Set it to `0` if every AFK session should count.

`stats.yml` schema v2 stores `manual-sessions` and `auto-sessions`. Existing v1.2.0 totals remain compatible. Old v1.2.0 sessions cannot be retroactively classified, so `/afkstats` may show them as `Legacy v1.2` sessions while all new sessions are classified normally.

## Commands

- `/afk <reason>` - enter AFK manually; use `/afk` again to return
- `/afkcheck [player]` - check current AFK state
- `/afklist` - list online AFK players, their duration, type, and reason
- `/afkstats [player]` - view persistent AFK statistics
- `/afktop [total|today|week|longest|sessions] [page]` - view leaderboard
- `/afkleaderboard ...` - alias of `/afktop`
- `/menkiafk status` - plugin runtime status
- `/menkiafk reload` - reload configuration
- `/menkiafk resetstats <player>` - reset one player's saved AFK statistics

## PlaceholderAPI

Current AFK state:

- `%menkiafk_status%`
- `%menkiafk_reason%`
- `%menkiafk_time%`
- `%menkiafk_type%`

Persistent statistics:

- `%menkiafk_stats_today%`
- `%menkiafk_stats_week%`
- `%menkiafk_stats_total%`
- `%menkiafk_stats_sessions%`
- `%menkiafk_stats_manual_sessions%`
- `%menkiafk_stats_auto_sessions%`
- `%menkiafk_stats_longest%`

## Permissions

- `menki.afk` - use `/afk` (default: true)
- `menki.afk.list` - use `/afklist` (default: true)
- `menki.afk.stats` - use `/afkstats` (default: true)
- `menki.afk.top` - use `/afktop` and `/afkleaderboard` (default: true)
- `menki.afk.admin` - admin status/reload/reset and checking other players (default: op)
- `menki.afk.auto.bypass` - bypass automatic AFK (default: op)
- `menki.afk.color` - allow `&` color codes in AFK reasons (default: op)

## Build

Requirements: JDK 21+ and Maven.

```bash
mvn clean package
```

The project uses `maven.compiler.release=21` and Paper API `1.21.11-R0.1-SNAPSHOT` as `provided`.

Output:

```text
MENKIAFK-1.3.0-Universal.jar
```

## Installation

1. Stop the server.
2. Put `MENKIAFK-1.3.0-Universal.jar` in `plugins/`.
3. Remove/rename older MENKIAFK JARs so only one version loads.
4. Start the server.
5. Optional: install PlaceholderAPI for `%menkiafk_*%` placeholders.

## Upgrade from v1.2.0

Existing configuration and `stats.yml` are backward compatible. The new options have built-in defaults even when an old `config.yml` is retained.

Existing v1.2.0 total/session statistics are preserved. Manual/Auto counters begin classification with v1.3.0 because v1.2.0 did not persist the AFK type of completed sessions.

## Performance design

AFK sessions, last-activity timestamps and cooldowns remain runtime memory only. Movement/rotation uses a throttled timestamp update; auto-AFK is checked periodically rather than scanning every tick. Mention work is bounded by online AFK players and a configurable notification cap. Persistent statistics are updated on AFK lifecycle transitions rather than movement events, `/afklist` scans only on command use, and leaderboard sorting remains command-driven.
