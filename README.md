# AniReku

Aplikasi nonton anime untuk Android — **tanpa iklan, tanpa pelacak, open source (GPL-3.0)**.

Katalog dan metadata dari [AniList](https://anilist.co). Pemutar video berbasis AndroidX Media3
(ExoPlayer) dengan dukungan HLS dan pemilihan kualitas. Ada pembaruan otomatis lewat GitHub Releases.

> Aplikasi ini **tidak** meng-host, menyimpan, atau mendistribusikan video apa pun. Ia juga tidak
> membungkus layanan pihak lain. Tautan putar berasal dari *feed* yang kamu konfigurasi sendiri
> (JSON lokal atau URL publik) — lihat [Sumber video](#sumber-video).

## Status

| Bagian | Status |
|---|---|
| Katalog, pencarian, detail (AniList) | ✅ jalan |
| Pemutar HLS/MP4 + pilih kualitas | ✅ jalan |
| Simpan tontonan + lanjut dari posisi terakhir | ✅ jalan |
| Update otomatis (GitHub Releases) | ✅ jalan |
| Unit test | ✅ 18 test |
| Adapter sumber video publik siap pakai | ⬜ belum ada (feed manual) |

Build lokal terakhir: `assembleDebug` sukses, APK ~22 MB, minSdk 24.

## Fitur

- **Katalog AniList** — trending, musiman, per genre, pencarian. Tanpa API key.
- **Pemutar** — Media3 ExoPlayer, HLS adaptif, pemilih kualitas manual, kontrol gestur.
- **Lanjut tonton** — posisi terakhir disimpan di Room, dibuka lagi otomatis.
- **Pustaka** — bookmark dan riwayat, tersimpan lokal.
- **Update mandiri** — cek rilis GitHub, unduh APK, lalu serahkan ke installer sistem.
- **Tanpa iklan** — tidak ada SDK iklan, tidak ada analitik, tidak ada akun.

## Sumber video

AniList hanya menyediakan *metadata*, bukan video. Jadi aplikasi ini sengaja memisahkan dua hal:

```
Katalog (metadata, episode)  ──►  AnimeSource   (AniList)
Video (URL stream)           ──►  StreamFeed    (milikmu)
```

`StreamFeed` membaca JSON dengan bentuk seperti ini:

```json
{
  "anime": [
    {
      "anilistId": "21",
      "title": "One Piece",
      "episodes": [
        {
          "number": 1,
          "title": "Saya Luffy!",
          "streams": [
            { "label": "720p", "url": "https://contoh.test/ep1-720.m3u8", "type": "hls" },
            { "label": "1080p", "url": "https://contoh.test/ep1-1080.m3u8", "type": "hls" }
          ]
        }
      ]
    }
  ]
}
```

Feed bisa ditaruh di berkas lokal atau di URL HTTPS (mis. WebDAV, Gist, bucket sendiri). Karena
adapter-nya terbuka, siapa pun bisa menambah sumber lain tanpa menyentuh UI atau pemutar.

**Yang tidak dilakukan aplikasi ini:** tidak ada parser yang menambang stream dari situs pihak
ketiga, tidak ada penembusan gerbang berbayar, dan tidak ada kunci API layanan lain yang dibongkar.
Kalau kamu punya hak atas sebuah katalog, masukkan ke feed.

## Build

Butuh JDK 17+ dan Android SDK (platform 35, build-tools 34).

```bash
export ANDROID_HOME=$HOME/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug     # APK di app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest # 18 unit test
```

Rilis bertanda tangan: buat `keystore.properties` di root (lihat `app/build.gradle.kts`) lalu
`./gradlew :app:assembleRelease`. Berkas itu sudah masuk `.gitignore`.

## Update otomatis

`UpdateChecker` memanggil `https://api.github.com/repos/kannnnna9/AniReku/releases`, membandingkan
`tag_name` dengan `BuildConfig.VERSION_NAME` secara numerik (bukan string — `0.10.0` > `0.9.2`),
lalu mengunduh aset `.apk` lewat `DownloadManager` dan menyerahkannya ke installer sistem.

Keamanan: APK yang dipalsukan akan **gagal dipasang** karena sertifikatnya beda dengan aplikasi
terpasang. Tidak perlu logika pinning tambahan di sisi app.

Rilis otomatis: push tag `v0.2.0` → workflow `.github/workflows/build.yml` membangun APK dan
melampirkannya ke release.

## Struktur

```
app/src/main/java/com/anireku/app/
├── core/
│   ├── model/          AnimeSource (SPI), AnimeSummary, Episode, StreamLink
│   ├── database/       Room: bookmark, riwayat, unduhan
│   └── repository/     ContentRepository — satu pintu untuk sumber + cache
├── source/
│   ├── anilist/        AniListSource (GraphQL, retry+backoff, cache 6 jam)
│   └── feed/           StreamFeed, LocalFeedSource
├── ui/
│   ├── home/ search/ detail/ library/ settings/
│   ├── common/         AnimeGrid, PosterCard
│   └── theme/          Material 3
├── update/             UpdateChecker
└── PlayerActivity.kt   Media3 ExoPlayer + pemilih kualitas
```

Arsitektur: satu modul, MVVM, injeksi manual (`Graph.kt`), Kotlin coroutines, tanpa framework
DI. Semua dependensi di-pin di `gradle/libs.versions.toml`.

## Test

```
AniListSourceTest   5  — mapping GraphQL, HTML stripping, error 429/404
FeedParserTest      6  — skema feed, penolakan data cacat
UpdateCheckerTest   3  — parsing rilis GitHub, pemilihan aset
VersionCompareTest  4  — perbandingan versi numerik
```

## Lisensi

GPL-3.0 — lihat [LICENSE](LICENSE). Kalau kamu memodifikasi dan mendistribusikan ulang, kode
sumbermu juga harus dibuka dengan lisensi yang sama.

AniReku tidak berafiliasi dengan AniList maupun layanan streaming mana pun. Metadata adalah milik
AniList dan kontributornya.
