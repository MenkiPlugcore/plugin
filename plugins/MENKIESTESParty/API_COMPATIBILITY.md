# MENKIESTESParty API Compatibility Policy

MENKIESTESParty exposes versioned public interfaces through Bukkit `ServicesManager`. Consumer plugins should depend only on public types below:

```text
id.cadera.menkiestesparty.api
id.cadera.menkiestesparty.api.v2
id.cadera.menkiestesparty.api.event
```

Internal managers, YAML paths, storage classes and architecture transport implementations are not public API.

## API v1 — compatibility line

The original service introduced in v1.4 remains:

```text
id.cadera.menkiestesparty.api.MenkiPartyAPI
MenkiPartyAPI.API_VERSION = "1.0"
```

MENKIESTESParty v2.0.0 does not remove or change the signature/record layout of this interface. Existing API v1 consumers can continue using its Bukkit service registration.

Within API v1, existing public methods and record components will not intentionally be removed/reordered/retyped. A breaking change will never be silently shipped under `API_VERSION = "1.0"`.

## API v2 — current architecture API

v2.0.0 adds:

```text
id.cadera.menkiestesparty.api.v2.MenkiPartyAPIv2
MenkiPartyAPIv2.API_VERSION = "2.0"
```

API v2 adds architecture/runtime state, richer member/social snapshots and Party revisions without changing API v1.

Compatible additive changes may be made within API v2. A future intentional breaking contract must use a new API major version rather than silently changing v2 signatures.

## Service coexistence

API v1 and v2 are separate Bukkit services. Consumers should request the exact interface they support.

API v2 registration requires the v2 architecture/document schema checks to be healthy. If API v2 is not registered, this does not intentionally disable Party gameplay or remove a healthy API v1 service.

API v1 retains the hardening/fail-closed behavior introduced in v1.4.1: a failed API v1 compatibility self-check can unregister only that public service while Party core remains enabled.

## Event model

Existing `id.cadera.menkiestesparty.api.event` events remain post-state and non-cancellable. The event bridge observes committed Party state rather than exposing mutable YAML objects.

Architecture v2 uses this same event stream for revision envelopes. It does not replace the public Bukkit events.

Because the event bridge is snapshot-driven, disabling `developer.events.enabled` disables those public transition events and therefore also stops architecture revision-envelope advancement.

## Reward/idempotency compatibility

Developer Reward triggers continue using persistent receipt identities with durable reservation before external side effects. The v2 API provider delegates controlled mutations to the existing validated core instead of introducing a second mutation pipeline.

## CI contract guards

The regular Gradle build protects both major API contracts:

- API v1 remains `1.0`.
- API v2 remains `2.0`.
- v1 public interface/records remain available.
- v2 Party snapshot includes revision + Social Profile.
- v2 collection snapshots are defensive copies.
- Document Schema/network envelope pure-Java contracts are regression tested.
- release metadata/documentation must match v2.0.0.

The production JAR verifier also requires both API class files and Architecture v2 classes/resources before the artifact can advance to runtime/MySQL/release gates.

## Runtime verification

Legacy API diagnostics remain:

```text
/partyapi status
/partyapi verify
```

Architecture/API v2 dependencies can be checked with:

```text
/partyarchitecture status
/partyarchitecture verify
```

For programmatic API v2 runtime details, use `MenkiPartyAPIv2.runtime()`.

## Placeholder compatibility

`%mparty_api_version%` intentionally remains API v1 (`1.0`) so existing scoreboards/configs do not change meaning.

v2 adds explicit placeholders:

```text
%mparty_api_v1_version%
%mparty_api_v2_version%
```

See `API_V2.md` for the v2 surface and `ARCHITECTURE.md` for schema/network semantics.
