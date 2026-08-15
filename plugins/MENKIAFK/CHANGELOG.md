# Changelog

## 1.1.0 Universal

- Repackaged as a single compatibility-oriented build for Paper 1.21.11 through 26.2.
- Keeps `api-version: 1.21.11` as the minimum server API.
- Keeps Java 21 bytecode to support the 1.21.11 baseline while remaining runnable on Java 25.
- Build remains API-only: no NMS, CraftBukkit internals, Mojang-mapped internals, or reobfuscation dependency.
- Preserves PlaceholderAPI soft dependency and EssentialsX-safe `/afk` override behavior.
- Preserves v1.0.0 config keys and runtime-only/TPS-friendly AFK data model.
