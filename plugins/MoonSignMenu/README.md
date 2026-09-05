# MoonSignMenu v1.3.0

Plugin Paper untuk MOONSIGN yang memberikan native Bedrock Forms melalui Floodgate, inventory GUI fallback untuk Java, Member Book, internal TPA, menu config-driven, dan flow Bedrock klik-only untuk Home, Pay, serta AxTrade.

## Target

- Paper 1.21.11
- Java 21
- Geyser + Floodgate untuk native Bedrock UI
- EssentialsX untuk Home Manager
- AxTrade untuk flow Barter

## Fitur utama

- `/menu` atau klik kanan `MOONSIGN Member Book` untuk membuka menu.
- Native Bedrock button icons tanpa emoji teks.
- Java inventory GUI fallback.
- Built-in TPA / TPAHere / accept / deny / toggle.
- Tombol `Kembali` pada submenu internal.
- Menu utama dan submenu config-driven.
- Tambah/hapus/edit/reorder tombol tanpa compile ulang.
- Button types: `command`, `teleport`, `homes`, `pay`, `trade`, `submenu`, `close`.
- `/menu reload` untuk menerapkan perubahan config tanpa restart.

## Bedrock Home Manager

Gunakan `type: homes`. MoonSignMenu membaca data home dan limit langsung dari EssentialsX, lalu menyediakan:

- jumlah home tersimpan / maksimum home;
- `Set Home Baru` dengan input nama;
- daftar semua home;
- teleport ke home;
- hapus home tertentu dengan konfirmasi;
- dukungan `essentials.sethome.multiple.unlimited`.

Contoh:

```yaml
sethome:
  enabled: true
  name: Home
  type: homes
  command: homes # fallback Java
  icon: textures/items/bed_red
  java-material: RED_BED
```

Command Essentials dapat diubah:

```yaml
integrations:
  essentials-home:
    set-command: 'sethome %home%'
    teleport-command: 'home %home%'
    delete-command: 'delhome %home%'
```

## Bedrock Transfer / Pay

Gunakan `type: pay`. Player Bedrock cukup:

1. klik `Transfer`;
2. pilih player online;
3. ketik nominal;
4. konfirmasi `BAYAR`.

```yaml
transfer:
  enabled: true
  name: Transfer
  type: pay
  command: pay # fallback Java
```

Template command:

```yaml
integrations:
  pay:
    command: 'pay %target% %amount%'
```

Nominal seperti `10000` dan format Indonesia `10.000` diterima.

## Bedrock AxTrade / Barter

Gunakan `type: trade`. Menu Barter menyediakan:

- Kirim Permintaan Trade;
- Terima Permintaan;
- Tolak Permintaan;
- Aktif/Nonaktif Permintaan;
- player picker online;
- tombol Kembali.

MoonSignMenu menggunakan command resmi AxTrade secara default:

```yaml
integrations:
  axtrade:
    send-command: 'axtrade %target%'
    accept-command: 'axtrade accept %target%'
    deny-command: 'axtrade deny %target%'
    toggle-command: 'axtrade toggle'
```

## Menu config-driven

Contoh tombol command biasa:

```yaml
team:
  enabled: true
  name: Party
  type: command
  command: party
  executor: player
  order: 80
  icon: textures/items/iron_sword
  java-material: IRON_SWORD
```

Untuk DGShop, cukup ubah command tombol Shop ke command pembuka GUI yang digunakan server.

## Submenu

```yaml
player-menu:
  enabled: true
  name: Menu Player
  type: submenu
  submenu: player
  order: 130
```

Submenu MoonSignMenu otomatis mendapatkan tombol `Kembali`.

## Command executor dan placeholder

Command button dapat dijalankan sebagai `player` atau `console`.

Placeholder umum:

- `%player%`
- `%uuid%`
- `%world%`

Flow khusus juga memakai:

- `%home%`
- `%target%`
- `%amount%`

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

## Upgrade dari v1.2

Saat startup pertama v1.3, konfigurasi default lama akan dimigrasikan:

- `sethome` command default -> `type: homes`;
- `transfer` command default -> `type: pay`;
- `barter` command default -> `type: trade`.

Setting custom lain tidak disentuh. Restart server satu kali setelah mengganti JAR; edit config berikutnya cukup `/menu reload`.

## Build

```bash
mvn clean package
```

Output:

```text
target/MoonSignMenu-1.3.0.jar
```
