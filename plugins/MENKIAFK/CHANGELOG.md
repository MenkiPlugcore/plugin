# Changelog

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
