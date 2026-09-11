# MENKIESTESParty × CdrJobs — Five Paths Integration

MENKIESTESParty v2.1.0 can consume CdrJobs Public API v2 without making CdrJobs a hard dependency.

## Runtime contract

- MENKIESTESParty stays fully usable when CdrJobs is missing or disabled.
- The bridge discovers `CdrJobsAPI` through Bukkit `ServicesManager` and requires API version 2 or newer.
- It never reads `cdrjobs.db` directly.
- `ProfessionActionEvent` is the authoritative contribution source while the bridge is healthy.
- If CdrJobs disappears at runtime, MENKIESTESParty immediately returns to its standalone Bukkit listeners.

## Why ProfessionActionEvent

CdrJobs emits `ProfessionActionEvent` only after its own activity validation. This lets Party progression inherit CdrJobs anti-exploit decisions instead of independently guessing whether an ore, crop, kill, log or fishing action should count.

With the default configuration, the old Party Bukkit listeners for Weekly/Daily profession activity are suspended while the bridge is connected. This prevents one action from being counted twice.

## Five Paths mapping

| CdrJobs | Party activity | Cdr-only Project type |
| --- | --- | --- |
| Miner | `mining` | `cdr_miner` |
| Farmer | `farmer` | `cdr_farmer` |
| Hunter | `hunter` | `cdr_hunter` |
| Lumberjack | `lumberjack` | `cdr_lumberjack` |
| Fisher | `fisher` | `cdr_fisher` |

Lumberjack and Fisher are new Party contribution paths introduced by this integration.

## Profession roster

Use:

```text
/party professions
```

The roster reads immutable CdrJobs API v2 snapshots and shows each member's strongest profession plus which professions currently qualify as Party Specialists.

Default specialist gate:

```yaml
profession-roster:
  specialist-min-level: 50
  specialist-min-mastery-tier: 0
```

A player is not assigned a permanent Party job. The same player can continue progressing all five CdrJobs professions normally.

## Five Paths Convergence

Use:

```text
/party convergence
```

Default rule:

```yaml
five-paths-convergence:
  enabled: true
  require-distinct-members: true
  project-progress-bonus-percent: 10
```

With `require-distinct-members: true`, five different Party members must be assignable to Miner, Farmer, Hunter, Lumberjack and Fisher specialist slots. A single maxed player cannot satisfy all five slots alone.

The default bonus applies only to CdrJobs-powered Party Project progress. It does not modify personal damage, drops, economy, CdrJobs XP or Fate Essence.

## CdrJobs-powered Party Projects

The integration ships five default definitions and merges them into `config.yml` only when they are absent:

- `fivepaths_miner` — Runebound Expedition
- `fivepaths_farmer` — Verdant Harvest
- `fivepaths_hunter` — Bloodfang Hunt
- `fivepaths_lumberjack` — Ironbark Timber Drive
- `fivepaths_fisher` — Tidebound Expedition

These use `cdr_*` types so they cannot accidentally receive progress from the older raw Bukkit Project listeners.

Existing legacy `mining`, `farmer` and `hunter` Projects remain compatible and continue using MENKIESTESParty's standalone listeners.

## Configuration

File:

```text
plugins/MENKIESTESParty/cdrjobs-integration.yml
```

Useful switches:

```yaml
integration:
  enabled: true
  minimum-api-version: 2

activity:
  feed-weekly-quests: true
  authoritative-weekly-quests: true
  feed-daily-missions: true
  authoritative-daily-missions: true
  feed-party-projects: true
```

If `authoritative-*` is disabled, the old Party listener is allowed to run alongside the bridge. This is intended only for controlled compatibility testing because overlapping activities may count twice.

## Diagnostics

```text
/party cdrjobs
/party cdrjobs reload
/party professions
/party convergence
```

`/party cdrjobs reload` requires `menkiestesparty.admin`.

## Recommended smoke test

1. Start with CdrJobs v1.6.0+ and MENKIESTESParty v2.1.0.
2. `/party cdrjobs` must report `HEALTHY` and API `2`.
3. Break one valid natural Miner ore. Weekly/Daily Mining must increase exactly once.
4. Break a CdrJobs-rejected placed ore. Party Mining must not increase.
5. Repeat equivalent accepted/rejected tests for Farmer, Hunter, Lumberjack and Fisher.
6. Start a `cdr_*` Project and verify only its matching profession advances it.
7. Build a roster with five distinct specialists and verify Convergence becomes `ACTIVE` after the configured cache window.
8. Disable/remove CdrJobs and restart. MENKIESTESParty must still enable and old Mining/Farmer/Hunter fallback tracking must work.
9. Re-enable CdrJobs and confirm the bridge reconnects without resetting Party data.
10. Re-run Party War, Contracts, GUI, storage and public API regression tests.

## Compatibility

The integration does not change MENKIESTESParty public API v1 (`1.0`) or v2 (`2.0`), Document Schema v2, storage backend protocol v1, or network architecture defaults.
