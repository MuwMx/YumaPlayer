# YumaPlayer Module Directory & Dependency Graph

This document serves as the official reference for the modular structure of YumaPlayer. It defines module categories, layer responsibilities, and explicit dependency rules to maintain architectural integrity and prevent circular dependencies.

---

## 1. Module Design Principles

- **Single Responsibility:** Each module has a focused purpose and encapsulates a single logical domain or subsystem.
- **Explicit Interfaces:** Modules communicate only through exposed domain interfaces or public API surfaces.
- **Feature Isolation:** Feature modules must remain strictly isolated from each other.
- **Centralized Core:** Shared business logic, domain entities, and common utilities belong in `:core:*`.
- **Acyclic Graph:** Circular dependencies between modules are strictly prohibited.

---

## 2. Architecture & Category Hierarchy

YumaPlayer consists of **19 Gradle modules** (verified against `settings.gradle.kts`). Dependencies point inward toward shared core abstractions. There are no `:feature:*`, `:service:*`, `:core:model`, `:core:domain`, `:core:data`, or `:data` modules — those names are reserved for a possible future split (see §6).

```
                 ┌──────────────┐
                 │    :app      │ (Composition Root: UI, playback, domain, DI)
                 └──────┬───────┘
                        │
 ┌──────────────────────┼──────────────────────────────────┐
 ▼                      ▼                                  ▼
┌──────────────┐ ┌──────────────┐ ┌───────────────────────────────────────┐
│:designsystem │ │  :database  │ │ Integrations & Lyrics                 │
│ (YDS tokens, │ │ (Room, v36) │ │ (:lyrics:* ×7, :spotifycore,          │
│  UI Kit)     │ │              │ │  :shazamkit, :canvas, :lastfm,        │
└──────────────┘ └──────────────┘ │  :flaccore)                           │
                                  └───────────────────┬───────────────────┘
                                                      ▼
                         ┌────────────────────────────────────────────┐
                         │  :core + :core:innertube (shared logic,    │
                         │   InnerTube API client, math, packed models)│
                         └──────────────────────┬─────────────────────┘
                                                ▼
                         ┌────────────────────────────────────────────┐
                         │            Low-Level Data Engines            │
                         │       (:moriextractor, :morideobfuscator)   │
                         └────────────────────────────────────────────┘
```

---

## 3. Module Categories & Responsibilities

### 📱 Application Root
- **`:app`**
  - **Responsibility:** Application entry point, Hilt composition root (`di/AppModule.kt`, `di/NetworkModule.kt`, `di/RepositoryModule.kt`), navigation graph, all Compose screens (`ui/screens/`, `ui/player/player_0/`), background playback (`playback/MusicService.kt` + `MusicService*.kt` splits), colocated domain packages (UseCases, repositories under `artist/`, `search/`, `library/`, `spotify/`, …), and ViewModels.
  - **Rule:** Composition root. No other module may depend on `:app`.

### 🎨 Design System
- **`:designsystem`**
  - **Responsibility:** YDS tokens (`ui/settings/SettingsDimensions.kt`, `ui/settings/SettingsAnimations.kt`, `ui/theme/YdsInsets.kt`, `ui/theme/YumaTheme.kt` / `LocalYumaColors`), primitive modifiers (`ui/theme/YumaModifiers.kt`: `yumaGlassCard`, `yumaClickable`, `yumaSegmentPosition`), and the Yuma UI Kit (`ui/component/`: preferences, `YumaMorphingHeader`, `FloatingNavigationToolbar`, `GlassScaffold`, shimmer placeholders, `YumaHaptics`).
  - **Rule:** Pure UI, no business logic, no dependency on `:app` or `:database`.

### 💾 Persistence
- **`:database`** (`namespace moe.rukamori.archivetune.database`, Room `CURRENT_VERSION = 36`)
  - **Responsibility:** Room facade (`db/MusicDatabase.kt`), `db/entities/` (33 files), KSP schema snapshots (`schemas/`).
  - **Rule:** Entities must not leak into Composables unmapped (known debt, see `LEGACY_WARNING.md` §B6).

### 🔌 Integrations & Extensions
- **`:lyrics:*`** — 7 standalone providers: `:lyrics:lrclib`, `:lyrics:kugou`, `:lyrics:paxsenix`, `:lyrics:simpmusic`, `:lyrics:betterlyrics`, `:lyrics:unison`, `:lyrics:youlyplus`. Each implements the shared domain provider contract.
- **`:spotifycore`** — Spotify metadata, sync helpers, and video-loop asset pipeline inputs.
- **`:shazamkit`** — Audio recognition engine integration.
- **`:canvas`** — Video background rendering engine.
- **`:lastfm`** — Scrobbling integration and metadata synchronization.
- **`:flaccore`** — Lossless FLAC domain (`FlacConfig`, `FlacKvStore`, `qbdlx/`: `QbdlxSigner`, `QbdlxCredentialStore`, `QbdlxPoolProvider`, `QbdlxQobuzSource`, `streaming/` resolvers). Implemented via `FlacConfigImpl` / `FlacKvStoreImpl` in `:app` (`lossless/`).

### ⚙️ Core Infrastructure
- **`:core`** — Shared pure-Kotlin logic: `core/common/math/` (`lerp3`, palette/color/image math), `core/model/packed/` models.
- **`:core:innertube`** — InnerTube API client for YouTube Music (`InnerTube.kt`, `YouTube.kt` facade, `utils/`, `pages/`, `models/`, `proxy/`).
- **Responsibility:** Shared domain abstractions, common models, and application-wide utilities.
- **Rule:** Single source of truth for shared contracts; `:core` stays free of Android UI imports.

### 🛠️ Low-Level Engines
- **`:moriextractor`** — Media extraction engine and stream link resolution utilities.
- **`:morideobfuscator`** — Low-level code execution engine for decoding streaming signatures.
- **Rule:** Isolated data components. Independent of Android UI and upper layers.

---

## 4. Layer Dependency Rules

| Category / Layer | Allowed Dependencies | Forbidden Dependencies |
| :--- | :--- | :--- |
| **Application Root** (`:app`) | `:designsystem`, `:database`, `:core`, `:core:innertube`, Integrations, `:lyrics:*`, low-level engines | None (Root container) |
| **Design System** (`:designsystem`) | Design/token dependencies only | `:app`, `:database`, business logic, data sources |
| **Persistence** (`:database`) | Internal Room/KSP dependencies only | `:app`, `:designsystem`, network clients |
| **Integrations & Lyrics** | `:core` (+ `:database`/`network` only via domain interfaces where applicable) | `:app`, `:designsystem`, direct dependencies between independent integration modules |
| **Core Infrastructure** (`:core`, `:core:innertube`) | `:moriextractor`, `:morideobfuscator` | `:app`, `:designsystem`, `:database`, Integrations, `:lyrics:*` |
| **Low-Level Engines** | Internal utility dependencies only | Higher-level modules (`:core`, `:app`, etc.) |

---

## 6. Reserved Target Names (Not Real Modules)

The names `:feature:*`, `:service:*` (e.g. `:service:playback`), `:core:model`, `:core:domain`, `:core:data`, `:core:network`, `:core:database`, and `:data` do **not** exist in `settings.gradle.kts`. They describe a possible future split (UI features, playback service, domain/data layers) and must not be referenced as real dependency targets in code or new docs. Until the split happens, their responsibilities live inside `:app` (screens, `playback/`, colocated domain packages) and `:core` / `:core:innertube` / `:database`.

---

## 5. Anti-Patterns & Enforcement Rules

1. **Cross-Provider / Cross-Feature Imports:** Direct imports between `:lyrics:*` submodules or between distinct `:feature:*` modules are forbidden. Shared functionality must be hoisted to `:core:*`.
2. **Circular Dependencies:** Module A depending on Module B while Module B depends on Module A is strictly blocked at the build system level.
3. **Leaky Integration Models:** Exposing third-party DTOs or network response models directly to UI features instead of mapping them through Domain models defined in `:core:*`.
4. **Shared Utility Duplication:** Common utilities, models, or helpers must not be duplicated across modules. Shared functionality belongs in `:core:*`.
