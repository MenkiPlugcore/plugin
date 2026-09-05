# MENKIESTESParty Command Wiki

Dokumentasi command resmi untuk **MENKIESTESParty v1.0.4**.

## Command utama dan alias

- `/party` — alias: `/p`, `/parties`
- `/pchat` — alias: `/pc`, `/partychat`
- `/partywar`
- `/partyseason`
- `/partyhall`

## Player umum

| Command | Fungsi |
|---|---|
| `/party` | Buka GUI Party |
| `/party menu` | Buka GUI Party |
| `/party help` | Lihat bantuan command |
| `/party create <nama>` | Buat Party baru |
| `/party accept` | Terima invite Party |
| `/party leave` | Keluar dari Party |
| `/party home` | Teleport ke Party Home |
| `/party members` | Buka GUI roster/member |
| `/party manage` | Alias GUI roster/member |
| `/party contribution` | Lihat kontribusi member |
| `/party contrib` | Alias contribution |
| `/party rep` | Lihat Reputation Party |
| `/party level` | Lihat Level dan slot Party |
| `/party quest` | Lihat Weekly Quest |
| `/party quests` | Alias quest |
| `/party relic` | Lihat Party Relic |
| `/party top` | Ranking Party |
| `/party war` | Status Party War |
| `/party war top` | Ranking poin Party War |
| `/party war hunt` | Tracker musuh |
| `/party claimchest` | Claim War Chest |
| `/party rewards` | Alias claimchest |
| `/party hall` | Lihat Reward Hall |
| `/party hall claim` | Claim Reward Hall jika eligible |
| `/pchat <pesan>` | Chat khusus Party |
| `/pc <pesan>` | Alias Party Chat |
| `/partychat <pesan>` | Alias Party Chat |
| `/partywar status` | Lihat status War |
| `/partywar top` | Ranking War |
| `/partywar hunt` | Tracker musuh |
| `/partywar tracker` | Alias tracker |
| `/partywar compass` | Alias tracker |
| `/partyseason status` | Lihat status Season |
| `/partyseason top` | Ranking Season |
| `/partyhall` | Lihat Hall aktif |
| `/partyhall claim` | Claim Hall reward jika eligible |

## Owner dan Officer

| Command | Owner | Officer | Fungsi |
|---|:---:|:---:|---|
| `/party invite` | ✅ | ✅ | Buka GUI Invite Player |
| `/party invite <player>` | ✅ | ✅ | Invite player langsung |
| `/party sethome` | ✅ | ✅ | Set Party Home |
| `/party kick <player>` | ✅ | ✅* | Kick member |
| `/party members` | ✅ | ✅ | Buka GUI Manage Members |
| `/party manage` | ✅ | ✅ | Alias Manage Members |

\* Officer hanya dapat mengeluarkan **Member biasa**. Officer tidak dapat mengeluarkan Owner atau Officer lain.

## Owner saja

| Command | Fungsi |
|---|---|
| `/party promote <player>` | Promote Member menjadi Officer |
| `/party demote <player>` | Demote Officer menjadi Member |
| `/party disband` | Bubarkan Party |

## Admin Party

Permission admin:

```text
menkiestesparty.admin
```

Default permission adalah OP.

| Command | Fungsi |
|---|---|
| `/party addrep <party> <jumlah>` | Tambah Reputation Party |
| `/party removerep <party> <jumlah>` | Kurangi Reputation Party |
| `/party setrep <party> <jumlah>` | Set Reputation Party |
| `/party resetquest` | Paksa reset Weekly Quest |
| `/party reload` | Reload config plugin |

## Admin Party War

| Command | Fungsi |
|---|---|
| `/partywar start [durasi] [target] [prepare]` | Mulai Party War |
| `/partywar run [durasi] [target] [prepare]` | Alias start |
| `/partywar finish` | Paksa selesaikan War dan tentukan winner dari score saat itu |
| `/partywar cancel` | Batalkan Party War |
| `/partywar stop` | Alias cancel |

Default Party War menggunakan konfigurasi:

- Duration: 30 menit
- Target: 30 poin
- Prepare: 10 menit
- World: `world`

## Admin Season

| Command | Fungsi |
|---|---|
| `/partyseason start <nama>` | Mulai Season baru |
| `/partyseason end` | Akhiri Season aktif |

## Admin Reward Hall

Reward Hall menggunakan **future reward queue**. Slot `1` berarti reward untuk Party War berikutnya.

| Command | Fungsi |
|---|---|
| `/partyhall plan` | Lihat antrean hadiah War |
| `/partyhall queue` | Alias plan |
| `/partyhall items` | Alias plan |
| `/partyhall item` | Alias plan |
| `/partyhall reward` | Alias plan |
| `/partyhall setitem [slot]` | Set/replace item hadiah pada slot antrean |
| `/partyhall additem` | Tambahkan item di tangan ke slot antrean berikutnya |
| `/partyhall removeitem <slot>` | Hapus item dari antrean lalu compact queue |
| `/partyhall delitem <slot>` | Alias removeitem |
| `/partyhall clearitems` | Hapus semua hadiah masa depan |
| `/partyhall clearitem` | Alias clearitems |
| `/partyhall setcurrent` | Recovery/replace item Hall yang sudah aktif pada Week saat ini |

Reward Hall queue tidak reset saat bulan berganti. Contoh: reward yang belum terpakai di akhir September tetap menjadi antrean untuk Party War Oktober.

## Aturan Reward Hall claim

Player hanya dapat claim Reward Hall jika:

1. Party-nya menjadi pemenang Party War.
2. Player tercatat sebagai participant War tersebut.
3. Memenuhi `war.min-participation-minutes` (default 5 menit).
4. Benar-benar tercatat combat melawan Party lain.
5. Belum pernah claim reward Hall untuk entry tersebut.

Setiap participant yang eligible mendapat **1 copy** reward dan hanya dapat claim sekali.

## Roster lock saat Party War

Saat Party War berada pada phase `PREPARE` atau `ACTIVE`, roster Party dikunci. Invite, accept, leave, kick, dan perubahan roster tidak boleh digunakan untuk memanipulasi peserta War.

## Member cap per Party Level

| Level | Maksimum Member |
|---:|---:|
| 1 | 5 |
| 2 | 10 |
| 3 | 12 |
| 4 | 15 |
| 5 | 20 |

Party Level 1 lama yang sudah terlanjur memiliki lebih dari 5 member tidak otomatis kehilangan member. Party tersebut hanya tidak dapat menerima member baru sampai jumlah member kembali sesuai cap atau Party naik level.

---

Dokumentasi ini harus ikut diperbarui setiap kali command, permission, role rule, atau behavior Party berubah.
