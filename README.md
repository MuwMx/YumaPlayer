<div align="center">

  <img src="assets/ic_launcher.webp" width="240" height="240" alt="YumaPlayer Icon" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.3);" />

  <h1>YumaPlayer (Yuma)</h1>

  <p align="center">
    <a href="README.md">
      <img src="https://img.shields.io/badge/🇺🇸%20English-6366f1?style=for-the-badge&labelColor=1e1e2e" alt="English" />
    </a>
    <a href="README_RU.md">
      <img src="https://img.shields.io/badge/🇷🇺%20Русский-6366f1?style=for-the-badge&labelColor=1e1e2e" alt="Russian" />
    </a>
  </p>

  <p align="center">
    <strong>A hybrid music client for Android focused on usability, clean aesthetics, and audiophile-grade sound.</strong>
    <br />
    <em>Spotify UI + Discovery Algorithms + YouTube Music Library + Hi-Res Lossless (FLAC).</em>
  </p>

  <p align="center">
    <a href="#-showcase"><b>Showcase</b></a> •
    <a href="#-key-features"><b>Features</b></a> •
    <a href="#️-architecture--documentation"><b>Architecture</b></a> •
    <a href="#-download--installation"><b>Download</b></a> •
    <a href="#-support-the-project"><b>Support</b></a>
  </p>

  <div>
    <img src="https://img.shields.io/github/v/release/MuwMx/YumaPlayer?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="Latest Release" />
    <img src="https://img.shields.io/github/stars/MuwMx/YumaPlayer?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="GitHub Stars" />
    <img src="https://img.shields.io/github/downloads/MuwMx/YumaPlayer/total?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="Downloads" />
    <img src="https://img.shields.io/badge/License-GPLv3-6366f1?style=for-the-badge&logo=gnu&logoColor=white&labelColor=1e1e2e" alt="License: GPLv3" />
    <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=1e1e2e" alt="Android 8.0+" />
    <a href="https://t.me/yumaplayer"><img src="https://img.shields.io/badge/Telegram-Community-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white&labelColor=1e1e2e" alt="Telegram" /></a>
  </div>

</div>

---

## 🌟 About the Project

**YumaPlayer** is an independent, open-source Android music player that unites the libraries and recommendation features of two leading streaming services alongside standalone local playback in Hi-Res quality.

The project evolved as a comprehensive rebuild of **ArchiveTune** (with foundations from **Metrolist** and **SimpMusic**), inspired by **PixelPlayer's** queue mechanics, **Meld's** Spotify integration pipeline, and **Stash's** FLAC architecture. On top of this base, YumaPlayer implements a modular 13-module architecture, the custom **YDS 2.1** design system, and hardware-accelerated fluid gesture physics.

No subscriptions. No advertisements. Zero telemetry, crash reporters, or third-party trackers. All credentials live exclusively on your device, encrypted with AES-256-GCM via Google Tink, and the codebase is completely open under the GNU General Public License v3.0.

---

## 📸 Showcase

<div align="center">
  <table>
    <tr>
      <td width="33%" align="center">
        <img src="assets/screenshots/fullplayer.png" alt="Full Player" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Main Player (FLAC & Glass)</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/lyrics_screen.png" alt="3-Tier Lyrics" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>3-Tier Synced Lyrics</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/queue.png" alt="Queue Management" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Queue (Dual-Sheet & Haptic)</b></sub>
      </td>
    </tr>
    <tr>
      <td width="33%" align="center">
        <img src="assets/screenshots/artist_screen.png" alt="Artist Header" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Immersive Artist Poster</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/artist_screen2.png" alt="Morphing Collapse" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Telegram-Style Morphing</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/main_screen.png" alt="Home Screen" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Home (Quick Picks & YDS)</b></sub>
      </td>
    </tr>
    <tr>
      <td width="33%" align="center">
        <img src="assets/screenshots/spotify.png" alt="Spotify Screen" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Spotify Feed & Recommendations</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/spotify_radio.png" alt="Spotify Radio" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Spotify Radio & Mixes</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/spotify_dailymix.png" alt="Spotify Daily" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Spotify Daily Mixes</b></sub>
      </td>
    </tr>
  </table>
</div>

---

## ✨ Key Features

### 🔄 Dual Mode (Spotify + YouTube Music)
* **Spotify Home:** Personalized recommendation feeds, Daily Mixes, Discover Weekly, favorite artist shelves, and direct import of your Spotify playlists and liked songs.
* **YouTube Music Home:** Full-catalog search across YouTube Music, charts, mood & genre categories, radio stations, and fast channel switching.

### 🎧 Audiophile Playback & Lossless FLAC
* **Lossless FLAC:** Stream and download true lossless FLAC tracks (via Qobuz / QBDLX backbone integration) using your own credentials or open community resolvers.
* **Seamless Fallback:** If a track is unavailable in lossless catalogs, playback instantly and silently routes through YouTube Music's high-bitrate stream (Opus / AAC).
* **Local Audio Engine:** Built-in player for standalone files stored on your device (FLAC, MP3, AAC, OGG, WAV).
* **Offline & Caching:** Automatic caching of played YouTube Music tracks for offline listening without recurring data usage *(FLAC offline caching in active development)*.
* **Audio Tuning:** Built-in 5-band equalizer with system audio effects support, EBU R128 loudness normalization, smooth crossfading, tempo and pitch adjustment, and a sleep timer.

### 🎤 Synchronized Lyrics & AI Translation
* **3-Tier Synchronized Display:** Simultaneous live display of original lyrics (Kanji / Hangul), pronunciation guide (Romaji), and translated lines.
* **Live Karaoke Scrolling:** Word-by-word and line-by-line sync pulled from 7+ providers (LRCLIB, Kugou, Paxsenix, BetterLyrics, SimpMusic, Unison, YouLyPlus).
* **On-the-Fly AI Translation:** Instant translation of lyrics into your preferred language right inside the player.
* **Lyrics Card Sharing:** Export and share formatted lyrics quote cards as clean images.

### 🎨 Yuma Design System (YDS 2.1) & Fluid Motion
* **Real Glassmorphism:** Translucent glass cards (`GlassScaffold`, 0.5dp hairline borders, composite 22dp / 5dp corner geometry).
* **Dual-Sheet Gesture Engine:** Independent, conflict-free player sheets (swipe up on the left for Lyrics, swipe up on the right for Queue).
* **Telegram-Style Morphing Header:** Smooth, scroll-linked transformation of artist posters into a status-bar drop using custom 3-point `lerp3` without image distortion.
* **Smart Freeze & Pre-warming:** Instant sheet expansion with 0-frame startup latency, while off-screen physics loops and background tasks suspend automatically (`snapshotFlow`) to preserve battery life and keep the device cool.
* **Dynamic Monet Theming:** Adaptive palette extraction powered by the currently playing track's artwork, tuned for strict WCAG contrast compliance.

### 🌐 Integrations & Convenience
* **Spotify Canvas & Apple Motion:** Looping video backgrounds during playback *(In development)*.
* **Music Recognition (Shazam):** Acoustic fingerprint search for songs playing around you *(In development)*.
* **Together (Listen Together):** Synchronized playback with friends over WebSockets.
* **Scrobbling:** Native Last.fm and ListenBrainz integration.
* **Discord Rich Presence:** Real-time presence status showing your current track and album art on Discord.
* **Android Auto & Widgets:** In-car playback support and home screen Glance widgets.

---

## 🌍 Globalization & Localization

YumaPlayer is localized into multiple languages thanks to community contributions (Thanks Archivetune!).

<div align="center">
  <img src="https://img.shields.io/badge/Translations-Community_Driven-6366f1?style=for-the-badge&logo=weblate&labelColor=1e1e2e" alt="Translation Status" />
</div>

> If you would like to contribute translations or improve existing language strings, join the discussion in our [Telegram Community](https://t.me/yumaplayer).

---

## 🏛️ Architecture & Documentation

YumaPlayer is structured into **13 independent Gradle modules** following Clean Architecture and Unidirectional Data Flow (UDF) principles:

```
┌─────────────────────────────────────────────────────────────┐
│                   :app (Composition Root)                   │
└──────────────────────────────┬──────────────────────────────┘
                               │
       ┌───────────────────────┼──────────────────────┐
       ▼                       ▼                      ▼
┌──────────────┐       ┌──────────────┐ ┌───────────────────────────┐
│  :feature:*  │       │  :service:*  │ │  :lyrics:*  │ :spotifycore│
│ (Compose UI) │       │  (Playback)  │ │  :canvas    │ :shazamkit  │
└──────┬───────┘       └──────┬───────┘ └─────────────┬─────────────┘
       │                      │                       │
       └──────────────────────┼───────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 :core:* (Domain, Data, Model)               │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Low-Level Media Extraction                  │
└─────────────────────────────────────────────────────────────┘
```

📖 **Detailed Developer Documentation:**
* [Architecture Overview](docs/architecture/ARCHITECTURE.md)
* [Modules Directory & Dependency Graph](docs/architecture/MODULES.md)
* [Architectural Decision Records (ADRs)](docs/architecture/DECISIONS.md)
* [Yuma Design System Specification (YDS 2.1)](docs/design/YDS.md) | [Russian Version](docs/design/YDS_ru.md)
* [Coding Standards & Rules (Yuma Rules)](docs/development/YUMA_RULES.md)

---

## 📥 Download & Installation

<div align="center">
  <table>
    <thead>
      <tr>
        <th align="center">GitHub Releases</th>
        <th align="center">Telegram Channel</th>
        <th align="center">Obtainium</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td align="center">
          <a href="https://github.com/MuwMx/YumaPlayer/releases/latest">
            <img src="assets/badge_github.png" height="50" alt="Download on GitHub" />
          </a>
        </td>
        <td align="center">
          <a href="https://t.me/yumaplayer">
            <img src="https://img.shields.io/badge/Telegram-Channel-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" height="40" alt="Get on Telegram" />
          </a>
        </td>
        <td align="center">
          <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/MuwMx/YumaPlayer">
            <img src="https://img.shields.io/badge/Obtainium-Auto_Updates-purple?style=for-the-badge" height="40" alt="Add to Obtainium" />
          </a>
        </td>
      </tr>
    </tbody>
  </table>
</div>

### Building from Source
```bash
git clone https://github.com/MuwMx/YumaPlayer.git
cd YumaPlayer
./gradlew assembleRelease
# Built APK will be located at: app/build/outputs/apk/release/
```
*Prerequisites:* Android Studio Ladybug (2024.2.1+) or newer, JDK 21, Android SDK 35+.

---

## ⚙️ Account Setup

YumaPlayer is fully functional out of the box without signing in. Logging in is optional and only required to sync personal playlists, likes, and recommendations:

<details>
<summary><b>🎵 Connecting Spotify</b></summary>

1. Open **Settings** → **Account** → **Spotify** → Tap **Log In**.
2. Sign in with your standard Spotify credentials in the secure in-app browser window.
3. Your session cookie (`sp_dc`) will be automatically captured and encrypted on your device.
</details>

<details>
<summary><b>📺 Connecting YouTube Music</b></summary>

Two convenient login methods are supported:
* **Method 1 (Google Account):** Open **Settings** → **Account** → **YouTube** → Sign in using the secure in-app authentication sheet.
* **Method 2 (Token / Cookie Input):** If you use two-factor authentication or prefer manual setup, navigate to **Settings** → **Account** → **Token Editor** and paste your session cookies directly.
</details>

---

## ☕ Support the Project

YumaPlayer is free, open source, and has no ads or subscriptions. If you enjoy using the player and wish to support ongoing development:

<div align="center">
  <a href="https://ko-fi.com/muwmix">
    <img src="assets/buymeacoffee.png" height="45" alt="Support on Ko-fi" />
  </a>
  <br /><br />
  <p>
    <b>Solana (SOL, USDT, USDC):</b><br />
    <code>DT3ckdbNuiQMR1mrCpBXCMrhLB19GckVv3YfxsLLiF8z</code>
  </p>
  <p>
    <b>PayPal / Email Transfer:</b><br />
    <code>muwmix.coffee@gmail.com</code>
  </p>
</div>

---

## 🤝 Acknowledgments (Open-Source Credits)

YumaPlayer is built on the shoulders of the open-source community:

* **[ArchiveTune](https://github.com/rukamori/ArchiveTune)** — for the solid playback foundations, rich settings base, and core architecture.
* **[Meld](https://github.com/FrancescoGrazioso/Meld)** — for the conceptual breakthrough and implementation of the Spotify hybrid integration.
* **[Stash](https://github.com/rawnaldclark/Stash)** — for inspiration in organizing lossless FLAC streaming and download pipelines.
* **[Metrolist](https://github.com/mostafaalagamy/Metrolist)** & **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)** — for early lyrics parsing and interface inspirations.
* **[PixelPlayer](https://github.com/theovilardo/PixelPlayer)** — for swipe kinematics ideas and queue structure.
* **[QBDLX](https://github.com/ImAiiR/QobuzDownloaderX)** — for the lossless audio streaming infrastructure.
* **[LRCLIB](https://lrclib.net/)** & **[BetterLyrics](https://better-lyrics.boidu.dev/)** — for the open synchronized lyrics databases.

---

## ⚖️ License & Legal Disclaimer

### License
YumaPlayer is free software licensed under the **GNU General Public License v3.0 (GPLv3)**.  
You may freely copy, modify, and redistribute the source code provided you preserve copyright notices and distribute derivative works under the same GPL-3.0 license.

### Disclaimer
YumaPlayer is an independent third-party client. It is **not affiliated with, endorsed by, or sponsored by Google LLC, YouTube Music, or Spotify AB**. The application is provided for personal use only as a tool for managing your own library.

---

<p align="center">
  Crafted with ❤️ and attention to every frame. Enjoy the music! 🎵
</p>
