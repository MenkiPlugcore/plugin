# MENKIESTESParty Public API v2

MENKIESTESParty v2.0.0 adds `MenkiPartyAPIv2` as a second Bukkit `ServicesManager` service.

The original `MenkiPartyAPI` remains available with:

```text
MenkiPartyAPI.API_VERSION = "1.0"
```

The new interface is:

```text
id.cadera.menkiestesparty.api.v2.MenkiPartyAPIv2
MenkiPartyAPIv2.API_VERSION = "2.0"
```

API v2 is additive. Existing plugins compiled against API v1 do not need to migrate merely because the server updates to MENKIESTESParty v2.0.0.

## Getting the service

```java
RegisteredServiceProvider<MenkiPartyAPIv2> registration =
        Bukkit.getServicesManager().getRegistration(MenkiPartyAPIv2.class);
if (registration == null) {
    return;
}
MenkiPartyAPIv2 api = registration.getProvider();
```

Consumers should depend only on public types under `id.cadera.menkiestesparty.api` / `api.v2`. Do not read MENKIESTESParty YAML files or internal managers directly.

## Runtime snapshot

`api.runtime()` exposes architecture state without exposing implementation objects:

- plugin version
- Document Schema version
- storage backend protocol version
- configured/active backend
- storage degraded/fallback state
- scheduler mode
- Folia detected/certified flags
- stable node id
- configured/active network mode
- distributed transport flag
- transport health
- network revision sequence
- envelopes emitted this boot

The `foliaCertified` field is `false` in v2.0.0.

## Party snapshot v2

The v2 Party snapshot keeps the core information available in v1 and adds:

- Party revision
- Social Party profile snapshot
- richer member snapshots

Social profile data contains description, tag, color, icon, visibility, active badge, unlocked achievement ids, and Dynamic Identity activity total.

Member snapshots add `joinedAt` and lightweight `activityStatus` while preserving UUID/name/role/division/online state.

## Revision semantics

`PartySnapshot.revision()` is a monotonic local revision signal for Party transitions observed through the existing public post-state event bridge.

It is intended for cache invalidation/version comparison in future network integrations. In v2.0.0 it is **not** a distributed-consensus version and must not be treated as a cross-server lock token.

If the server disables `developer.events.enabled`, revision envelopes stop advancing because the underlying event bridge is disabled.

## Read API

```text
partyKey(UUID)
party(UUID)
party(String)
parties()
member(UUID)
currentProject(String)
contracts(String)
relation(String, String)
runtime()
```

All exposed collections in public snapshots use defensive copies.

## Controlled mutations

API v2 keeps the controlled mutation surface:

```text
addPartyExperience(String, int, String)
broadcast(String, String)
hasCapability(UUID, String)
integrationAvailable(String)
```

These operations delegate to the existing validated core/API v1 implementation rather than bypassing Party rules or persistence semantics.

## Integration names

In addition to existing names such as `vault` and `placeholderapi`, v2 recognizes:

```text
api_v1
api_v2
network
network-foundation
distributed-network
redis
```

`network` means the v2 network contract exists. `distributed-network`/`redis` return false in the stock v2.0.0 LOCAL runtime because no distributed transport is bundled.

## Coexistence policy

MENKIESTESParty v2.0.0 registers API v1 and API v2 independently through Bukkit `ServicesManager`.

API v1 retains its existing runtime hardening/fail-closed behavior. API v2 is registered only when:

1. developer API support is enabled;
2. API v1 compatibility check is healthy;
3. Document Schema v2 architecture verification passes; and
4. the API v2 contract self-check passes.

Failure to register API v2 does not intentionally disable Party gameplay.

## Versioning policy

API v1 remains frozen-compatible for existing integrations. API v2 may receive additive compatible changes during the v2 plugin line.

A future intentional breaking API change must use a new public API major contract instead of silently replacing v1/v2 signatures.

See `API_COMPATIBILITY.md` for the complete compatibility policy and `ARCHITECTURE.md` for Document Schema/network details.
