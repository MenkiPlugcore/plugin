# MoonSignMenu v1.2.0

Plugin Paper untuk MOONSIGN yang memberikan native Bedrock Forms melalui Floodgate, inventory GUI fallback untuk Java, Member Book, internal TPA, dan sekarang menu yang sepenuhnya dapat diatur dari `config.yml`.

## Target

- Paper 1.21.11
- Java 21
- Geyser + Floodgate untuk native Bedrock UI

## Fitur utama

- `/menu` atau klik kanan `MOONSIGN Member Book` untuk membuka menu.
- Native Bedrock button icons tanpa emoji teks.
- Java inventory GUI fallback.
- Built-in TPA / TPAHere / accept / deny / toggle.
- Tombol `Kembali` pada submenu internal.
- Menu utama dan submenu config-driven.
- Tambah/hapus/edit/reorder tombol tanpa compile ulang.
- Button types: `command`, `teleport`, `submenu`, `close`.
- Command dapat dijalankan sebagai player atau console.
- Permission per tombol.
- Bedrock icon path dan Java material per tombol.
- Java menu pagination otomatis bila tombol banyak.
- `/menu reload` untuk menerapkan perubahan config tanpa restart.

## Install / Upgrade

1. Pastikan server memakai Java 21 + Paper 1.21.11.
2. Install Geyser-Spigot dan Floodgate-Spigot untuk native UI Bedrock.
3. Ganti JAR lama dengan `MoonSignMenu-1.2.0.jar`.
4. Restart server satu kali untuk migrasi config.
5. Setelah itu perubahan tombol dapat diterapkan dengan `/menu reload`.

Saat upgrade dari v1.1, nilai `menu-actions` lama dan icon main menu lama akan dipindahkan ke struktur menu baru bila config belum memiliki `menu.main.buttons`.

## Contoh tombol command

```yaml
menu:
  main:
    buttons:
      team:
        enabled: true
        name: "Team"
        type: "command"
        command: "team"
        executor: "player"
        order: 80
        icon: "textures/items/iron_sword"
        java-material: "IRON_SWORD"
        permission: ""

      shop:
        enabled: true
        name: "Shop"
        type: "command"
        command: "shop"
        executor: "player"
        order: 90
        icon: "textures/items/nether_star"
        java-material: "NETHER_STAR"
        permission: ""
```

Untuk UltimateTeams atau DGShop, cukup ubah `command` ke command pembuka GUI plugin yang dipakai server.

## Menambah tombol baru

Tambahkan section baru di `menu.main.buttons`:

```yaml
      enderchest:
        enabled: true
        name: "Ender Chest"
        type: "command"
        command: "ec"
        executor: "player"
        order: 105
        icon: "textures/blocks/ender_chest_front"
        java-material: "ENDER_CHEST"
        permission: ""
        lore:
          - "&7Buka Ender Chest milikmu."
```

`order` menentukan urutan tombol. `enabled: false` menyembunyikan tombol.

## Submenu

Buat tombol pembuka submenu:

```yaml
      player-menu:
        enabled: true
        name: "Menu Player"
        type: "submenu"
        submenu: "player"
        order: 130
        icon: "textures/items/name_tag"
        java-material: "PLAYER_HEAD"
```

Lalu definisikan submenu:

```yaml
menu:
  submenus:
    player:
      title: "&dMenu Player"
      content: "&7Fitur pribadi player."
      back-menu: "main"
      buttons:
        homes:
          enabled: true
          name: "Homes"
          type: "command"
          command: "homes"
          executor: "player"
          order: 10
          icon: "textures/items/bed_red"
          java-material: "RED_BED"
```

Submenu MoonSignMenu otomatis mendapatkan tombol `Kembali`. GUI yang dibuka oleh plugin eksternal tetap mengikuti GUI plugin tersebut dan tidak dapat disisipi tombol Back oleh MoonSignMenu.

## Permission tombol

```yaml
      vipshop:
        enabled: true
        name: "VIP Shop"
        type: "command"
        command: "vipshop"
        permission: "moonsign.vipshop"
```

Dengan `menu.hide-buttons-without-permission: true`, player tanpa permission tidak akan melihat tombol tersebut.

## Command executor dan placeholder

```yaml
      daily:
        enabled: true
        name: "Daily Reward"
        type: "command"
        command: "reward give %player% daily"
        executor: "console"
```

Placeholder command:

- `%player%`
- `%uuid%`
- `%world%`

## Command

| Command | Fungsi |
|---|---|
| `/menu` | Buka Menu Member |
| `/menu reload` | Reload `config.yml` tanpa restart |
| `/tpa <player>` | Minta teleport ke player |
| `/tpahere <player>` | Minta player teleport ke kamu |
| `/tpaccept` | Terima request |
| `/tpdeny` | Tolak request |
| `/tptoggle` | Matikan/aktifkan incoming request |

## Permission

Player default:

- `moonsignmenu.menu`
- `moonsignmenu.tpa`
- `moonsignmenu.tpahere`
- `moonsignmenu.tpaccept`
- `moonsignmenu.tpdeny`
- `moonsignmenu.tptoggle`

Admin / bypass:

- `moonsignmenu.admin.reload` (default OP)
- `moonsignmenu.bypass.cooldown` (default OP)
- `moonsignmenu.bypass.disabled` (default OP)

## Build

```bash
mvn clean package
```

Output:

```text
target/MoonSignMenu-1.2.0.jar
```
