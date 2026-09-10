# MENKIAFK v1.4.1 Universal

Standalone native AFK plugin by MENKIESTES. MENKIAFK does not require a specific server setup, economy plugin, AFK world, crate plugin, or external database.

## Compatibility target

- Paper 1.21.11 -> 26.2: one JAR target
- Spigot 1.21.11: supported through Bukkit/Spigot API usage
- Build bytecode: Java 21 (`--release 21`)
- Runtime: Java 21 on Paper 1.21.11; Java 25 on Paper 26.1+
- No NMS, CraftBukkit internals, reflection into Minecraft internals, or paperweight/reobf output
- PlaceholderAPI is optional (`softdepend`)
- EssentialsX compatible; MENKIAFK can own the bare `/afk` label while `/essentials:afk` remains untouched

The plugin intentionally compiles against the lowest target API, Paper 1.21.11, so the universal JAR does not accidentally reference newer-only APIs.

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
- `/msg`, `/tell`, `/w`, `/whisper`, `/pm`, `/m` warning without cancelling the original command
- bounded remembered-message inbox while the target is AFK
- configurable return-on activity triggers
- runtime AFK sessions remain RAM-only and TPS-friendly
- persistent AFK statistics stored locally in `plugins/MENKIAFK/stats.yml`
- `/afkstats [player]` for today, week, total AFK, session counters, longest session, and last AFK
- `/afktop [total|today|week|longest|sessions] [page]`
- `/afkleaderboard` alias for `/afktop`
- configurable minimum AFK duration before a session enters statistics
- Manual vs Auto session counters
- autosave checkpoints for active valid sessions
- admin reset with `/menkiafk resetstats <player>`
- PlaceholderAPI placeholders when PlaceholderAPI is installed

## v1.4.1 Stability Patch

v1.4.1 is intentionally a feature freeze patch. It adds no gameplay system and no new repeating task.

Stability hardening:

- async chat continuations verify the player is still online before touching AFK state
- external namespaced AFK commands such as `/essentials:afk` are no longer mistaken for MENKIAFK's own toggle in activity handling
- `stats.yml` saves are written to `stats.yml.tmp` first and then replaced atomically when the filesystem supports it
- unreadable YAML is quarantined as `stats-corrupt-<timestamp>.yml` instead of being silently overwritten
- if the corrupt file cannot be quarantined, statistic writes are blocked for that server session to protect the original file
- inconsistent Manual/Auto counters and longest-session values are normalized safely on load
- `/menkiafk status` includes `Stats I/O: OK/BLOCKED`
- `/menkiafk reload` checkpoints statistics before applying new statistics configuration
- shutdown logs distinguish a successful save from a deliberately blocked persistence state

The persistent schema remains version 3. Existing v1.2.0, v1.3.0, and v1.4.0 data remains readable.

## v1.4.0 Utility & Configuration

Manual AFK configuration:

```yaml
manual-afk:
  require-reason: false
  default-reason: "Sedang tidak tersedia"
```

With `require-reason: false`, `/afk` immediately enters AFK using `default-reason`. Set it to `true` to preserve reason-required behavior.

Silent AFK is permission-based:

```text
menki.afk.silent
```

A player with this permission still becomes AFK normally and still contributes to statistics, but their AFK and return broadcasts are suppressed. The permission defaults to false so existing server broadcast behavior is not changed automatically.

`stats.yml` schema v3 adds `last-afk-at`. Existing v1.2.0/v1.3.0 data remains readable; older records simply have no historical last-AFK timestamp until the player enters AFK again.

## Commands

- `/afk [reason]` - enter AFK manually; use `/afk` again to return
- `/afkcheck [player]` - check current AFK state
- `/afklist` - list online AFK players, duration, type, and reason
- `/afkstats [player]` - view persistent AFK statistics and last AFK
- `/afktop [total|today|week|longest|sessions] [page]` - view leaderboard
- `/afkleaderboard ...` - alias of `/afktop`
- `/menkiafk status` - plugin runtime status including statistics I/O health
- `/menkiafk reload` - checkpoint statistics and reload configuration
- `/menkiafk resetstats <player>` - reset one player's saved AFK statistics

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

Numeric total placeholders return plain integer values and are intended for scoreboard conditions, sorting, math, and external integrations without parsing formatted duration text.

## Permissions

- `menki.afk` - use `/afk` (default: true)
- `menki.afk.list` - use `/afklist` (default: true)
- `menki.afk.stats` - use `/afkstats` (default: true)
- `menki.afk.top` - use `/afktop` and `/afkleaderboard` (default: true)
- `menki.afk.admin` - admin status/reload/reset and checking other players (default: op)
- `menki.afk.auto.bypass` - bypass automatic AFK (default: op)
- `menki.afk.color` - allow `&` color codes in AFK reasons (default: op)
- `menki.afk.silent` - suppress own AFK/return broadcasts while keeping normal state/stats (default: false)

## Statistics design

Persistent storage uses Bukkit YAML (`stats.yml`). There is no MySQL, Redis, SQLite driver, Vault, packet library, GUI framework, or server-specific dependency.

- activity detection does not write statistics to disk
- leaderboard sorting runs only when `/afktop` is requested
- `/afklist` scans online players only when used
- daily history remains bounded by `stats.keep-daily-days`
- active sessions are projected into autosave checkpoints without mutating in-memory totals
- sessions shorter than `stats.minimum-session-seconds` do not enter duration/session statistics
- last-AFK timestamp is a single long value per player and adds no scheduler
- save replacement is crash-safer because the target file is not directly rewritten in-place

## Build

Requirements: JDK 21+ and Maven.

```bash
mvn clean package
```

Output:

```text
MENKIAFK-1.4.1-Universal.jar
```

## Installation / upgrade

1. Stop the server.
2. Put `MENKIAFK-1.4.1-Universal.jar` in `plugins/`.
3. Remove/rename older MENKIAFK JARs so only one version loads.
4. Start the server.
5. Optional: install PlaceholderAPI for `%menkiafk_*%` placeholders.

Existing v1.2.0/v1.3.0/v1.4.0 config and `stats.yml` data remain compatible. v1.4.1 adds no new required configuration key.

## Performance design

MENKIAFK v1.4.1 adds no new repeating task. AFK sessions, last-activity timestamps and cooldowns remain runtime memory only. Movement/rotation uses throttled timestamp updates; auto-AFK is checked periodically by the existing task. Persistent statistics are updated on AFK lifecycle transitions, autosave uses the existing stats task, and the stability hardening only changes lifecycle guards and the way YAML is safely replaced on disk.
