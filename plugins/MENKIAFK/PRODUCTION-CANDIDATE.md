# MENKIAFK v1.5.1 — Production Candidate Checklist

v1.5.1 is the final candidate stage before v1.6.0 Production Stable. Feature development is frozen during this stage.

## Automated gates

These checks run in GitHub Actions before the release job:

- Maven regression tests on Java 21
- Maven regression tests on Java 25
- public API v1 method-signature checks
- immutable AFK session/statistics snapshot checks
- legacy-session accounting check
- lifecycle events remain non-cancellable
- `plugin.yml` metadata/version/API baseline checks
- default `config.yml` production-candidate metadata check
- required public API classes exist in the built JAR
- classfile baseline is Java 21 / major version 65
- Bukkit/Paper and PlaceholderAPI provided dependencies are not bundled into the JAR
- `jdeps` output contains no NMS or CraftBukkit dependency
- release channel is explicitly `candidate` or `stable`

## Manual live Paper smoke test required before v1.6.0

Use a backup/copy of a real server, not the primary production instance for the first pass.

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
- Upgrade using an existing v1.2.0/v1.3.0/v1.4.x/v1.5.0 `stats.yml`
- Public `MenkiAfkAPI` service is discoverable by a small consumer plugin
- `PlayerEnterAfkEvent` fires after state is committed
- `PlayerLeaveAfkEvent` fires after normal return/quit/kick
- No leave event is expected during plugin/server disable
- Paper 26.2 + Java 25 smoke pass using the same JAR

## Promotion rule

Promote to v1.6.0 Production Stable only when:

1. all automated candidate gates are green,
2. the live Paper smoke-test checklist has no release-blocking failure,
3. no public API v1 breaking change is required,
4. `stats.yml` schema can remain backward compatible,
5. no new feature is added during the candidate period.

If a candidate bug is found, fix it as v1.5.2 (or another 1.5.x candidate patch) and repeat the checklist instead of shipping the bug into v1.6.0.
