# MENKIESTESParty v2.0.1 — GUI Visual Polish Patch

Release date: 2026-09-11

## Added

- Global visual theme layer for MENKIESTESParty inventory GUIs.
- Default black/cyan/purple stained-glass palette for a cleaner, more deliberate layout.
- Decorative Party header in the root `MENKIESTES Party` menu with Party, role, level/member and Social Identity context.
- Consistent decoration across Party management, Progression, Interaction, Inbox/Activity and Administration inventory screens.
- PDC-tagged decorative items so visual filler can be distinguished safely from functional buttons.
- Top-inventory click/shift-click/drag protection for themed MENKIESTESParty menus.
- Dedicated `gui-theme.yml` configuration.
- `GUI_THEME.md` documentation.
- Bukkit-free GUI layout regression tests.

## Visual behavior

The theme only fills inventory slots that are still empty after the existing GUI manager has populated its functional buttons. Existing action slots, pagination controls, confirmation buttons, member heads, Project nodes, Contract entries and admin actions are not replaced by the theme.

Default palette:

```yaml
theme:
  palette:
    primary: BLACK_STAINED_GLASS_PANE
    accent: CYAN_STAINED_GLASS_PANE
    secondary: PURPLE_STAINED_GLASS_PANE
```

The root Party menu also receives a decorative header in slot 4 when that slot is otherwise unused.

## Safety / compatibility

- No Party data path is changed.
- No Document Schema migration is introduced; Document Schema remains v2.
- Storage backend protocol remains v1.
- `MenkiPartyAPI.API_VERSION` remains `1.0`.
- `MenkiPartyAPIv2.API_VERSION` remains `2.0`.
- YAML remains the default backend; SQLite/MySQL remain optional.
- No new external dependency or service is added.
- No GUI animation, polling task or per-tick visual scheduler is added.
- Existing GUI managers remain responsible for permissions, validation and mutations.
- Existing Architecture v2, social, admin safety, storage failover and network LOCAL behavior remain unchanged.
- Folia remains experimental and is not production-certified by this visual patch.

## Configuration

`plugins/MENKIESTESParty/gui-theme.yml` controls the visual layer. The whole theme can be disabled with:

```yaml
theme:
  enabled: false
```

`fill-empty-slots: false` keeps the root header behavior while leaving other empty inventory slots untouched. Palette materials can be changed to valid Bukkit material names.

See [`GUI_THEME.md`](GUI_THEME.md).

## Upgrade from v2.0.0

1. Stop the server normally.
2. Replace the old MENKIESTESParty JAR with v2.0.1.
3. Keep the existing plugin data directory.
4. Start the server.
5. Open `/party`, Progression, Interaction and `/partyadmin` menus.
6. Confirm existing buttons still execute their original actions.
7. Confirm filler panes cannot be taken, shift-clicked or replaced.

No data migration is required for v2.0.1.

## Recommended smoke test

- `/party` root menu appearance and header.
- Invite / member management navigation.
- Progression Hub, Projects, Skills, Divisions and Identity.
- Party Browser, Contracts, Diplomacy and Applications.
- Inbox / Recent Activity.
- Admin Browser / Inspect.
- Back/page/confirm buttons.
- Shift-click from player inventory while a themed GUI is open.
- Drag items across the top inventory.
- `/partyarchitecture verify`, `/partystorage verify`, `/partyapi verify` after upgrade.
