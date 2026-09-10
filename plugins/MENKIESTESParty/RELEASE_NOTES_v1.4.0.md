# MENKIESTESParty v1.4.0 — Developer API & Integration Update

## Added

- Public `MenkiPartyAPI` v1.0 registered through Bukkit `ServicesManager`.
- Immutable Party, Member, Project, Contract and Relation snapshots.
- Controlled API write operations for Party XP and Party broadcasts.
- Post-state Bukkit events for Party lifecycle, member changes, level changes, Project completion, Contract status, Diplomacy relation/Trust changes and Party War state.
- Optional runtime Vault Economy hook with no hard Vault dependency.
- Configurable reward engine for Party level-up, Project completion, Contract completion and Party War victory.
- Console command reward placeholders.
- PlaceholderAPI additions for API/plugin version, owner, online members, raw identity, Project percent, unread inbox, Vault availability, dynamic relation and Trust lookups.
- `API.md` developer documentation with integration examples.
- v1.4.0 config migration.

## Architecture

External plugins receive immutable snapshots instead of references to internal YAML or manager objects. This keeps the public API independent from the current local-YAML storage implementation and prepares MENKIESTESParty for future storage backends.

Events are post-state and non-cancellable. Existing Party validation, rank capabilities, Contract limits, Party War membership locks and other safety rules remain authoritative.

## Optional Vault

Vault is detected at runtime through Bukkit ServicesManager using reflection. MENKIESTESParty still builds and runs without Vault.

All default Vault rewards are `0`.

## Reward defaults

All command lists are empty and all money values are zero by default, so upgrading to v1.4.0 does not change economy or reward balance.

## Compatibility

- Existing v1.3.2 Party and Interaction data is used directly.
- No database migration.
- No required dependency added.
- Paper 1.21.11 / Java 21 remains the build target.
