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
- **Formats** — HLS (`.m3u8`), MPEG-TS (`.ts`), DASH (`.mpd`), and progressive
  (`.m3u`, `.mp4`) via Android Media3 / ExoPlayer.
- **Browsing** — channel list with logos, group filters, search, favourites
  ("Vault"), and recents ("Recent").
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
│   └── PlayerActivity.kt        ExoPlayer + horror controller UI
└── ui/
    ├── NocturneViewModel.kt
    ├── components/              ChannelRow, horror effects
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
- Pasted playlists are held in memory; re-import them to reload after a restart.
  URL and file sources are re-fetched on demand.

## Tech

Kotlin · Jetpack Compose (Material 3) · Media3/ExoPlayer · OkHttp · Coil ·
DataStore · Navigation Compose