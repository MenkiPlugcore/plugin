# MENKIESTESParty v1.3.2 — Interaction Stability & UX Update

## Added

- Persistent per-player Party Notification Inbox stored in `interactions.yml`.
- Unread notification counter injected into the existing Party Interaction Hub.
- Offline notification summary on player join.
- Short-lived Recent Party Activity feed for Contracts, Diplomacy, Applications, Recruitment, Project completions and roster changes.
- `/partyinbox` (`/pinbox`) and `/partyactivity` (`/pactivity`).
- Admin diagnostics through `/partydebug <party>` and nested `/party debug <party>`.
- Conservative integrity checks for orphan player indexes, applications and diplomacy state.
- Command mutation rate limiting for Interaction actions.
- Configurable queue size, retention and anti-spam values.

## Stability behavior

- State snapshots run every 5 seconds, not on hot gameplay loops.
- Notification/activity queues are bounded and pruned automatically.
- Existing `InteractionManager` remains the authority for Contract cooldowns, Party limits, War membership locks and rank capability validation.
- Ambiguous data is never guessed or force-repaired. `/party debug <party>` surfaces integrity warnings for manual review.
- Existing v1.3.1 data is used directly; no reset or database migration is required.

## Defaults

```yaml
stability:
  enabled: true
  notifications:
    max-per-player: 30
    retention-days: 14
    join-summary: true
  activity:
    max-per-party: 30
    retention-days: 7
  anti-spam:
    command-action-ms: 750
  integrity:
    safe-repair-on-startup: true
    safe-repair-periodic: true
```
