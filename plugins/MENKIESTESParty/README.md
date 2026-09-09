# MENKIESTESParty v1.1.0

Native Paper Party system untuk MENKIESTES/MOONSIGN, menggunakan penyimpanan YAML lokal.

## Update v1.1.0

- Daily Party Mission ditambahkan sebagai progress bersama seluruh anggota Party.
- Weekly Quest lama tetap aktif untuk progres Party Relic.
- Party War sekarang fokus ke kompetisi skor/kill.
- Pemenang Party War hanya menerima Party XP (`war.party-xp`, default 250).
- Tidak ada War Chest, Party Hall, antrean item, atau reward item otomatis dari Party War.
- Reward senjata/custom item diberikan manual oleh admin langsung ke perwakilan team.
- `/partyhall`, `/party hall`, `/party claimchest`, dan `/party rewards` sudah tidak digunakan sebagai sistem reward.

## Daily Mission

Default:

| Mission | Goal | Party XP |
| --- | ---: | ---: |
| Mining | 150 | 40 |
| Hunter | 30 | 50 |
| Farmer | 80 | 35 |

Daily Mission reset otomatis ketika tanggal server berganti. Progress bersifat shared untuk satu Party.

Command:

- `/party daily`
- `/partydaily`
- `/partydaily resetall` — admin

## Party War

Admin:

- `/partywar start [durasi] [target] [prepare]`
- `/partywar finish`
- `/partywar cancel`

Player:

- `/partywar status`
- `/partywar top`
- `/partywar hunt`

War tetap menyimpan history, skor, kill, death, combat participation, dan pemenang. Reward fisik tidak dibuat plugin.

## Requirements

- Java 21
- Paper 1.21.11
- PlaceholderAPI opsional

Build:

```bash
gradle clean build
```

Output berada di `build/libs/`.
