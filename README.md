# MenkiPlugcore Plugins

Koleksi plugin Minecraft custom buatan **MenkiPlugcore / MENKIESTES**.

Repository ini menggunakan struktur monorepo untuk plugin yang masih dikelola bersama. Plugin yang sudah berkembang menjadi proyek mandiri dapat dipindahkan ke repository standalone agar source code, CI build, release, dan dokumentasinya lebih terisolasi.

## Plugin Catalog

| Plugin | Versi | Platform | Java | Status |
|---|---:|---|---:|---|
| [MENKIAFK](plugins/MENKIAFK) | 1.1.0 Universal | Paper 1.21.11–26.2, Spigot 1.21.11 | 21+ | Aktif |
| [MoonSignMenu](plugins/MoonSignMenu) | 1.3.0 | Paper 1.21.11 + Geyser/Floodgate + optional EssentialsX/AxTrade | 21+ | Aktif |
| [MENKIESTESParty](https://github.com/MenkiPlugcore/MENKIESTESParty) | 2.1.0 | Paper 1.21.11 | 21+ | Standalone |

## Struktur

```text
plugin/
├── plugins/
│   ├── MENKIAFK/
│   └── MoonSignMenu/
└── .github/workflows/
```

MENKIESTESParty sekarang dikelola terpisah di:

https://github.com/MenkiPlugcore/MENKIESTESParty

## Build

Setiap plugin di repository ini dapat dibuild dari folder masing-masing sesuai build system-nya. MENKIESTESParty memiliki pipeline build dan release sendiri di repository standalone.

GitHub Actions melakukan compile-check dan menghasilkan artifact JAR untuk plugin yang memiliki workflow.

## Distribusi

Source repository ini tetap menjadi sumber resmi untuk plugin yang tercantum di monorepo. MENKIESTESParty menggunakan repository standalone sebagai source dan release resminya.

## Lisensi

Repository ini menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Hak cipta source original MENKIESTES tetap milik **CADERA**, sementara komponen pihak ketiga tetap mengikuti lisensi masing-masing.
