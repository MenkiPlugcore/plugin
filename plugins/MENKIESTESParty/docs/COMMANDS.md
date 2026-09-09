# MENKIESTESParty Commands — v1.1.0

## Player

- `/party` — buka Party GUI.
- `/party create <nama>` — buat Party.
- `/party invite [player]` — invite player; Owner/Officer.
- `/party accept` — terima invite.
- `/party leave` — keluar dari Party.
- `/party disband` — bubarkan Party; Owner.
- `/party kick <player>` — keluarkan member; Owner/Officer.
- `/party promote <player>` — jadikan Officer; Owner.
- `/party demote <player>` — turunkan ke Member; Owner.
- `/party sethome` — set Party Home; Owner/Officer.
- `/party home` — teleport ke Party Home.
- `/party members` / `/party manage` — roster GUI.
- `/party contribution` — kontribusi anggota.
- `/party rep` — Party reputation/XP internal.
- `/party level` — level dan slot Party.
- `/party top` — leaderboard Party.
- `/party quest` — Weekly Quest.
- `/party relic` — Party Relic.
- `/party daily` — Daily Party Mission.
- `/partydaily` — alternatif Daily Mission.
- `/party war` / `/partywar status` — status Party War.
- `/partywar top` — ranking War aktif.
- `/partywar hunt` — tracker musuh.
- `/pchat <pesan>` — Party chat.

## Admin

Permission: `menkiestesparty.admin`

- `/party addrep <party> <amount>`
- `/party removerep <party> <amount>`
- `/party setrep <party> <amount>`
- `/party resetquest`
- `/party reload`
- `/partydaily resetall`
- `/partywar start [durasi] [target] [prepare]`
- `/partywar finish`
- `/partywar cancel`
- `/partyseason status|top|start|end`

## Reward Party War v1.1.0

Plugin hanya memberikan `war.party-xp` kepada Party pemenang. Tidak ada chest/item otomatis dan tidak ada Party Hall. Senjata/custom item diberikan manual oleh admin ke perwakilan team.
