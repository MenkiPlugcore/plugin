# MoonSignMenu v1.0.0

Plugin Paper untuk MOONSIGN yang memberikan **native Bedrock Forms** melalui Floodgate dan **inventory GUI fallback** untuk player Java.

## Target

- Paper 1.21.11
- Java 21
- Geyser + Floodgate untuk native Bedrock UI

## Fitur v1.0.0

- `/menu`
- Bedrock native member menu
- Java inventory menu fallback
- Native Bedrock `Minta Teleport` form dengan dropdown player
- Mode `Pergi ke mereka` (`/tpa`) dan `Bawa ke saya` (`/tpahere`)
- Incoming request Bedrock modal: TERIMA / TOLAK
- Java fallback menerima instruksi `/tpaccept` atau `/tpdeny`
- `/tpaccept`, `/tpdeny`, `/tptoggle`
- Request expiry
- Anti-spam cooldown
- Persistent incoming-request toggle (`data.yml`)
- Optional block cross-world
- Cancel request on world change
- Sound feedback
- Menu buttons can run commands from `config.yml`

## Build

```bash
mvn clean package
```

Output:

```text
target/MoonSignMenu-1.0.0.jar
```

GitHub Actions pada repository MenkiPlugcore/plugin juga melakukan compile-check dan mengunggah artifact JAR.

## Install

1. Pastikan server memakai Java 21 + Paper 1.21.11.
2. Install Geyser-Spigot dan Floodgate-Spigot jika ingin native UI Bedrock.
3. Masukkan `MoonSignMenu-1.0.0.jar` ke folder `plugins/`.
4. Restart server.
5. Jalankan `/menu`.

## Command

| Command | Fungsi |
|---|---|
| `/menu` | Buka menu member |
| `/tpa <player>` | Minta teleport ke player |
| `/tpahere <player>` | Minta player teleport ke kamu |
| `/tpaccept` | Terima request |
| `/tpdeny` | Tolak request |
| `/tptoggle` | Matikan/aktifkan incoming request |

## Permission

Permission player default aktif:

- `moonsignmenu.menu`
- `moonsignmenu.tpa`
- `moonsignmenu.tpahere`
- `moonsignmenu.tpaccept`
- `moonsignmenu.tpdeny`
- `moonsignmenu.tptoggle`

Bypass:

- `moonsignmenu.bypass.cooldown` (default OP)
- `moonsignmenu.bypass.disabled` (default OP)

## Integrasi tombol lain

Edit `plugins/MoonSignMenu/config.yml` di server:

```yaml
menu-actions:
  warp: "warp"
  pwarp: "pwarp"
  sethome: "sethome"
  land: "claimslist"
  transfer: "pay"
  bank: "bank"
  team: "team"
  shop: "shop"
  playershop: "ah"
  report: "report"
  barter: "trade"
```

Kosongkan command jika fitur belum dipakai.
