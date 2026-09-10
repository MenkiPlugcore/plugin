# MENKIESTESParty API Compatibility Policy

This document applies to the public API introduced in MENKIESTESParty v1.4.0 and hardened in v1.4.1.

## Current contract

- Plugin line: `1.4.x`
- Public API: `MenkiPartyAPI.API_VERSION = "1.0"`
- Provider discovery: Bukkit `ServicesManager`
- State model: immutable snapshots
- Events: post-state and non-cancellable

Consumers should depend only on classes under:

```text
id.cadera.menkiestesparty.api
id.cadera.menkiestesparty.api.event
```

Internal managers, YAML paths and storage classes are not public API.

## Compatibility guarantee for API v1

Within API v1, MENKIESTESParty will avoid removing or changing the signature of existing public methods and record components. Additive changes may be introduced when they remain source/binary compatible.

A future intentional breaking change must use a new API major version instead of silently changing the v1 contract.

## CI contract guard

`MenkiPartyApiContractTest` pins:

- `API_VERSION`
- public abstract method names, parameter types and return types
- snapshot record component order/names/types
- defensive copy behavior of Party member lists
- bounded Project percentage behavior

The regular Gradle `build` runs these tests. An accidental API contract change should therefore fail CI before a release artifact is accepted.

## Runtime verification

Administrators can run:

```text
/partyapi status
/partyapi verify
```

`/partyapi verify` checks the API version, method surface, snapshot record types, event classes, Bukkit service registration and a basic API read smoke test.

With the default setting:

```yaml
developer:
  hardening:
    fail-closed-on-contract-error: true
```

an unhealthy public API provider is removed from Bukkit `ServicesManager`. Core Party gameplay remains enabled.

## Event de-duplication

v1.4.1 fingerprints each observed state transition before firing the public Bukkit event. Identical fingerprints inside the configured de-duplication window are suppressed.

```yaml
developer:
  hardening:
    event-dedup-window-ms: 5000
```

Events remain post-state and non-cancellable.

## Reward idempotency

Developer Reward triggers use persistent receipt identities in `interactions.yml`. A receipt is reserved and flushed before external side effects run. This is an at-most-once strategy designed to prevent duplicate Vault deposits or console commands during abnormal reload/crash/tick conditions.

Receipts can be inspected indirectly with `/partyapi status`, which shows the current receipt count. Detailed data remains an internal storage concern and should not be read by consumer plugins.
