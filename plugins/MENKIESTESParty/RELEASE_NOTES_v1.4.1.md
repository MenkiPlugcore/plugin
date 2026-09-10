# MENKIESTESParty v1.4.1 — API Hardening & Compatibility Update

## Added

- Runtime public API v1.0 contract self-check.
- `/partyapi status` for API/integration/reward diagnostics.
- `/partyapi verify` for full runtime contract verification.
- Fail-closed API service protection: only the public API service is unregistered when the contract is unhealthy; Party core stays enabled.
- Event fingerprint de-duplication for all v1.4 public post-state events.
- Persistent at-most-once reward receipts stored in `interactions.yml`.
- Reward command safety limits for maximum commands per trigger and maximum command length.
- Automatic reward receipt retention/pruning.
- PlaceholderAPI `%mparty_api_health%`.
- JUnit API contract regression tests executed by the normal Gradle `build` lifecycle.

## API compatibility

`MenkiPartyAPI.API_VERSION` remains `1.0`.

No existing v1.4.0 public API method or snapshot field is intentionally removed or renamed. CI now checks the exact method contract and record component shapes so accidental breaking changes fail the build.

## Reward safety

v1.4.1 uses persistent at-most-once receipts for configured Developer Reward triggers:

- Party Level Up — one receipt per Party level.
- Project Complete — one receipt per project completion timestamp.
- Contract Complete — one receipt per Contract ID.
- Party War Win — one receipt per War history ID.

The receipt is synchronously flushed before Vault/console side effects are dispatched. This favors duplicate prevention during crash/reload edge cases. A receipt can therefore show `COMPLETED_WITH_ERRORS` when an external reward action failed; it is not automatically replayed.

## New defaults

```yaml
developer:
  hardening:
    enabled: true
    fail-closed-on-contract-error: true
    log-startup-diagnostics: true
    event-dedup-window-ms: 5000
    reward-receipt-retention-days: 90
    rewards:
      max-commands-per-trigger: 20
      max-command-length: 512
```

## Compatibility

- Existing v1.4.0 Party, interaction, notification and reward configuration stays valid.
- Existing `parties.yml`, `wars.yml`, `season.yml` and `interactions.yml` are reused.
- No new required plugin dependency or database is introduced.
- Vault and PlaceholderAPI remain optional.
