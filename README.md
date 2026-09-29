# Nocturne — IPTV Player

A lean Android IPTV player with a horror-house aesthetic. Browse your playlists
like a channel guide and zap between streams in a fullscreen, flickering,
scanline-and-vignette player.

> **Nocturne ships empty.** It hosts, indexes, and provides no streams. You load
> your own M3U/M3U8 playlists and XMLTV guide, and you are responsible for the
> content you access and for having the rights to view it.

## Features

- **Playlist import** — from a URL, a local file, or raw pasted text.
  Forgiving M3U parser handles `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`,
  `#EXTGRP`, BOMs, and both quoted attribute styles.
- **Default channels** — on first run the app pulls a bundled channel list so
  the vault is never empty. Re-pull it any time with **Load default channels**.
- **Formats** — HLS (`.m3u8`), MPEG-TS (`.ts`), DASH (`.mpd`), and progressive
  (`.mp4`) via Android Media3 / ExoPlayer; RTMP via the media3 RTMP extension;
  `tvbus` / `mitv` / `p8p` / `vjms` through pluggable bridges (see below).
- **Browsing** — channel list with logos, group filters, search, favourites
  ("Vault"), and recents ("Recent"). Each row shows its transport
  (`HLS`, `RTMP`, `TVBUS`…) and a source count when a channel has backups.
- **Preview monitor** — a muted 16:9 mini-player pinned at the top of the
  channel list, tuned to your last-watched channel. Tap it to go fullscreen.
- **Multi-source channels** — when a playlist lists the same channel on
  several servers, Nocturne folds them into one row. The player slips to the
  next source automatically when one dies; the **SRC n/m** chip cycles manually.
- **EPG** — load an XMLTV URL for now/next titles in the list and the player.
- **Horror theme** — animated title-card intro, glitch text, flickering
  scanlines, film-grain noise, vignette, and a static-flash "dip" when you zap
  channels.

## Project layout

```
app/src/main/java/com/nocturne/iptv/
├── MainActivity.kt              Navigation host + intro gate
├── NocturneApp.kt               Application + manual DI container
├── data/
│   ├── Models.kt                Channel, PlaylistSource, EpgProgram
│   ├── M3uParser.kt             M3U/M3U8 parser
│   ├── XmltvParser.kt           Streaming XMLTV parser
│   ├── EpgLookup.kt             now/next queries against EPG state
│   ├── NetworkClient.kt         OkHttp fetcher
│   ├── NocturneStore.kt         DataStore persistence
│   └── NocturneRepository.kt    State holder + import pipeline
├── player/
│   ├── PlaybackSession.kt       Shared zap queue
│   ├── PlayerActivity.kt        ExoPlayer + horror controller UI
│   └── source/
│       ├── Transports.kt        Scheme labels + MediaItem hints
│       ├── NativeBridges.kt     tvbus/mitv/p8p/vjms bridge registry
│       └── NocturneDataSource.kt Scheme-routing DataSource factory
└── ui/
    ├── NocturneViewModel.kt
    ├── components/              ChannelRow, PreviewBar, horror effects
    ├── screens/                 Home, AddPlaylist, Settings, Intro
    └── theme/                   Palette, typography, Material theme
```

## Building the APK (no local Android SDK)

This repo builds in the cloud through GitHub Actions — no Android Studio or SDK
install required on your machine.

1. Create an empty repository on GitHub (do **not** initialise it with a README).
2. From this project folder:

   ```bash
   git init
   git add .
   git commit -m "Nocturne IPTV player"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```

3. Open the repo's **Actions** tab. The **Build APK** workflow runs on push (and
   can be re-run any time with **Run workflow**).
4. When it finishes (~3–5 min), download the **NocturneIPTV-apk** artifact from
   the run summary. Inside is `NocturneIPTV-<n>.apk`.

### Installing on a device

1. Copy the `.apk` to the phone.
2. Allow "Install unknown apps" for the file manager / browser you use.
3. Tap the APK to install.

The debug build is signed with the standard debug key, which is fine for
personal sideloading. For Play Store distribution you will need a release
keystore and a `release` signing config.

## Using it

1. **Add a source** (the red `SOURCE` button).
   - **URL** — e.g. a provider playlist link ending in `.m3u` / `.m3u8`.
   - **Paste** — drop raw `#EXTM3U…` text.
   - **File** — pick a local `.m3u` / `.m3u8`.
2. Optionally add an **XMLTV EPG URL** in the same sheet.
3. Tap any channel to play. Use the on-screen **CH+ / CH-** rails to zap.
4. Star a channel to keep it in the **Vault**.

## Notes & limitations

- Cleartext HTTP is permitted because many IPTV sources are plain HTTP. If you
  only use HTTPS sources you can tighten
  `res/xml/network_security_config.xml`.
- Streams that are dead, geo-blocked, or use unsupported codecs will show a
  themed error card with a retry action.
- When a channel has several sources, the player tries them in order before
  giving up — the **SRC n/m** chip shows where you are.
- Pasted playlists are held in memory; re-import them to reload after a restart.
  URL and file sources are re-fetched on demand.

## Proprietary transports (`tvbus` / `mitv` / `p8p` / `vjms`)

These schemes belong to their vendors, so stock ExoPlayer cannot play them.
Nocturne routes every one of them through `player/source/NativeBridges.kt`:

- `rtmp://` works today via the bundled media3 RTMP extension.
- Until a vendor SDK is bundled, each exotic scheme fails fast with a card
  that names the missing SDK instead of a cryptic error.
- Got an SDK? Its usual shape is a tiny HTTP proxy on `127.0.0.1`. Subclass
  `ProxyRewriteBridge` with the proxy port, then call
  `NativeBridges.register(…)` once at startup — the player picks it up with
  zero changes to playback code.

## Tech

Kotlin · Jetpack Compose (Material 3) · Media3/ExoPlayer · OkHttp · Coil ·
DataStore · Navigation Compose