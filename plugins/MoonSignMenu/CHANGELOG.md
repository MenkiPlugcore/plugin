# Changelog

## 1.1.0

- Added image icons to native Bedrock menu buttons using vanilla Bedrock texture paths.
- Removed emoji dependency from menu labels.
- Added Back navigation to the built-in teleport menu flow on Bedrock and Java.
- Reworked Bedrock teleport selection into icon-based SimpleForm pages so navigation stays consistent.
- Added automatic `MOONSIGN Member Book` item.
- Right-clicking Member Book opens the member menu without typing `/menu`.
- Member Book is restored when missing on join/respawn and can be configured to prevent dropping.
- Added configurable Bedrock icon paths and Member Book settings.
- Existing configs now receive new default keys automatically during upgrade.

## 1.0.0

- Added `/menu` member menu.
- Added native Bedrock forms through Floodgate.
- Added Java inventory GUI fallback.
- Added internal `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, and `/tptoggle` system.
- Added request expiration and anti-spam cooldown.
- Added persistent incoming-request toggle.
- Added optional cross-world blocking and cancellation on world change.
- Added configurable menu command actions and sound feedback.
