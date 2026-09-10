# MENKIESTESParty v1.3.0

Native modular Paper Party/Guild framework by CADERA. Designed for Paper 1.21.11 / Java 21, local YAML storage, and no required database.

## v1.3.0 — Party Interaction Update

v1.3.0 adds interaction between Parties without tying the plugin to a specific server network, economy plugin, or database.

New systems:

- Party Contracts
- Diplomacy + Trust Score
- Recruitment + Join Applications
- Configurable rank capabilities for interaction actions
- Dedicated `interactions.yml` storage
- New PlaceholderAPI values

All v1.3 modules are optional.

## Party Contracts

One Party can offer an objective to another Party. Built-in objective types:

- `mining`
- `hunter`
- `farmer`

Flow:

1. Issuer creates a Contract.
2. Target Party receives a PENDING proposal.
3. A permitted rank accepts or denies it.
4. After acceptance, activity from the target Party advances the Contract.
5. Completion updates Contract statistics and Trust Score.

Commands:

```text
/party contract
/party contract info <id>
/party contract create <party> <mining|hunter|farmer> <goal>
/party contract accept <id>
/party contract deny <id>
/party contract cancel <id>
/party contract abandon <id>
```

Direct alias: `/partycontract` or `/pcontract`.

Contract protection includes configurable goal limits, proposal expiry, active deadline, per-pair cooldown, active/open limits, placed-block anti-abuse for mining, and automatic history cleanup.

### Contract rewards

Automatic Contract Party XP is intentionally `0` by default. This prevents two Parties or alt Parties from repeatedly creating Contracts just to generate free XP.

Server owners can enable it:

```yaml
interaction:
  contracts:
    party-xp-reward: 0
```

By default Contract completion primarily affects Diplomacy Trust.

## Diplomacy + Trust

Relationships are symmetric between two Parties.

Relations:

- `NEUTRAL`
- `ALLY`
- `RIVAL`

Trust Score ranges from `-100` to `100` and is stored separately from the relation label. Completing Contracts can increase Trust; abandoning or failing them can reduce it.

Commands:

```text
/party diplomacy
/party diplomacy status <party>
/party diplomacy request <party> ally
/party diplomacy accept <party>
/party diplomacy deny <party>
/party diplomacy neutral <party>
/party diplomacy rival <party>
```

Alliance requires agreement. If both Parties send an Alliance request to each other, the second request completes the Alliance immediately. Neutral and Rival changes are direct actions.

Direct alias: `/partydiplomacy`, `/pdiplomacy`, `/pdiplo`.

## Recruitment + Join Applications

Each Party has a recruitment mode:

- `OPEN` — `/party apply <party>` joins immediately when a slot is available.
- `APPLICATION` — creates an application that Owner/authorized ranks can review.
- `CLOSED` — rejects new applications.

Commands:

```text
/party browse [page]
/party apply <party> [message]
/party apply cancel <party>
/party recruitment <open|application|closed>
/party applications
/party applications accept <player>
/party applications deny <player>
```

Direct aliases:

```text
/partybrowse
/partyapply
/partyrecruitment
/partyapplications
```

Applications expire automatically and a player can only have a configurable number of active applications. Accepting an application removes the player's other stale applications.

Party War membership locks and Party member limits are respected by OPEN recruitment and application acceptance.

## Configurable rank capabilities

v1.3 adds server-configurable capabilities for interaction management. Owner always has all interaction capabilities as a lockout safeguard. Officer and Member capabilities are configurable.

Default:

```yaml
rank-permissions:
  enabled: true
  officer:
    - contracts.create
    - contracts.respond
    - diplomacy.manage
    - recruitment.manage
    - applications.manage
  member: []
```

Wildcards are supported:

```yaml
rank-permissions:
  officer:
    - 'contracts.*'
    - diplomacy.manage
```

Use `/party rankperms` or `/partyrankperms` to see the current role's interaction capabilities.

This capability layer applies to v1.3 interaction actions. Existing core ownership safety such as Owner-only disband and Owner-only role changes remains unchanged.

## Interaction overview

```text
/party interaction
/partyinteraction
```

Shows active Contracts, completed Contract count, recruitment mode, pending applications, and quick command references.

## Modular configuration

```yaml
modules:
  projects: true
  skill-tree: true
  divisions: true
  identity: true
  contracts: true
  diplomacy: true
  applications: true
```

Disabling an interaction module does not disable the core Party system.

## Storage

v1.3 adds:

```text
plugins/MENKIESTESParty/interactions.yml
```

It contains Contract state/history, Diplomacy relations, Trust Score, Alliance requests, application queues, and interaction statistics.

Existing data files remain unchanged:

```text
parties.yml
wars.yml
season.yml
hall.yml
```

No database migration is required.

## Progression systems from v1.2.x

MENKIESTESParty still includes:

- Party Projects
- Party Skill Tree
- Party Divisions
- Dynamic Party Identity
- Progression GUI with pagination, confirmations and visual progress bars
- Weekly Quest
- Daily Party Mission
- Party Relic
- Party War
- Party Season

## PlaceholderAPI

Existing `%mparty_*%` placeholders remain. v1.3 adds:

```text
%mparty_recruitment%
%mparty_contracts_active%
%mparty_contracts_completed%
%mparty_applications_pending%
```

## Upgrade from v1.2.2

1. Stop the server.
2. Replace the old MENKIESTESParty JAR.
3. Do **not** delete `plugins/MENKIESTESParty/`.
4. Start the server normally.

The v1.3 config migration merges missing defaults without resetting existing Party data. `interactions.yml` is created automatically.

## Requirements

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional
- GriefPrevention optional
- No Skript required
- No database required
- No Vault required

## License

MENKIESTES SOFTWARE LICENSE v1.0 — MENKIESTES dibuat oleh CADERA. See repository `LICENSE`.
