# Architectural Decision Records (ADRs)

This document records the major architectural, design, and technical decisions made for YumaPlayer. 
Each record captures the context, decision, rationale, and consequences of a key architectural choice.

---

## Index of Decisions

- **[ADR-001](#adr-001-material-3-expressive-ui-foundation)** — Material 3 Expressive as the UI Foundation
- **[ADR-002](#adr-002-hct-hue-chroma-tone-color-engine)** — HCT (Hue-Chroma-Tone) Color Engine for Dynamic Theming
- **[ADR-003](#adr-003-unidirectional-data-flow-udf--stateflow)** — Unidirectional Data Flow (UDF) & StateFlow
- **[ADR-004](#adr-004-androidx-media3-as-exclusive-playback-engine)** — AndroidX Media3 as Exclusive Playback Engine
- **[ADR-005](#adr-005-jetpack-compose-only)** — Jetpack Compose Only
- **[ADR-006](#adr-006-glass--translucency-effects-restricted-to-overlays)** — Glass & Translucency Effects Restricted to Overlays
- **[ADR-007](#adr-007-pure-kotlin-domain-layer)** — Pure Kotlin Domain Layer (Zero Android Framework Imports)
- **[ADR-008](#adr-008-multi-module-clean-architecture)** — Multi-Module Clean Architecture
- **[ADR-009](#adr-009-lossless-flac-streaming-playback)** — Lossless FLAC Streaming Playback
- **[ADR-010](#adr-010-120fps-gesture-kinematics--player-sheet-layer-architecture)** — 120fps Gesture Kinematics & Player Sheet Layer Architecture
- **[ADR-011](#adr-011-spotify-sync-architecture)** — Spotify Sync Architecture
- **[ADR-012](#adr-012-room-persistence-extracted-into-database-module)** — Room Persistence Extracted into `:database` Module
- **[ADR-013](#adr-013-dual-player-crossfade-engine)** — Dual-Player Crossfade Engine

---

## ADR-001: Material 3 Expressive UI Foundation

- **Status:** Accepted
- **Context:** Music players require distinct visual identity, high contrast legibility, and rich motion while maintaining standard Android accessibility.
- **Decision:** Adopt Material 3 Expressive principles as the foundation for Yuma Design System (YDS 2.1). Customize shapes (squircled corners, custom radii), elevation, and expressive motion curves.
- **Consequences:**
  - *Positive:* Unique music-centric aesthetic, smooth spring animations, unified design tokens.
  - *Negative:* Most UI components should be built through Yuma UI Kit wrappers rather than consuming raw Material 3 components directly.

---

## ADR-002: HCT (Hue-Chroma-Tone) Color Engine

- **Status:** Accepted
- **Context:** Album art covers vary dramatically in color palette. RGB/HSL tinting often results in muddy UI colors or poor contrast against text.
- **Decision:** Use Google's HCT (Hue, Chroma, Tone) color model to dynamically extract dominant album colors and map them to YDS surface tokens designed to maintain accessible contrast ratios.
- **Consequences:**
  - *Positive:* Perceptually accurate color extraction, automatic high-contrast light/dark surfaces.
  - *Negative:* Requires CPU-bound palette extraction (must run off the Main thread on background dispatchers).

---

## ADR-003: Unidirectional Data Flow (UDF) & StateFlow

- **Status:** Accepted
- **Context:** Complex UI state (loading, error, queue updates, lyrics sync) can easily lead to state desynchronization and race conditions if mutated from multiple sources.
- **Decision:** Enforce UDF across all screens. ViewModels expose a single immutable `StateFlow<UiState>` and process incoming user actions via sealed intent interfaces (`UiIntent`).
- **Consequences:**
  - *Positive:* Single source of truth, predictable state transitions, seamless UI testability.
  - *Negative:* Requires creating explicit intent classes and state wrappers for every screen flow.

---

## ADR-004: AndroidX Media3 as Exclusive Playback Engine

- **Status:** Accepted
- **Context:** Background audio playback must support system media controls, notification actions, Android Auto, lockscreen controls, and ExoPlayer customization.
- **Decision:** Build playback infrastructure strictly around AndroidX Media3 (`MediaSessionService`, `ExoPlayer`, `MediaController`).
- **Consequences:**
  - *Positive:* Seamless system integrations, official Media3 cache data source support, decoupled UI from playback lifecycle.
  - *Negative:* Inter-process communication complexity between UI `MediaController` and background `MediaSession`.

---

## ADR-005: Jetpack Compose Only

- **Status:** Accepted
- **Context:** XML layout inflation adds view hierarchy overhead and complex state synchronization code.
- **Decision:** YumaPlayer UI is declarative Jetpack Compose. XML-based UI layouts are not used for new development.
- **Consequences:**
  - *Positive:* Concise layout code, reactive UI state binding, shared YDS design token system.
  - *Negative:* `AndroidView` interop is strictly restricted to specialized surface engines (e.g., video canvas playback).

---

## ADR-006: Glass & Translucency Effects Restricted to Overlays

- **Status:** Accepted
- **Context:** Overusing glassmorphism (translucency + background blur) causes high GPU render latency and poor text legibility on low-end devices.
- **Decision:** Glass effects are strictly restricted to Overlay surfaces (mini-player bar, bottom sheets, dialogs, floating headers). Main content backgrounds must use solid YDS surface tones.
- **Consequences:**
  - *Positive:* Preserves GPU performance, guarantees text legibility on main screens.
  - *Negative:* Limits design freedom for main content areas.

---

## ADR-007: Pure Kotlin Domain Layer

- **Status:** Accepted
- **Context:** Coupling business logic to Android framework APIs makes unit testing slow and breaks multiplatform / modular architecture potential.
- **Decision:** Domain code (colocated `:app` domain packages plus shared `:core` contracts) must remain pure Kotlin with zero imports from `android.*` packages.
- **Consequences:**
  - *Positive:* Fast JVM unit testing (no Robolectric needed), clean separation of concerns.
  - *Negative:* Android Context utilities must be wrapped in domain interfaces implemented in `:data` or `:app`.

---

## ADR-008: Multi-Module Clean Architecture

- **Status:** Accepted
- **Context:** A monolithic application module leads to slow build times, tight coupling, and uncontrolled dependencies.
- **Decision:** Split YumaPlayer into 19 isolated Gradle modules (`:app`, `:designsystem`, `:database`, `:core`, `:core:innertube`, `:lyrics:*` ×7, `:canvas`, `:spotifycore`, `:shazamkit`, `:lastfm`, `:flaccore`, `:moriextractor`, `:morideobfuscator`) with inward dependency rules toward domain core.
- **Consequences:**
  - *Positive:* Parallel Gradle builds, strict feature isolation, modular provider system.
  - *Negative:* Requires managing build logic across multiple module definitions.

---

## ADR-009: Lossless FLAC Streaming Playback

- **Status:** Accepted
- **Context:** Lossless (FLAC) playback relied on a fixed quality tier and silent fallback to YouTube whenever lossless resolution exceeded a 2.5s timeout. Qobuz API clients were configured at DI time with placeholder token values, so direct lossless sources could not work out of the box.
- **Decision:**
  - Add `PlaybackSource` (`YT_MUSIC` / `FLAC`) and `FlacQuality` (CD / HI_RES / MAX) enums in `constants/`. The lossless resolver chain is invoked only when the selected source is `FLAC`; `EnableLosslessKey` mirrors `source == FLAC`.
  - Hybrid token handling: Qobuz API clients receive `() -> String` token providers that read the current DataStore setting lazily at call time, returns empty string when setting empty (qbdlx disabled until user provides token). No blocking DataStore read happens in the DI graph.
  - Cache resolved stream URLs in `StreamUrlCache` (256 entries) inside `MusicService` and raise the lossless resolve timeout from 2500 ms to 10000 ms so the FLAC chain succeeds instead of silently falling back to YouTube.
  - Player settings expose user-selectable source/quality and the lossless-only fields (memory, folder, tokens) are shown only when `FLAC` is active; the FLAC folder picker persists the granted URI permission via `takePersistableUriPermission`.
  - FLAC downloads are enqueued as unique WorkManager jobs (`flac_download_<songId>`, `ExistingWorkPolicy.KEEP`); the URL is resolved inside `FlacDownloadWorker.doWork()` through `FlacDownloaderEntryPoint`, with HTTP headers/timeouts and `sanitizeFileName` applied via `SafDirectoryManager`.
- **Consequences:**
  - *Positive:* Lossless streaming works with public proxies (squid/kennyy) without filling in credentials; user-provided tokens take priority over built-in defaults; previously-cached URLs are reused without a new HTTP resolve until their expiry minus a safety margin.
  - *Negative:* Direct Qobuz provider (qbdlx) still requires real tokens to function.

---

## ADR-010: 120fps Gesture Kinematics & Player Sheet Layer Architecture

- **Status:** Accepted
- **Context:** The player bottom sheets (Lyrics & Queue) require buttery-smooth 120fps physics-driven gestures. Common Compose pitfalls (reading animation fractions during composition, subscribing `BackHandler` to continuous floating-point thresholds, forcing continuous `CompositingStrategy.Offscreen` during gestures, and embedding content inside custom layout shift modifiers) introduce frame drops, composition thrashing, and GPU memory bandwidth bottlenecks.
- **Decision:**
  1. **Strict Draw-Phase Reading (Zero Composition Overhead):** Continuous animation fractions (`queueFraction.value`, `lyricsFraction.value`, `fractionProvider()`) must NEVER be read in the body of Composable functions. All fractional values must be observed exclusively inside Draw-phase blocks (`graphicsLayer { ... }` or `drawWithContent { ... }`), keeping the Composition tree completely static during gestures.
  2. **Discrete BackHandler Binding:** Do not bind `BackHandler` to continuous thresholds (`fraction > 0.05f`) or place separate dynamic `BackHandler` instances inside child screens (`QueueScreen`, `LyricsColumn`). Back handling must be anchored centrally in `UnifiedPlayerSheetV2` using discrete boolean flags (`isQueueVisible`, `isLyricsVisible`).
  3. **Adaptive CompositingStrategy on Lazy Lists:** Continuous `CompositingStrategy.Offscreen` allocates and renders to an offscreen GPU FBO every frame, which throttles mobile GPUs during gestures. During active swipe movement (`fraction < 0.99f`), lists must use `CompositingStrategy.Auto`. `CompositingStrategy.Offscreen` is engaged strictly when the layer is statically resting in fully-opened state (`fraction >= 0.99f`) to apply top/bottom edge fade masks.
  4. **Isolated MatchParentSize Background Underlays:** Trimming lateral borders via negative layout translation (`layout { placeRelative(-borderPx, 0) }`) must be performed inside a dedicated underlay `Box(Modifier.matchParentSize().sheetBackground())`. Content composables (`QueueScreen`, `LyricsColumn`) render as siblings on top with standard `Modifier.fillMaxSize()`, ensuring clean, undistorted touch and layout coordinates.
  5. **Agent Freeze Policy:** Automated agents are strictly prohibited from refactoring or modifying swipe physics, gesture handlers, or sheet layers (`UnifiedPlayerSheetV2`, `UnifiedPlayerSheetLayers`, `QueueScreen`, `LyricsColumn`) unless explicitly ordered by the user with exact specifications.
- **Consequences:**
  - *Positive:* Rock-solid 120fps gesture fluidity with 0ms startup delay, zero layout re-computations during drag, and optimal GPU resource utilization.
  - *Negative:* Layer composition and background trimming require strict structural discipline.

---

## ADR-011: Spotify Sync Architecture

- **Status:** Accepted
- **Context:** Liking, unliking, or adding songs to library in YumaPlayer can optionally mirror into the user's Spotify account when tracks originate from or link to Spotify. Synchronous requests, blocking WorkManager overhead, repeated single-item network calls (N+1 queries), and unguarded sync calls can degrade UI responsiveness or trigger rate limits.
- **Decision:**
  1. **Fire-and-Forget IO Scope:** Use an asynchronous, non-blocking coroutine scope `CoroutineScope(SupervisorJob() + Dispatchers.IO)` in `SpotifySync`. Network sync runs completely decoupled from local database writes, never blocking Room transactions, UI threads, or requiring heavy WorkManager jobs.
  2. **Thread-Safe Session Renewal with Double-Checked Locking:** Token management in `SpotifySync.refreshToken` executes inside `tokenMutex.withLock` with a double-check pattern. Before initiating network token requests, the method re-verifies whether another concurrent coroutine has already refreshed `SpotifyAccessTokenKey` and `SpotifyAccessTokenExpiresAtKey`.
  3. **Batched Library Mutations (Elimination of N+1):** Multi-item operations in `syncLikeForSongs` group tracks by `liked` status and send batched requests (`Spotify.addToLibrary(chunk)` / `Spotify.removeFromLibrary(chunk)`) in chunks of up to 50 Spotify URIs per call.
  4. **Strict Isolation by Preference Toggle & Auth Guard:** All sync operations are guarded by `SpotifySyncLikesKey` (default `false`) configured in `AccountSettings.kt`. If the user has disabled the toggle or is not authenticated to Spotify, operations terminate early with zero network traffic.
  5. **Centralized Invocation Pipeline:** Direct calls to `SpotifySync` from UI components or `MusicService` are prohibited. All like/library synchronizations must route centrally through `SyncUtils.likeSong(song)` and `SyncUtils.likeSongs(songs)`.
- **Consequences:**
  - *Positive:* Instant local UI feedback, zero UI thread blocking, optimal network throughput with chunked batching, and full user control over background Spotify synchronization.
  - *Negative:* Spotify library state reflects changes asynchronously with minor eventual-consistency delay.

---

## ADR-012: Room Persistence Extracted into `:database` Module

- **Status:** Accepted
- **Context:** The Room persistence layer (`MusicDatabase`, 33 entity files, universal migration machinery) lived inside `:app` next to UI and playback code, coupling the user library to the composition root's build and lifecycle.
- **Decision:** Extract persistence into a dedicated `:database` Android library module (`namespace moe.rukamori.archivetune.database`, Room `CURRENT_VERSION = 36`, KSP schema snapshots under `database/schemas/`). `:app` consumes it as a library dependency; entities and migrations evolve behind the module boundary.
- **Consequences:**
  - *Positive:* Library storage builds and versions independently of UI/playback; schema snapshots are pinned per Room version via KSP.
  - *Negative:* `fallbackToDestructiveMigration` and `UniversalMigration` reconciliation now span a module boundary, so schema mistakes surface as cross-module migration failures (see `LEGACY_WARNING.md` §B2).

---

## ADR-013: Dual-Player Crossfade Engine

- **Status:** Accepted
- **Context:** Single-player crossfade mechanisms in ExoPlayer face inherent limitations including track transition stutter, buffer starvation, seeking race conditions, and inability to decode two streams concurrently. Seamless overlap transitions require two distinct player pipelines without breaking MediaSession continuity, PlayerConnection contracts, or audio focus.
- **Decision:**
  1. **Dual Engine Roles (`MASTER` / `STANDBY`):** Introduce `DualPlayerRoleHolder` managing `MASTER` and `STANDBY` player roles. The master player maintains active `MediaSession` integration and UI bindings, while the standby player pre-warms the next media item at zero volume (`volume = 0f`, `pauseAtEndOfMediaItems = true`).
  2. **Equal-Power Volume Ramping:** Execute smooth volume transitions between outgoing and incoming players using calculated ramps, maintaining acoustic power consistency without distortion or clipping.
  3. **Atomic Handoff & Role Swap:** At the handoff boundary, verify buffer readiness via `canHandoffWithoutRebuffer` and `awaitPrimaryCrossfadeHandoffReady`. Handoff state atomically, swap roles (`dualPlayerRoleHolder.swap()`), and release the secondary player (`releaseSecondaryCrossfadePlayer()`) without session re-instantiation.
  4. **Contract Isolation & Legacy Fallback:** Maintain the exact public API surface of `PlayerConnection`. Crossfade durations `<= 0L` safely route through `shouldUseLegacyPath`, bypassing dual-engine allocation.
- **Consequences:**
  - *Positive:* Perfectly smooth, gapless crossfade transitions with zero audio artifacts, uninterrupted MediaSession lifetime, and complete UI contract preservation.
  - *Negative:* Temporary dual-decoder memory and CPU overhead during active crossfade ramp intervals.

