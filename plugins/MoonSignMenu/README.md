# MoonSignMenu v1.1.0

Plugin Paper untuk MOONSIGN yang memberikan **native Bedrock Forms** melalui Floodgate dan **inventory GUI fallback** untuk player Java.

## Target

- Paper 1.21.11
- Java 21
- Geyser + Floodgate untuk native Bedrock UI

## Fitur v1.1.0

- `/menu` tetap tersedia sebagai fallback.
- `MOONSIGN Member Book` otomatis diberikan ke player.
- Klik kanan Member Book untuk membuka Menu Member tanpa mengetik command.
- Member Book otomatis dikembalikan jika hilang saat join/respawn.
- Member Book dapat dibuat tidak bisa dibuang.
- Bedrock native member menu menggunakan icon gambar dari texture vanilla Bedrock, bukan emoji teks.
- Tidak membutuhkan custom resource pack untuk icon bawaan.
- Menu TP Bedrock memakai halaman SimpleForm ber-icon dengan tombol `Kembali`.
- Java inventory GUI juga memiliki tombol `Kembali` pada submenu TP.
- Internal `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, dan `/tptoggle`.
- Incoming request Bedrock modal: TERIMA / TOLAK.
- Request expiry, cooldown, persistent toggle, optional cross-world block, dan sound feedback.
- Tombol menu lain menjalankan command yang dapat diatur dari `config.yml`.

## Build

```bash
mvn clean package
```

Output:

```text
target/MoonSignMenu-1.1.0.jar
```

GitHub Actions pada repository MenkiPlugcore/plugin melakukan compile-check dan mengunggah artifact JAR.

## Install / Upgrade

1. Pastikan server memakai Java 21 + Paper 1.21.11.
2. Install Geyser-Spigot dan Floodgate-Spigot untuk native UI Bedrock.
3. Ganti JAR lama dengan `MoonSignMenu-1.1.0.jar` di folder `plugins/`.
4. Restart server.
5. Config lama akan menerima key default baru untuk icon dan Member Book.
6. Player akan mendapatkan Member Book otomatis jika belum memilikinya.

## Command

| Command | Fungsi |
|---|---|
| `/menu` | Buka menu member secara manual |
| `/tpa <player>` | Minta teleport ke player |
| `/tpahere <player>` | Minta player teleport ke kamu |
| `/tpaccept` | Terima request |
| `/tpdeny` | Tolak request |
| `/tptoggle` | Matikan/aktifkan incoming request |

## Member Book

```yaml
member-book:
  enabled: true
  give-on-join: true
  give-delay-ticks: 10
  hotbar-slot: 8
  prevent-drop: true
  material: "BOOK"
  name: "&d&lMOONSIGN &fMember Book"
  lore:
    - "&7Klik kanan untuk membuka Menu Member."
    - "&8Item menu pribadi MOONSIGN."
```

`hotbar-slot` menggunakan index 0-8. Default `8` berarti slot hotbar paling kanan.

## Icon Bedrock

SimpleForm Bedrock mendukung image path. MoonSignMenu menggunakan texture vanilla client secara default:

```yaml
bedrock-icons:
  enabled: true
  tpa: "textures/items/ender_pearl"
  warp: "textures/items/compass_item"
  pwarp: "textures/items/map_filled"
  sethome: "textures/items/bed_red"
  bank: "textures/items/gold_ingot"
  back: "textures/items/arrow"
```

Karena memakai texture bawaan Bedrock, player tidak perlu mengunduh resource pack tambahan untuk konfigurasi default. Path dapat diganti nanti jika server menggunakan custom Bedrock resource pack.

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
