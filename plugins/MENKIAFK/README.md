# MENKIAFK v1.1.0 Universal

Native AFK plugin for MENKIESTES servers.

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
- AFK duration tracking in RAM
- chat mention warning for AFK players
- `/msg`, `/tell`, `/w`, `/whisper`, `/pm`, `/m` warning without cancelling the original command
- bounded remembered-message inbox while the target is AFK
- return on movement, rotation, chat, command, interaction, inventory click, or death (configurable)
- plugin teleports do not count as activity by default
- runtime-only maps: no variable/database spam
- PlaceholderAPI placeholders when PlaceholderAPI is installed

## PlaceholderAPI

- `%menkiafk_status%`
- `%menkiafk_reason%`
- `%menkiafk_time%`
- `%menkiafk_type%`

## Permissions

- `menki.afk` - use `/afk` (default: true)
- `menki.afk.admin` - admin status/reload and check other players (default: op)
- `menki.afk.auto.bypass` - bypass automatic AFK (default: op)
- `menki.afk.color` - allow `&` color codes in AFK reasons (default: op)

## Build

Requirements: JDK 21+ and Maven.

```bash
mvn clean package
```

The project uses `maven.compiler.release=21` and Paper API `1.21.11-R0.1-SNAPSHOT` as `provided`.

## Installation

1. Stop the server.
2. Put `MENKIAFK-1.1.0-Universal.jar` in `plugins/`.
3. Remove/rename the old MENKIAFK JAR so two versions are not loaded together.
4. Start the server.
5. Optional: install PlaceholderAPI for the four `%menkiafk_*%` placeholders.

## Upgrade from v1.0.0

The configuration keys remain backward compatible. Existing `plugins/MENKIAFK/config.yml` can be retained. If you want the refreshed comments/defaults, back up the old config and let v1.1.0 regenerate it.

## Performance design

AFK sessions, last-activity timestamps and cooldowns are runtime memory only. Movement/rotation uses a throttled timestamp update; auto-AFK is checked periodically rather than scanning every tick. Mention work is bounded by online AFK players and a configurable notification cap.
