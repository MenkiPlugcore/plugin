# MENKIESTESParty GUI Theme

MENKIESTESParty v2.0.1 adds a global visual layer for the plugin's inventory menus. The goal is to make the UI feel like one product instead of a collection of raw chest inventories, without duplicating or bypassing the existing GUI logic.

## Design

Default palette:

```text
Primary    BLACK_STAINED_GLASS_PANE
Accent     CYAN_STAINED_GLASS_PANE
Secondary  PURPLE_STAINED_GLASS_PANE
```

Functional buttons continue to use their existing semantic materials and colors. The visual theme only fills slots that remain empty after each GUI has populated its actual controls.

This means Project nodes, skill nodes, player heads, Contract entries, pagination buttons, confirmation controls and admin actions keep their original item identity and click routing.

## Covered inventory screens

The theme recognizes the current MENKIESTESParty inventory family, including:

- main Party menu;
- Party Invite / Members / Manage Member;
- Progression Hub / Profile / Levels;
- Projects / Skills / Divisions / Dynamic Identity;
- Interaction Hub / Party Browser / Party Overview;
- Contracts and Contract creation/detail screens;
- Diplomacy;
- Recruitment and Applications;
- Rank Capabilities;
- Notification Inbox and Recent Activity;
- Administration Browser and Inspect.

## Root Party header

The main `MENKIESTES Party` inventory uses its otherwise-empty slot 4 as a non-functional header.

For a Party member it can display:

```text
MENKIESTES PARTY
Party: Example
Role: OWNER
Level: 4 • Member: 8/15
Identity: [TAG] • Active Badge

MENKIESTES UI • CADERA
v2.0.1
```

Players without a Party instead receive basic create/browse guidance.

The header is decorative. It never performs a mutation.

## Configuration

File:

```text
plugins/MENKIESTESParty/gui-theme.yml
```

Default:

```yaml
theme:
  enabled: true
  fill-empty-slots: true
  filler-name: '&0'

  palette:
    primary: BLACK_STAINED_GLASS_PANE
    accent: CYAN_STAINED_GLASS_PANE
    secondary: PURPLE_STAINED_GLASS_PANE

  main-header:
    enabled: true
    material: NETHER_STAR
    name: '&b&lMENKIESTES &fPARTY'

  watermark: '&8MENKIESTES UI &7• &fCADERA'
```

### Disable the visual layer

```yaml
theme:
  enabled: false
```

The existing GUI functionality continues to work; the theme listener simply stops decorating/protecting themed inventory surfaces.

### Keep header but remove filler panes

```yaml
theme:
  fill-empty-slots: false
```

### Change palette

Use valid Bukkit/Paper Material names, for example:

```yaml
theme:
  palette:
    primary: GRAY_STAINED_GLASS_PANE
    accent: LIGHT_BLUE_STAINED_GLASS_PANE
    secondary: BLUE_STAINED_GLASS_PANE
```

Invalid configured materials safely fall back to the default material for that role.

## Item movement protection

Decorative panes and the root header are tagged internally with a plugin PersistentDataContainer marker.

While a recognized MENKIESTESParty GUI is open, v2.0.1 also protects the top inventory from item movement:

- top-inventory clicks are cancelled after the existing GUI action handler gets its event;
- shift-click movement into the GUI is cancelled;
- drag operations touching the top inventory are cancelled.

The existing GUI manager remains responsible for the action itself. The theme layer only prevents the inventory item from being physically moved after that action routing.

## Performance

The GUI theme has no timer, animation or polling loop.

Work happens only when a recognized inventory is opened or interacted with. Decoration is bounded by the inventory size (normally 27 or 54 slots), and cached pane templates are cloned for empty slots.

No database query or external network request is introduced by the theme.

## Compatibility

v2.0.1 does not change:

```text
Document Schema: 2
Storage backend protocol: 1
Public API v1: 1.0
Public API v2: 2.0
Network default: LOCAL
Java target: 21
Paper target: 1.21.11
```

Existing YAML/SQLite/MySQL data remains compatible.

## Testing checklist

After upgrading, verify at minimum:

```text
/party
/party project
/party skill
/party division
/party interaction
/party browse
/party contract
/party diplomacy
/party inbox
/party activity
/partyadmin
```

Also test the functional buttons for Back, Next/Previous page and confirmation flows. Try shift-clicking and dragging normal inventory items while these menus are open; no item should enter the plugin GUI.

For configuration changes, a normal server restart is the safest reload path during production testing. The plugin-wide reload path also reloads the GUI theme when invoked by an internal/admin flow that calls `reloadPluginConfig()`.
