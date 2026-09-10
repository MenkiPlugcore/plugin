# MENKIAFK — Production Candidate / Deployment Smoke Checklist

This checklist was introduced for v1.5.1 and remains the recommended deployment validation checklist for the v1.6.x Production Stable line.

The repository's automated release gates validate compilation, API contract, packaging, bytecode baseline, and forbidden dependency usage. They do **not** reproduce every operator's real Paper server, plugin stack, filesystem, permissions, or proxy environment. Run the live checks below on a backup/staging server before replacing a critical production deployment.

## Automated gates

These checks run in GitHub Actions before the release job:

- Maven regression tests on Java 21
- Maven regression tests on Java 25
- public API v1 method-signature checks
- immutable AFK session/statistics snapshot checks
- legacy-session accounting check
- lifecycle events remain non-cancellable
- `plugin.yml` metadata/version/API baseline checks
- default `config.yml` metadata check
- required public API classes exist in the built JAR
- classfile baseline is Java 21 / major version 65
- Bukkit/Paper and PlaceholderAPI provided dependencies are not bundled into the JAR
- `jdeps` output contains no NMS or CraftBukkit dependency
- release channel is explicitly `candidate` or `stable`

## Recommended live Paper smoke test

Use a backup/copy of a real server for the first deployment pass.

- Fresh install on Paper 1.21.11 + Java 21
- Startup with PlaceholderAPI absent
- Startup with PlaceholderAPI present
- Startup with EssentialsX present and `/afk` command conflict scenario
- Manual `/afk` with no reason using default config
- Manual `/afk <reason>`
- Return from AFK using `/afk`
- Return by movement
- Return by rotation
- Return by chat
- Return by command
- Return by interaction/inventory click
- Auto AFK timeout
- `menki.afk.auto.bypass`
- `menki.afk.silent`
- `/afkcheck`
- `/afklist`
- `/afkstats`
- `/afktop` and `/afkleaderboard`
- PlaceholderAPI state/statistics placeholders
- Minimum-session threshold does not pollute statistics
- Manual/Auto counters remain correct
- Quit while AFK finalizes the session
- Kick while AFK finalizes the session
- Normal server shutdown finalizes and saves active sessions
- Restart preserves statistics
- `/menkiafk reload` preserves/checkpoints active statistics safely
- `/menkiafk resetstats <player>` while player is online and offline
- `/menkiafk status` reports `Stats I/O: OK` on healthy storage
- Corrupt `stats.yml` is quarantined instead of silently overwritten
- Newer unsupported schema blocks writes during downgrade protection
- Upgrade using an existing v1.2.0-v1.5.x `stats.yml`
- Public `MenkiAfkAPI` service is discoverable by a small consumer plugin
- `PlayerEnterAfkEvent` fires after state is committed
- `PlayerLeaveAfkEvent` fires after normal return/quit/kick
- No leave event is expected during plugin/server disable
- Paper 26.2 + Java 25 smoke pass using the same JAR

## Stable deployment rule

For an individual production server, deploy v1.6.x after:

1. repository automated gates are green,
2. the server-specific staging/smoke pass has no release-blocking failure,
3. only one MENKIAFK JAR is present,
4. `plugins/MENKIAFK/` has been backed up,
5. `/menkiafk status` reports healthy statistics I/O after startup.

If a stable-line bug is found, patch it in v1.6.x without breaking public API v1 or `stats.yml` schema compatibility unless a future major release explicitly requires that change.
