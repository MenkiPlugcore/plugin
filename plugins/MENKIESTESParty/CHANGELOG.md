# Changelog

All notable MENKIESTESParty changes are documented here. Releases remain standalone/public-plugin oriented and preserve the custom MENKIESTES/CADERA project licensing while respecting bundled third-party notices.

## 1.6.1 - 2026-09-10

Administration Stability & Safety Patch.

### Added

- Per-staff 6-character confirmation token for dangerous admin actions.
- Exactly-once confirmation consumption with replay/concurrency protection.
- Configurable wrong-token attempt cap and automatic cancellation.
- Pending confirmation cleanup when staff disconnects.
- Failed/cancelled/expired confirmation outcomes in the bounded audit log.
- `/partyadmin health` administration health summary.
- `/partyadmin search <query> [page]` Party search.
- `/partyadmin inspect <party> verbose` cross-index/integrity diagnostics.
- Verified repair post-check showing integrity warnings before and after repair.
- Unique export/archive filenames to prevent accidental overwrite.
- YAML snapshot content verification and SHA-256 sidecars.
- Independent verified pre-disband safety snapshot before the v1.6.0 archive-first disband path executes.
- JUnit regression tests for confirmation exactly-once behavior, token failure/expiry, archive collision protection, checksum tamper detection, metadata and API compatibility.

### Safety / Compatibility

- Dangerous mutations still reuse the v1.6.0 validated administration implementation after the v1.6.1 safety gate succeeds.
- Transfer Owner is rejected before staging if the target is not already a Party member.
- Reset Contract is rejected before staging unless the Contract is ACTIVE.
- Disband is rejected before staging while Party War roster locking is active.
- `MenkiPartyAPI.API_VERSION` remains `1.0`.
- YAML remains the default local backend; SQLite/MySQL remain optional.
- Java 21 build, MySQL 8.4 integration and Java 25 runtime probes remain release gates.
- GitHub Release publication remains automatic after all production gates succeed.

## 1.6.0 - 2026-09-10

Administration & Moderation Update.

### Added

- `/partyadmin` (`/padmin`, `/mpartyadmin`) administration command and inventory browser.
- Nested `/party admin ...` route.
- Party list/inspect for player GUI and console.
- Staff force join/remove.
- Owner transfer with confirmation.
- Display-only Party rename while preserving the stable Party key.
- Reversible freeze/unfreeze with saved recruitment mode.
- Party XP/Rep set/add/remove.
- Conservative owner/member/player-index repair.
- Active Project progress reset.
- Active Contract progress reset.
- Support export and archive YAML snapshots.
- Archive-first admin disband.
- Bounded administration audit log in `interactions.yml`.
- Dedicated `administration.yml`.
- Granular `menkiestesparty.admin.*` permissions.
- `ADMINISTRATION.md` and `RELEASE_NOTES_v1.6.0.md`.

### Safety / Compatibility

- Freeze is enforced in core roster operations including pending invite acceptance.
- Frozen Party interaction mutations are blocked from commands and stale GUI actions.
- Staff roster mutations are refused while Party War is PREPARE/ACTIVE.
- Dangerous operations require `/partyadmin confirm` within a configurable timeout.
- Admin disband aborts if the pre-disband archive cannot be created.
- `MenkiPartyAPI.API_VERSION` remains `1.0`.
- YAML, SQLite and MySQL backends remain compatible and SQL remains optional.

## 1.5.1 - 2026-09-10

Storage Stability & Migration Hardening.

### Added

- Verified live `/partystorage migrate <YAML|SQLITE|MYSQL>` flow.
- Pre-migration snapshots and SHA-256 document verification.
- Migration journal and rollback metadata.
- `/partystorage rollback` with latest-state preservation.
- SQL retry/reconnect policy and periodic backend health checks.
- Recovery from SQL fallback through resync + checksum verification.
- Storage queue/backlog diagnostics.
- Backup rotation.
- Unclean-shutdown marker/recovery snapshot.
- Schema/version marker and default merge for storage/messages files.
- MySQL 8.4 service-container integration test in CI.

### Compatibility

- `MenkiPartyAPI.API_VERSION` remains `1.0`.
- YAML remains the production-safe default.
- Java 21 build and Java 25 runtime probes remain release gates.

## 1.5.0 - 2026-09-10

Storage & Compatibility Update.

### Added

- Storage abstraction behind the existing in-memory Party documents.
- `YAML`, `SQLITE`, and `MYSQL` backends.
- Self-contained SQLite and MySQL JDBC drivers.
- Automatic YAML -> SQL import when the SQL store is empty.
- YAML warm-mirror fallback while SQL is primary.
- Single-worker async persistence with write coalescing.
- Durable synchronous flush path for reward receipts and shutdown.
- Atomic YAML writes.
- `messages.yml` localization foundation.
- `/partystorage status|verify|flush|backup`.
- Scheduler compatibility boundary and Folia runtime detection.
- `STORAGE.md`, third-party notices and release notes.

### Compatibility

- Full Folia support is not claimed; experimental mode remains opt-in.
- Public API remains `1.0`.
- Existing v1.4.1 YAML installs upgrade without forced storage migration.

## 1.4.1 - 2026-09-10

API Hardening & Compatibility Update.

### Added

- Runtime API contract self-check.
- `/partyapi status|verify` diagnostics.
- Fail-closed behavior limited to the public API service when its contract is unhealthy.
- Event transition de-duplication.
- Persistent reward receipts with at-most-once semantics.
- Reward command count/length guards.
- `%mparty_api_health%`.
- CI regression tests locking API v1.0 method/snapshot contracts.
- `API_COMPATIBILITY.md`.

### Compatibility

- `MenkiPartyAPI.API_VERSION` remains `1.0`; no intentional public signature break.

## 1.4.0 - 2026-09-10

Developer API & Integration Update.

### Added

- Public `MenkiPartyAPI v1.0` through Bukkit `ServicesManager`.
- Immutable Party, Member, Project, Contract and Relation snapshots.
- Controlled Party XP/broadcast API operations.
- Post-state Bukkit events for Party create/disband/member/level/project/contract/relation/war transitions.
- Optional Vault Economy hook without a hard dependency.
- Configurable command/Vault reward engine with zero/empty safe defaults.
- Additional PlaceholderAPI values for API/plugin/owner/online/project/inbox/Vault/relation/trust data.
- `API.md` developer guide.

### Safety

- Public events are informational/post-state and do not bypass Party validation.
- Reward integrations remain disabled by zero/empty defaults until explicitly configured.

## 1.3.2 - 2026-09-10

Interaction Stability & UX Update.

### Added

- Persistent notification inbox and unread counter.
- Recent Party Activity feed with bounded retention.
- Login notification summary.
- Interaction command anti-spam.
- Conservative orphan/index integrity repair.
- `/partydebug <party>` diagnostics.
- Interaction snapshot detection on a slow cadence instead of gameplay hot paths.

### Changed

- Notification/activity queues are capped to prevent unbounded `interactions.yml` growth.
- Interaction state changes refresh more consistently after mutations.

## 1.3.1 - 2026-09-10

Interaction GUI Update.

### Added

- Interaction Hub in the main Party GUI.
- Paginated Party Browser and Party detail view.
- Contract list/detail and target -> type -> goal creation wizard.
- Contract accept/deny/cancel/abandon confirmations.
- Diplomacy list/detail and Alliance/Rival/Neutral actions.
- Recruitment mode GUI.
- Incoming/outgoing Applications GUI.
- Rank Capabilities GUI.
- Granular Interaction GUI permissions.

### Changed

- Zero-argument Interaction commands open GUI screens while argument forms retain command behavior.
- All GUI mutations delegate to the existing InteractionManager rules.

## 1.3.0 - 2026-09-10

Party Interaction Update.

### Added

- Party Contracts with mining/hunter/farmer objectives.
- Contract proposal/accept/deny/cancel/abandon lifecycle, expiry, cooldown, limits and anti-abuse.
- Diplomacy relations: `NEUTRAL`, `ALLY`, `RIVAL`.
- Symmetric Trust Score from `-100` to `100`.
- Recruitment modes: `OPEN`, `APPLICATION`, `CLOSED`.
- Join Applications with expiry and optional note.
- Configurable interaction rank capabilities.
- Dedicated `interactions.yml`.
- Interaction PlaceholderAPI values.

### Safety

- Automatic Contract Party XP defaults to `0`.
- Party War roster locks and Party member limits are respected.
- No database/economy/proxy dependency is required.

## 1.2.2 - 2026-09-10

GUI & Safety Polish.

### Added

- Pagination for Projects, Skills, Divisions and member assignment.
- Project cancel and Skill unlock confirmations.
- Visual progress bars.
- Clickable Party Level screen.
- Granular Progression GUI permissions.

## 1.2.1 - 2026-09-10

Progression GUI Update.

### Added

- Progression Hub.
- Party Profile, Projects, Skill Tree, Divisions and Identity inventory screens.
- Division information in the member roster.

### Changed

- Removed the obsolete Party Reward Hall button from the main Party GUI.

## 1.2.0 - 2026-09-10

Party Progression Update.

### Added

- Party Projects.
- Party Skill Tree.
- Party Divisions.
- Dynamic Party Identity.
- Progression commands and PlaceholderAPI values.

### Compatibility

- Existing Weekly Quest, Daily Mission, Party Relic, Party War and Season data remain valid.

## 1.1.0 - 2026-09-09

### Added

- Daily shared Party missions.

### Changed

- Party War automatic reward simplified to Party XP.
- Custom/item War rewards are handed out manually by server admins.

### Removed

- Party Hall reward system.
- Automatic War Chest reward flow.

## 1.0.4

- Party Level 1 member cap changed to 5.
- Party War, Weekly Quest, Party Relic, Party GUI and local YAML storage retained.
