# Changelog

## 1.5.1 Universal — Production Candidate

- Feature freeze: no new gameplay feature, scheduler, database, GUI, packet library, NMS access, economy dependency, or server-specific integration.
- Added JUnit regression tests for the public API v1 contract, immutable snapshot behavior, legacy-session accounting, lifecycle event non-cancellability, and release metadata.
- Added Java 21 and Java 25 CI regression matrix before the release build is allowed to run.
- Added final JAR verification for embedded `plugin.yml` version/main/API baseline.
- Added final JAR verification that all required public API v1 classes are present.
- Added Java 21 classfile verification (major version 65).
- Added checks preventing Bukkit/Paper and PlaceholderAPI provided dependencies from being bundled into the production JAR.
- Added `jdeps` verification to reject NMS/CraftBukkit bytecode dependencies.
- Added explicit Maven release channel metadata. `candidate` releases are published as GitHub Prereleases instead of Latest stable.
- Added `PRODUCTION-CANDIDATE.md` with the remaining live Paper smoke-test checklist required before v1.6.0 Production Stable.
- Preserved public API v1, commands, permissions, placeholders, config behavior, and `stats.yml` schema version 3.
- Existing v1.2.0-v1.5.0 configuration/statistics data remains compatible.

## 1.5.0 Universal — Public API Update

- Added a stable read-only `MenkiAfkAPI` service registered through Bukkit `ServicesManager`.
- Added `MenkiAfkAPI.get()` convenience lookup for consuming plugins after MENKIAFK has enabled.
- Added immutable `AfkSessionSnapshot` and `AfkStatisticsSnapshot` public data models.
- Added public `AfkSessionType` with `MANUAL` and `AUTO` values.
- Added `PlayerEnterAfkEvent`, fired after AFK state has been committed.
- Added `PlayerLeaveAfkEvent`, fired after AFK state has been removed during normal runtime, including AFK quit/kick cleanup.
- Public lifecycle events are informational and intentionally not cancellable.
- Plugin/server disable does not emit leave events; shutdown only finalizes statistics and clears runtime state.
- Added `API.md` with dependency, ServicesManager, statistics, session, and event examples.
- Public API is read-only: no force-AFK/force-return mutation methods and no raw internal managers/YAML are exposed.
- No new scheduler, database, packet library, NMS access, GUI framework, economy dependency, or server-specific integration was added.
- `stats.yml` remains schema v3 and compatible with existing v1.2.0-v1.4.1 data.
- Preserved the Paper 1.21.11 -> 26.2 universal compatibility target and Java 21 bytecode baseline.

## 1.4.1 Universal — Stability Patch

- Hardened async chat handling so a delayed main-thread continuation cannot update AFK state after the player has disconnected.
- Fixed external namespaced AFK commands such as `/essentials:afk` being incorrectly treated as MENKIAFK's own toggle for activity handling.
- Added crash-safer `stats.yml` writes using a sibling temporary file plus atomic replace when supported by the filesystem.
- Added corrupt YAML protection: unreadable `stats.yml` is quarantined instead of being silently overwritten; if quarantine fails, writes are blocked for that server session to protect the original file.
- Added downgrade protection: if `stats.yml` uses a schema newer than MENKIAFK supports, known fields are read best-effort but writes are blocked so the newer format is not overwritten.
- Added safe normalization for inconsistent session counters and longest-session values loaded from `stats.yml`.
- Added `Stats I/O: OK/BLOCKED` to `/menkiafk status`; failed saves also surface as an unhealthy I/O state until a later save succeeds.
- `/menkiafk reload` checkpoints statistics before applying new timezone/retention/autosave settings.
- Shutdown logging no longer claims statistics were saved when persistence had been blocked for data protection.
- No feature expansion, new scheduler, database, GUI, packet library, NMS access, economy dependency, or server-specific integration was added.
- Preserved `stats.yml` schema v3 and backward compatibility with v1.2.0/v1.3.0/v1.4.0 data.
- Preserved the Paper 1.21.11 -> 26.2 universal compatibility target and Java 21 bytecode baseline.

## 1.4.0 Universal — Utility & Configuration Update

- Added `manual-afk.require-reason` to make manual AFK reasons optional or required.
- Added `manual-afk.default-reason` for `/afk` without arguments when reasons are optional.
- Added `menki.afk.silent` permission to suppress the player's AFK and return broadcasts without changing AFK state or statistics.
- `menki.afk.silent` defaults to false to avoid silently changing existing server broadcast behavior.
- Added persistent `last-afk-at` tracking and displayed it in `/afkstats`.
- Added `%menkiafk_last_afk%` PlaceholderAPI placeholder.
- Added numeric `%menkiafk_stats_total_seconds%`, `%menkiafk_stats_total_minutes%`, and `%menkiafk_stats_total_hours%` placeholders.
- Upgraded `stats.yml` to schema version 3 while preserving v1.2.0/v1.3.0 data compatibility.
- Existing older statistics without `last-afk-at` remain valid and gain the field after future AFK activity.
- No new scheduler, database, GUI framework, packet library, economy dependency, or server-specific integration was added.
- Preserved the Paper 1.21.11 -> 26.2 universal compatibility target and Java 21 bytecode baseline.

## 1.3.0 Universal — Lightweight QoL Update

- Added `/afklist` to show currently online AFK players with duration, type, and reason.
- Added configurable `afk-list.max-entries` to prevent chat flooding on large servers.
- Added `stats.minimum-session-seconds` so very short AFK toggles do not pollute persistent statistics or leaderboards.
- Added persistent Manual vs Auto AFK session counters.
- Added `%menkiafk_stats_manual_sessions%` and `%menkiafk_stats_auto_sessions%` PlaceholderAPI placeholders.
- Upgraded `stats.yml` to schema version 2 while keeping v1.2.0 statistics backward compatible.
- Existing v1.2.0 sessions are preserved as legacy/unclassified sessions because older data did not store completed-session AFK type.
- Added `menki.afk.list` permission, defaulting to true.
- No new scheduler, database, GUI framework, packet library, economy dependency, or server-specific integration was added.
- Runtime AFK detection remains RAM-based and movement/chat hot paths remain free of statistics disk writes.
- Preserved the Paper 1.21.11 -> 26.2 universal compatibility target and Java 21 bytecode baseline.

## 1.2.0 Universal

- Added standalone persistent AFK statistics in `plugins/MENKIAFK/stats.yml`.
- Added `/afkstats [player]` with today, current week, total AFK time, session count, and longest session.
- Added `/afktop [total|today|week|longest|sessions] [page]` and `/afkleaderboard` alias.
- Added live inclusion of active AFK sessions in statistics and leaderboards.
- Added autosave checkpointing for active AFK sessions to reduce stat loss after an unexpected server stop.
- Added configurable statistics timezone, daily history retention, autosave interval, and leaderboard page size.
- Added `%menkiafk_stats_today%`, `%menkiafk_stats_week%`, `%menkiafk_stats_total%`, `%menkiafk_stats_sessions%`, and `%menkiafk_stats_longest%` PlaceholderAPI placeholders.
- Added `/menkiafk resetstats <player>` for administrators.
- Statistics remain independent from Vault, economy plugins, crate plugins, AFK worlds, external databases, or any specific server configuration.
- Runtime AFK/session detection remains RAM-based and no statistics writes occur in movement/chat hot paths.
- Leaderboard sorting remains command-driven instead of being evaluated continuously by placeholders.
- Preserved the Paper 1.21.11 -> 26.2 universal compatibility target and Java 21 bytecode baseline.

## 1.1.0 Universal

- Repackaged as a single compatibility-oriented build for Paper 1.21.11 through 26.2.
- Keeps `api-version: 1.21.11` as the minimum server API.
- Keeps Java 21 bytecode to support the 1.21.11 baseline while remaining runnable on Java 25.
- Build remains API-only: no NMS, CraftBukkit internals, Mojang-mapped internals, or reobfuscation dependency.
- Preserves PlaceholderAPI soft dependency and EssentialsX-safe `/afk` override behavior.
- Preserves v1.0.0 config keys and runtime-only/TPS-friendly AFK data model.
