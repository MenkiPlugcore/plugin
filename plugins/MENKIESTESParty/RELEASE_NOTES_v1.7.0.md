# MENKIESTESParty v1.7.0 — Social & Party Identity Update

Release date: 2026-09-10

## Added

- Player-facing Social Party Profiles through `/partyprofile [party|player]` and `/party profile`.
- `/partysocial` / `/party social` profile customization for description, short tag, color, icon, visibility and active badge.
- Public/private Party profile visibility with own-Party/staff access to private profiles.
- Unique uppercase alphanumeric Party tags with configurable limits.
- Configurable profile color and icon allowlists.
- Configuration-driven permanent Party achievements.
- Earned achievement selection as the active Party badge.
- `/party social member [player]` with role, Division, join date and lightweight activity status.
- `/partytop` and `/party top` leaderboards for reputation, level, members, completed Projects, Dynamic Identity activity and Party age.
- `social.yml` for profile/privacy/achievement/leaderboard configuration.
- New PlaceholderAPI values for social profile fields, achievements, activity and member status.
- `SOCIAL_IDENTITY.md` player/admin documentation.
- Bukkit-free `SocialIdentityPolicy` validation/scoring rules with regression tests.

## Dynamic Identity compatibility

Dynamic Party Identity from v1.2.x remains automatic. Warlike/Industrial/Agrarian/Project Focused/Balanced/Developing continues to be derived from Party activity and cannot be manually selected through the social profile editor.

Social Identity is a separate presentation layer around that existing automatic identity.

## Privacy and safety

- `PRIVATE` profiles remain visible to their own members and authorized staff.
- Private profiles are excluded from social leaderboards by default.
- Description formatting/color control codes are stripped before persistence.
- Party tags are validated and globally unique by default.
- Colors and icons are allowlist-only.
- Only the Party Owner can change profile visibility.
- Owner/Officer role checks remain required for normal profile edits.
- A badge can only be selected after its achievement has been unlocked.
- Achievement unlocks are permanent after being recorded.
- Profile edits use a short configurable cooldown to avoid accidental write spam.

## Performance

- No new social per-tick scheduler is introduced.
- Leaderboards are calculated only when requested.
- Unique-tag scans run only when a tag is changed.
- Achievement refresh is event/on-demand driven.
- Dynamic Identity activity data is reused instead of introducing duplicate gameplay counters.
- Social data remains inside the existing Party document at `parties.<party>.social.*`.
- `social.yml` is configuration only; no new storage table/document is required.

## Compatibility

- Public `MenkiPartyAPI.API_VERSION` remains **1.0**.
- Existing v1.6.2 Party, interaction, administration and storage data remains compatible.
- Existing member `joined-at` data is reused for member profiles; older missing values can fall back to Party creation time.
- YAML remains the default local backend.
- SQLite/MySQL remain optional.
- No new required runtime dependency or external service is introduced.
- Java target remains Java 21 / Paper 1.21.11.
- Java 25 runtime compatibility and MySQL 8.4 integration remain production release gates.
- Full Folia support is still not claimed.
- Web Inspector/Web Editor remains deferred until after v2.0.

## Documentation

- `README.md` updated for v1.7.0.
- `CHANGELOG.md` updated with v1.7.0.
- `SOCIAL_IDENTITY.md` documents profiles, privacy, achievements, member status, leaderboards, PlaceholderAPI and configuration.
- Existing `ADMINISTRATION.md`, `STORAGE.md`, `API.md` and `API_COMPATIBILITY.md` remain applicable.
- This release-note file is required by the automatic GitHub Release job.

## Upgrade

Stop the server, replace the previous MENKIESTESParty JAR, keep the existing `plugins/MENKIESTESParty/` directory, and perform a full start. `social.yml` is created/merged automatically. No storage migration is required.
