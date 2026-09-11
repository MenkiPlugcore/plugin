# MENKIESTESParty v2.1.0 — CdrJobs Five Paths Integration

Release date: 2026-09-11

## Added

- Optional CdrJobs Public API v2 integration with no hard compile/runtime dependency.
- Event-driven Party contribution bridge using CdrJobs `ProfessionActionEvent`.
- Authoritative Daily/Weekly mode to prevent double-count with legacy Bukkit listeners.
- New Lumberjack and Fisher Party contribution paths.
- Five Paths profession roster via `/party professions`.
- Five Paths Convergence via `/party convergence`.
- Default distinct-member Convergence rule: Miner + Farmer + Hunter + Lumberjack + Fisher specialist coverage.
- Configurable Party-only Convergence bonus for CdrJobs-powered Project progress.
- Five CdrJobs-specific Party Project definitions using `cdr_*` project types.
- Runtime diagnostics via `/party cdrjobs` and admin `/party cdrjobs reload`.
- Dedicated `cdrjobs-integration.yml`.
- Bukkit-free unit tests for profession mapping, event amount safety and distinct Five Paths matching.

## Safety / compatibility

- MENKIESTESParty still starts and functions without CdrJobs.
- If CdrJobs is disabled at runtime, the bridge disconnects and standalone Party activity listeners become active again.
- No direct read/write of CdrJobs SQLite data.
- CdrJobs anti-exploit acceptance becomes the source of truth while authoritative mode is active.
- Existing legacy Party Projects remain compatible; new CdrJobs-powered Projects use separate `cdr_*` types.
- One player is never forced into one Party profession. CdrJobs multi-profession progression remains unchanged.
- Five Paths Convergence does not modify personal damage, economy, drops, CdrJobs XP or Fate Essence.
- MENKIESTESParty Public API v1 remains `1.0`.
- MENKIESTESParty Public API v2 remains `2.0`.
- Document Schema remains `2`.
- Storage backend protocol remains `1`.
- No destructive Party data migration.

## Default specialist rules

```yaml
profession-roster:
  specialist-min-level: 50
  specialist-min-mastery-tier: 0

five-paths-convergence:
  enabled: true
  require-distinct-members: true
  project-progress-bonus-percent: 10
```

With the default distinct-member rule, an all-profession player cannot satisfy Five Paths Convergence alone.

## Upgrade

1. Stop the server normally.
2. Install CdrJobs v1.6.0+ if Five Paths integration is desired.
3. Replace MENKIESTESParty with v2.1.0.
4. Keep the existing MENKIESTESParty data directory.
5. Start the server.
6. Run `/party cdrjobs`.
7. Run `/party professions` and `/party convergence`.
8. Verify one accepted activity increments Party progress exactly once.
9. Verify one CdrJobs-rejected exploit activity increments nothing.
10. Re-run `/partyapi verify`, `/partyarchitecture verify`, and `/partystorage verify`.

See [`CDRJOBS_INTEGRATION.md`](CDRJOBS_INTEGRATION.md) for the full integration contract and smoke-test matrix.
