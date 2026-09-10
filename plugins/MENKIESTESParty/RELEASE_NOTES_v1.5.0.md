# MENKIESTESParty v1.5.0 — Storage & Compatibility Update

Production-focused persistence and runtime compatibility release.

## Added

- Storage backend abstraction preserving existing in-memory YAML manager contracts.
- `YAML` default backend.
- Self-contained `SQLite` backend.
- Self-contained `MySQL` backend.
- One-time YAML -> SQL import when the configured SQL store is empty.
- Missing-document SQL migration from YAML.
- SQL -> YAML warm mirror backup.
- Runtime SQL failure -> YAML fallback option.
- Coalesced single-worker async persistence for normal dirty saves.
- Durable synchronous persistence for reward receipts and shutdown.
- Atomic YAML file replacement.
- `/partystorage status|verify|flush|backup` diagnostics.
- `messages.yml` localization foundation for v1.5+ surfaces while preserving legacy message compatibility.
- Scheduler compatibility boundary + Folia detection/fail-safe.
- YAML + SQLite backend contract tests.
- JDBC driver packaging test.
- Java 21 build/test gate plus Java 25 runtime compatibility probe.
- Production JAR verification for plugin version, public API v1.0 and JDBC drivers.

## Compatibility

- Paper target remains 1.21.11.
- Production classfile target remains Java 21.
- Java 21 build/test and Java 25 runtime probe.
- `MenkiPartyAPI.API_VERSION` remains `1.0`.
- Existing Party/progression/interaction data needs no reset.
- Default `storage.backend: YAML` makes upgrading from v1.4.1 non-destructive.

## Folia note

The scheduler boundary is groundwork only. Full region-thread safety is not certified, so Folia experimental mode remains disabled by default.
