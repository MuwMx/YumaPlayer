<div align="center">

  <h1>ArchiveTune Core</h1>

  <p align="center">
    <strong>InnerTube API client for YouTube Music.</strong>
    <br />
    <em>The core library powering <a href="https://github.com/ArchiveTuneApp/ArchiveTune">ArchiveTune</a> — a high-performance, privacy-focused YouTube Music client for Android.</em>
  </p>

  <p align="center">
    <img src="https://img.shields.io/github/v/release/ArchiveTuneApp/core?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="Latest Version" />
    <img src="https://img.shields.io/github/license/ArchiveTuneApp/core?style=for-the-badge&color=6366f1&labelColor=1e1e2e" alt="License" />
    <img src="https://img.shields.io/badge/Language-Kotlin-7f52ff?style=for-the-badge&logo=kotlin&color=6366f1&labelColor=1e1e2e" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Runtime-JVM-6366f1?style=for-the-badge&logo=openjdk&labelColor=1e1e2e" alt="JVM" />
    <img src="https://img.shields.io/github/stars/ArchiveTuneApp/core?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="Stars" />
  </p>

  <a href="https://star-history.com/#ArchiveTuneApp/core&ArchiveTuneApp/ArchiveTune&Date">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ArchiveTuneApp/core,ArchiveTuneApp/ArchiveTune&type=Date&theme=dark" />
      <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=ArchiveTuneApp/core,ArchiveTuneApp/ArchiveTune&type=Date" />
      <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=ArchiveTuneApp/core,ArchiveTuneApp/ArchiveTune&type=Date" width="600" />
    </picture>
  </a>

</div>

## Overview

This is the standalone InnerTube API core extracted from [ArchiveTune](https://github.com/ArchiveTuneApp/ArchiveTune). It provides a complete Ktor-based HTTP client for interacting with YouTube Music's InnerTube API, including request signing, response parsing, proxy rotation, and playback authentication.

## Features

- **Full API Coverage** — search, browse, library, playlist management, playback, and account interactions
- **Ktor Client** — built on Ktor with OkHttp engine, content negotiation, brotli encoding, and DNS-over-HTTPS
- **Response Parsing** — complete set of Kotlinx Serialization models for InnerTube responses
- **Page Parsers** — domain-level parsers that transform raw JSON into typed page objects
- **Proxy Rotation** — built-in rotating proxy selector with cooldown tracking for failed proxies
- **Playback Auth** — PO token management for authenticated playback
- **NewPipe Integration** — optional cipher deobfuscation and stream URL extraction via NewPipe Extractor

## Architecture

The diagram below shows how this library fits into the ArchiveTune app and how data flows through the layers.

```mermaid
flowchart TB
    subgraph Android["ArchiveTune App (Android)"]
        UI["Jetpack Compose UI<br/>Screens & Components"]
        VM["ViewModels<br/>State holders"]
        SVC["Services<br/>MusicService, Player"]
        DB["Room Database<br/>Local cache"]
    end

    subgraph Core["core (this library)"]
        YT["YouTube.kt<br/>High-level API singleton"]
        IT["InnerTube.kt<br/>Ktor HTTP client"]
        MB["MusicBackend.kt<br/>API contract"]

        subgraph Models["Models"]
            REQ["Request bodies<br/>(SearchBody, PlayerBody, ...)"]
            RES["Response models<br/>(PlayerResponse, BrowseResponse, ...)"]
        end

        subgraph Pages["Pages"]
            PARSERS["Page parsers<br/>(AlbumPage, ArtistPage, SearchPage, ...)"]
        end

        subgraph Proxy["Proxy"]
            RPS["RotatingProxySelector<br/>IP rotation with cooldown"]
            RPC["RotatingProxyClient<br/>Proxy list fetcher"]
        end

        AUTH["PlaybackAuthState<br/>PO token & cookie management"]
        UTILS["Utils"]
    end

    subgraph External["External"]
        YTM["YouTube Music<br/>InnerTube API"]
        NEWPIPE["NewPipe Extractor<br/>Cipher / stream URL"]
        BANDCAMP["Bandcamp / SoundCloud<br/>(via NewPipe)"]
    end

    UI --> VM --> SVC
    SVC --> YT
    YT --> IT
    YT --> MB
    MB --> IT
    IT --> Models
    IT --> Pages
    IT --> AUTH
    IT --> Proxy
    IT --> UTILS
    IT -->|HTTP / Ktor| YTM
    IT -->|Stream decryption| NEWPIPE
    NEWPIPE --> BANDCAMP
    VM --> DB
```

**Data flow:**
1. User interacts with ArchiveTune's Compose UI
2. ViewModels & Services call `YouTube.*` methods
3. `YouTube` delegates to `InnerTube` via the `MusicBackend` interface
4. `InnerTube` builds signed requests, sends them via Ktor to YouTube Music's InnerTube API
5. Raw JSON responses are deserialized into typed response models
6. Page parsers transform structured responses into domain page objects
7. For playback, stream URLs are decrypted via NewPipe Extractor (optional)

## Package Structure

```
com.muwmx.yuma.innertube/
├── InnerTube.kt              — Core HTTP client
├── YouTube.kt                — High-level API singleton (main entry point)
├── MusicBackend.kt           — API contract interface
├── PlaybackAuthState.kt      — Authentication state model
├── SearchFilter.kt           — Search filter parameter helpers
├── LibraryFilter.kt          — Library filter parameter helpers
├── models/                   — Response data models (JSON deserialization targets)
│   ├── body/                 — Request body models
│   └── response/             — Response wrapper models
├── pages/                    — Page parsers (response-to-domain transformation)
├── proxy/                    — Proxy rotation and configuration
└── utils/                    — Shared utilities
```

## Dependencies

- **Ktor Client** 3.5.0 — HTTP client core, OkHttp engine, content negotiation, brotli encoding, JSON serialization
- **OkHttp** 5.3.2 — DNS-over-HTTPS support
- **Kotlinx Serialization** — JSON deserialization
- **NewPipe Extractor** 0.26.2 — stream URL extraction, cipher deobfuscation, Bandcamp/SoundCloud search
- **re2j** 1.8 — Google RE2 regular expressions
- **Rhino** 1.9.1 — JavaScript engine (cipher operations)

## Usage

```kotlin
// Search
val results = YouTube.search("query", SearchFilter.SONGS)

// Browse
val home = YouTube.home()
val album = YouTube.album("browse_id")
val artist = YouTube.artist("browse_id")
val playlist = YouTube.playlist("playlist_id")

// Player
val player = YouTube.player("video_id", "playlist_id", YouTubeClient.WEB)

// Playlist management
YouTube.createPlaylist("My Playlist", "description")
YouTube.addToPlaylist("playlist_id", listOf("video_id"))

// Auth
YouTube.authState = PlaybackAuthState(cookie = "...", visitorData = "...")
```

## License

[GNU General Public License v3.0](LICENSE)
