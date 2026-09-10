# MENKIESTESParty Social & Party Identity

Applies to **MENKIESTESParty v1.7.0**.

This layer adds player-facing Party profiles without replacing the existing Dynamic Party Identity from v1.2.x. Dynamic Identity is still derived automatically from Party activity; Social Identity is the customizable public profile around that gameplay identity.

## Commands

```text
/partyprofile [party|player]
/partysocial
/partytop [reputation|level|members|projects|activity|age] [page]
```

Nested forms are also available:

```text
/party profile [party|player]
/party social ...
/party badges
/party achievements
/party top [metric] [page]
/party leaderboard [metric] [page]
```

`/party top` remains reputation-first by default, preserving the familiar ranking behavior while allowing additional metrics.

## Dynamic Identity vs Social Identity

`/party identity` remains automatic and can produce identities such as `Warlike`, `Industrial`, `Agrarian`, `Project Focused`, `Balanced`, or `Developing` from the existing rolling activity window.

v1.7.0 does **not** let players manually choose that automatic identity. Instead, owners/officers customize the social profile fields around it.

## Public profile

A profile can show:

- Party display name and optional short tag;
- description;
- configured profile color and icon;
- Owner and online/member count;
- automatic Dynamic Identity;
- selected earned badge;
- Party Level and reputation;
- current Project and recruitment mode;
- unlocked achievement count;
- creation date and visibility;
- weighted activity total from the Dynamic Identity window.

View your Party or another Party:

```text
/party profile
/party profile PARADOX
/partyprofile SomePlayer
```

The resolver accepts a stable Party key, display name, or a stored Party member name.

## Editing a profile

Owner and Officer can edit normal profile presentation when they have `menkiestesparty.social.edit`:

```text
/party social description We focus on building and exploration
/party social description clear
/party social tag MNKI
/party social tag clear
/party social color AQUA
/party social icon NETHER_STAR
/party social badge established
/party social badge none
```

Descriptions are stored as plain text. Minecraft color/control codes and line breaks are stripped before storage, whitespace is normalized, and the configured length limit is enforced.

Tags are uppercase alphanumeric only and are globally unique by default. The default configuration allows 2-5 characters.

Color and icon values must be present in the allowlists in `social.yml`; arbitrary Materials or formatting codes are not accepted.

Profile edits have a short command cooldown to prevent accidental spam and excessive persistence writes.

## Privacy

Only the Party Owner can change visibility:

```text
/party social visibility public
/party social visibility private
```

A `PRIVATE` profile remains visible to its own members and to staff with the bypass permission. By default, private Parties are excluded from social leaderboards.

The setting is profile privacy, not gameplay invisibility: it does not hide the Party from core roster, War, Contract, administration, or server-side data systems that already require Party membership/state.

## Achievements and active badge

```text
/party social achievements
/party badges
```

Achievements are configuration-driven. Default v1.7.0 achievements cover:

- Party Level;
- reputation;
- member count;
- completed Projects;
- Party age;
- weighted Dynamic Identity activity.

Achievement unlocks are permanent once recorded at:

```text
parties.<party>.social.achievements.<id>.unlocked-at
```

A Party can select only an achievement it has already unlocked as its active badge. Falling below a metric later does not revoke a previously earned achievement.

Achievements are refreshed by relevant Party events and whenever profile/achievement commands are requested. There is no new social polling scheduler.

## Member social information

```text
/party social member
/party social member <player>
```

This command is restricted to the caller's own Party and reports:

- Party role;
- Division;
- joined date;
- lightweight activity status.

Activity status is derived on demand:

| Status | Default meaning |
| --- | --- |
| `ONLINE` | Player is currently online |
| `ACTIVE` | Last played within 7 days |
| `AWAY` | Last played within 30 days |
| `INACTIVE` | Older/unknown activity |

Existing members without a historical `joined-at` value fall back to the Party creation timestamp when available.

## Leaderboards

```text
/party top reputation
/party top level
/party top members
/party top projects
/party top activity
/party top age
```

Leaderboards are calculated only when requested. No permanent ranking cache or per-tick scan is introduced.

`activity` uses the same rolling data source that already feeds Dynamic Party Identity, so v1.7.0 does not duplicate block/mob activity tracking.

## PlaceholderAPI

v1.7.0 adds these placeholders for the requesting player's own Party:

```text
%mparty_social_tag%
%mparty_social_description%
%mparty_social_color%
%mparty_social_icon%
%mparty_social_visibility%
%mparty_social_badge%
%mparty_social_badge_id%
%mparty_social_profile_name%
%mparty_social_achievements%
%mparty_social_activity%
%mparty_member_since%
%mparty_member_status%
```

Existing `%mparty_identity%` and `%mparty_identity_raw%` continue to represent the automatic Dynamic Identity.

## Permissions

| Permission | Purpose |
| --- | --- |
| `menkiestesparty.social.view` | View social Party profiles |
| `menkiestesparty.social.edit` | Edit own Party profile; Party role rules still apply |
| `menkiestesparty.social.leaderboard` | View social leaderboards |
| `menkiestesparty.social.private.bypass` | View another Party marked PRIVATE |

The normal `menkiestesparty.admin` permission also bypasses social view/edit permission checks, but profile role rules still protect ordinary member edits.

## Configuration

`plugins/MENKIESTESParty/social.yml` controls:

- profile visibility defaults;
- description/tag limits;
- tag uniqueness;
- allowed colors/icons;
- privacy behavior in leaderboards;
- member activity windows;
- leaderboard page size;
- achievement definitions.

The file is merged with bundled defaults when the plugin starts/reloads, preserving existing configured values while adding future missing keys.

## Storage and performance

Social profile data is stored under the existing Party record:

```text
parties.<party>.social.*
```

`social.yml` is configuration only. There is no sixth persistent storage document and no new SQL table. YAML, SQLite and MySQL continue to use the same v1.5.x storage abstraction.

v1.7.0 adds no social per-tick task. Expensive cross-Party work such as leaderboard ranking or unique-tag validation runs only when the corresponding command is used. Normal social edits use the existing coalesced persistence path.

## Web panel

A web inspector/editor is **not** part of v1.7.0. It remains deferred until after the v2.0 architecture milestone.
