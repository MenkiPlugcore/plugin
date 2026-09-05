# MenkiPlugcore Plugins

Koleksi plugin Minecraft custom buatan **MenkiPlugcore / MENKIESTES**.

Repository ini menggunakan struktur monorepo: setiap plugin berada di folder terpisah dan dapat dibuild secara mandiri. Tujuannya supaya source code, riwayat versi, CI build, dan distribusi tetap rapi saat jumlah plugin bertambah.

## Plugin Catalog

| Plugin | Versi | Platform | Java | Status |
|---|---:|---|---:|---|
| [MENKIAFK](plugins/MENKIAFK) | 1.1.0 Universal | Paper 1.21.11–26.2, Spigot 1.21.11 | 21+ | Aktif |
| [MoonSignMenu](plugins/MoonSignMenu) | 1.2.0 | Paper 1.21.11 + Geyser/Floodgate | 21+ | Aktif |
| [MENKIESTESParty](plugins/MENKIESTESParty) | 1.0.0 Core | Paper 1.21.x / 1.21.11 | 21+ | Aktif |

## Struktur

```text
plugin/
├── plugins/
│   ├── MENKIAFK/
│   ├── MoonSignMenu/
│   └── MENKIESTESParty/
│       ├── src/
│       ├── build.gradle.kts
│       ├── settings.gradle.kts
│       ├── README.md
│       └── CHANGELOG.md
└── .github/workflows/
```

## Build

Setiap plugin dapat dibuild dari folder masing-masing. Plugin Maven menggunakan `mvn clean package`, sedangkan MENKIESTESParty menggunakan Gradle:

```bash
cd plugins/MENKIESTESParty
gradle clean build
```

GitHub Actions melakukan compile-check dan menghasilkan artifact JAR untuk plugin yang memiliki workflow.

## Distribusi

Source repository ini disiapkan sebagai sumber resmi plugin MenkiPlugcore. Binary release dapat dipublikasikan melalui GitHub Releases dan/atau Modrinth tanpa menyimpan file hasil build ke dalam source tree.

## Lisensi

Belum ada lisensi open-source yang diberikan untuk repository ini. Hak cipta tetap berada pada pemilik repository sampai lisensi eksplisit ditambahkan.
