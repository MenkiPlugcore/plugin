# MENKIESTESParty Developer API v1.0

Available since MENKIESTESParty v1.4.0.

The public API is registered through Bukkit `ServicesManager`. Do not access MENKIESTESParty internal managers or YAML files from another plugin.

## Get the API

```java
import id.cadera.menkiestesparty.api.MenkiPartyAPI;
import org.bukkit.plugin.RegisteredServiceProvider;

RegisteredServiceProvider<MenkiPartyAPI> registration =
        getServer().getServicesManager().getRegistration(MenkiPartyAPI.class);

if (registration == null) {
    getLogger().warning("MENKIESTESParty API is unavailable.");
    return;
}

MenkiPartyAPI api = registration.getProvider();
```

Add MENKIESTESParty as a `depend` or `softdepend` in your plugin when consuming the API.

## Read Party state

```java
api.party(player.getUniqueId()).ifPresent(party -> {
    getLogger().info("Party: " + party.displayName());
    getLogger().info("Level: " + party.level());
    getLogger().info("Members: " + party.memberCount());
    getLogger().info("Online: " + party.onlineMembers());
});
```

Snapshots are immutable. `PartySnapshot.members()` is copied before it is exposed.

Available snapshots:

- `PartySnapshot`
- `MemberSnapshot`
- `ProjectSnapshot`
- `ContractSnapshot`
- `RelationSnapshot`

Useful calls:

```java
api.partyKey(playerUuid);
api.party(playerUuid);
api.party("paradox");
api.parties();
api.currentProject("paradox");
api.contracts("paradox");
api.relation("paradox", "lunar");
api.hasCapability(playerUuid, "contracts.create");
api.integrationAvailable("vault");
```

## Controlled write operations

The API does not expose YAML or internal mutable objects.

```java
api.addPartyExperience("paradox", 100, "MyPlugin achievement");
api.broadcast("paradox", "&aAchievement completed!");
```

MENKIESTESParty clamps Party XP at zero through its normal Party service.

## Bukkit events

v1.4.0 provides post-state, non-cancellable events:

- `PartyCreateEvent`
- `PartyDisbandEvent`
- `PartyMemberChangeEvent`
- `PartyLevelChangeEvent`
- `PartyProjectCompleteEvent`
- `PartyContractStatusEvent`
- `PartyRelationChangeEvent`
- `PartyWarStateEvent`

Example:

```java
@EventHandler
public void onProject(PartyProjectCompleteEvent event) {
    getLogger().info(event.partyKey() + " completed " + event.projectName());
}

@EventHandler
public void onMember(PartyMemberChangeEvent event) {
    if (event.action() == PartyMemberChangeEvent.Action.JOIN) {
        getLogger().info(event.member().name() + " joined " + event.partyKey());
    }
}
```

Events are emitted after MENKIESTESParty state has already passed its internal validation. They are intentionally non-cancellable.

The event bridge snapshots state on the configured interval:

```yaml
developer:
  events:
    enabled: true
    scan-ticks: 20
```

Default is once per second.

## PlaceholderAPI additions

v1.4.0 adds:

```text
%mparty_api_version%
%mparty_plugin_version%
%mparty_owner%
%mparty_online_members%
%mparty_identity_raw%
%mparty_project_percent%
%mparty_inbox_unread%
%mparty_vault_available%
%mparty_relation_<partyKey>%
%mparty_trust_<partyKey>%
```

Examples:

```text
%mparty_relation_paradox%
%mparty_trust_paradox%
```

## Vault hook

Vault is optional. MENKIESTESParty does not require or compile against Vault API.

At runtime v1.4.0 discovers a registered Vault Economy provider through Bukkit ServicesManager. Other plugins can check:

```java
api.integrationAvailable("vault");
```

## Reward engine

The reward engine is server-configured and disabled by empty/zero rewards by default.

Supported triggers:

- `party-level-up`
- `project-complete`
- `contract-complete`
- `war-win`

Each trigger supports:

```yaml
vault-owner-money: 0
commands: []
```

Common command placeholders:

```text
{party}
{party_key}
{owner}
{owner_uuid}
{level}
{experience}
```

Trigger-specific placeholders include:

```text
{old_level}
{new_level}
{project}
{project_name}
{contract}
{contract_type}
{source_party}
{source_party_key}
{target_party}
{target_party_key}
{war_history_id}
```

Commands run as console. Values are supplied from committed Party state, not raw chat input.

## Compatibility policy

API version `1.0` is intended to remain source-compatible throughout the MENKIESTESParty v1.x line. Future storage implementations should preserve these snapshot contracts where practical.
