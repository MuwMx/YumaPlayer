# LEGACY_WARNING — Fragile Zone Registry and Agent Freeze Policy

**Status:** Mandatory.
**Purpose:** Registry of code that must not be refactored autonomously. This file is a shield against unsanctioned refactoring. It lists concrete mines: historical workarounds, god-objects, and ancestor legacy that is stable but fragile.

> **FREEZE POLICY:**
> Agents are STRICTLY FORBIDDEN from refactoring, splitting, renaming, relocating, or "improving" any entity listed in this registry while executing feature tasks. If you discover a bug or edge case inside a frozen zone, append a record using the format from Section 1 and continue with the original task. Do not touch the code without a direct task specification from the developer.

All paths below were verified by direct directory and file reads. Cross-references to ADR-009 / ADR-010 / ADR-011 are marked explicitly.

---

## 1. Tech-Debt Reporting Format (Mandatory)

When an agent finds an issue inside a frozen zone, it must not fix it. It must append a single-line record:

```text
[TECH-DEBT] <module/file>: <issue> | trigger: <conditions> | risk: <failure_mode> | do_not_touch_until: <condition>
```

Example:

```text
[TECH-DEBT] :moriextractor/SignatureParser: fails on new sts format | trigger: 403 from InnerTube | risk: silent fallback to Opus | do_not_touch_until: manual analysis of the new format
```

---

## 2. Section A — Ancestral Legacy (ArchiveTune / SimpMusic / Metrolist)

### A1. Root ancestor package `moe.rukamori.archivetune`
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/` (42 subdirectories: `playback/`, `db/`, `viewmodels/`, `ui/`, `spotify/`, …), `core/src/main/kotlin/moe/rukamori/archivetune/innertube/`
- **Debt:** Historic ArchiveTune package name permeates DI, navigation (`archivetune://login`), `MusicService`, and `MainActivity`. Renaming breaks the Hilt graph, deep links, and Room migrations.
- **Do not touch:** Hundreds of imports, `SessionToken(ComponentName(MusicService))`, external intent filters.
- **Refactor criterion:** Standalone package-rename task with a migration script plus full regression on deep links and MediaSession.

### A2. InnerTube client and monolithic `YouTube.kt` singleton
- **Paths:** `core/src/main/kotlin/moe/rukamori/archivetune/innertube/InnerTube.kt`, `core/src/main/kotlin/moe/rukamori/archivetune/innertube/YouTube.kt` (~2677 lines), `core/src/main/kotlin/moe/rukamori/archivetune/innertube/utils/`, `core/src/main/kotlin/moe/rukamori/archivetune/innertube/pages/`, `core/src/main/kotlin/moe/rukamori/archivetune/innertube/models/`, `core/src/main/kotlin/moe/rukamori/archivetune/innertube/proxy/`
- **Debt:** Monolithic Ktor client with mutable `authState` (`cookie`, `poToken`, `visitorData`, `dataSyncId`), `httpClient` recreation inside the `proxy` setter, and page parsers (`HomePage`, `AlbumPage`, `SearchPage`). Inherited from ViMusic / Metrolist / SimpMusic. Any YouTube format change breaks everything at once.
- **Do not touch:** `HomeViewModel`, `MusicService`, and `PlayerViewModel` call `YouTube.*` directly; half of the methods have no interface seam.
- **Refactor criterion:** Extract a `MusicBackend` interface for all call sites plus contract tests on JSON fixtures; only then split `YouTube.kt`.

### A3. Low-level engines `:moriextractor`, `:morideobfuscator`, headless BotGuard WebView
- **Paths:** `moriextractor/src/main/kotlin/moe/rukamori/archivetune/moriextractor/StreamingExtractionManager.kt`, `moriextractor/src/main/kotlin/moe/rukamori/archivetune/moriextractor/BackendExtractorResponse.kt`, `morideobfuscator/` (module), `app/src/main/kotlin/moe/rukamori/archivetune/utils/potoken/BotGuardTokenGenerator.kt`, `app/src/main/kotlin/moe/rukamori/archivetune/App.kt` (`BotGuardTokenGenerator.initialize` / `preWarm`)
- **Debt:** Obfuscated stream deciphering plus PoToken generation inside a headless WebView (~50 MB, released in `onAppBackgrounded`). Initialized in `App.initializeCriticalSync()` via `MoriCipherRuntime.initialize(cacheDirectory=noBackupFilesDir/mori_cipher, proxyProvider={YouTube.streamProxy})`. Fragile coupling: proxy or `visitorData` rotation invalidates the cache (`YTPlayerUtils.clearPlaybackAuthCaches()`).
- **Do not touch:** A mistake produces a silent fallback to YouTube or permanent `STATE_BUFFERING`; WebView leaks are invisible to unit tests.
- **Refactor criterion:** Isolate behind a `StreamingResolver` interface plus an integration rig with recorded and replayed challenges. See also ADR-009.

### A4. YouTube cookie plus Spotify `sp_dc` dual token lifecycle
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/spotify/SpotifySync.kt` (`refreshToken` / `ensureToken`), `app/src/main/kotlin/moe/rukamori/archivetune/utils/DataStore.kt`, `app/src/main/kotlin/moe/rukamori/archivetune/App.kt` (`collect{it.toPlaybackAuthState()}`), `app/src/main/kotlin/moe/rukamori/archivetune/viewmodels/HomeViewModel.kt` (`prepareYouTubeAccount` / `refreshAccountIdentity`)
- **Debt:** Dual authentication model: YouTube cookie maps to `PlaybackAuthState` with fingerprint comparison; Spotify `sp_dc` / `sp_key` maps to `SpotifyAuth.fetchAccessToken`. Tokens live in DataStore, expiry uses a manual 60s grace (`TOKEN_EXPIRY_GRACE_MS`). Logic is scattered across `App`, `HomeViewModel`, and `SpotifySync`.
- **Do not touch:** Account-switch races desynchronize `YouTube.authState` from the Spotify token; `HomeViewModel` assigns `YouTube.cookie=cookie` in try/catch without rollback.
- **Refactor criterion:** Single `AuthRepository` with `StateFlow<AuthState>` and atomic rotation, covered by a double-refresh test.

### A5. Qobuz lazy providers and synchronous `runBlocking` in Hilt DI (ADR-009)
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/lossless/FlacConfigImpl.kt` (suspend `qbdlxTokenPool()` from DataStore), `app/src/main/kotlin/moe/rukamori/archivetune/di/AppModule.kt:225` (`provideQbdlxCredentialStore` calls `runBlocking { config.qbdlxTokenPool() }`), `app/src/main/kotlin/moe/rukamori/archivetune/playback/resolvers/StreamUrlCache.kt`, `app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt` (`losslessUrlCache`)
- **Debt (ADR-009):** Qobuz clients receive lazy `() -> String` token providers; an empty string means qbdlx is disabled. However, `QbdlxPoolProvider` in the Hilt graph performs `runBlocking` on the main DI path. URL cache holds 256 entries with a 60s safety margin; the FLAC resolve timeout was raised from 2500ms to 10000ms.
- **Do not touch:** Replacing `runBlocking` with suspend breaks the synchronous `QbdlxCredentialStore` constructor; changing cache TTL reintroduces the silent fallback to YT_MUSIC.
- **Refactor criterion:** Migrate `QbdlxCredentialStore` to a suspend factory / AssistedInject and move `StreamUrlCache` into `:core` with expiry unit tests.

### A6. SpotifySync Mutex double-checked locking and 50-item batching (ADR-011)
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/spotify/SpotifySync.kt:35,329-356` (`scope=SupervisorJob+IO`, `tokenMutex`, `syncLikeForSongs` with `chunked(50)`), `app/src/main/kotlin/moe/rukamori/archivetune/utils/SyncLikes.kt:66-110` (`chunked(8)` for YTM, `LikeSourceResolver`), `app/src/main/kotlin/moe/rukamori/archivetune/utils/SyncUtils.kt` (central facade)
- **Debt (ADR-011):** Fire-and-forget IO scope, `refreshToken` under `Mutex.withLock` with `expiresAt` re-check, batched `addToLibrary` / `removeFromLibrary` calls of up to 50 URIs, `SpotifySyncLikesKey=false` gate by default, direct `SpotifySync` calls from UI / `MusicService` forbidden — routing only through `SyncUtils.likeSong` / `likeSongs`.
- **Do not touch:** Removing the mutex causes refresh storms on parallel likes; changing chunk size triggers Spotify 429; bypassing `SyncUtils` causes double sync.
- **Refactor criterion:** Move into `:spotifycore` behind a `TokenRefresher` interface plus a concurrent-refresh test (N coroutines collapse into 1 network call).

---

## 3. Section B — God-Objects and Architecture Debt

### B1. `MusicService.kt` monolithic service
- **Path:** `app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt` (+ `MusicServiceAudio.kt`, `MusicServicePlayback.kt`, `MusicServiceCrossfade.kt`, `MusicServiceIntegrations.kt`, `MusicServiceTogether.kt`, `MusicServiceWidgetUpdater.kt`)
- **Debt:** One class owns ExoPlayer plus a Cast wrapper, four URL caches (`playbackUrlCache`, `losslessUrlCache`, `extractorPlaybackUrlCache`, `contentLengthCache`), crossfade with `secondaryCrossfadePlayer`, Discord / loudness / EQ / audio-focus / Bluetooth / wakelock handling, Together sessions, history (`PendingHistoryFinalization`), a `runBlocking` import, `ResolvingDataSource`, and two `OkHttpClient` instances (media + extractor). The split into `MusicService*.kt` files is physical only; state is still shared via `internal var`.
- **Do not touch:** `onCreate` ordering (player -> session -> `ensureStartedAsForeground` -> DataStore collect) is fragile; reordering throws `ForegroundServiceStartNotAllowedException` or drops the queue.
- **Refactor criterion:** Extract `PlaybackEngine`, `UrlResolveCache`, `AudioEffectsController`, and `PresenceController` as interfaced classes; keep `MusicService` as a thin `MediaLibraryService` facade.

### B2. `MusicDatabase.kt` Room facade (v36)
- **Path:** `app/src/main/kotlin/moe/rukamori/archivetune/db/MusicDatabase.kt` (1184 lines, `CURRENT_VERSION=36`), `app/src/main/kotlin/moe/rukamori/archivetune/db/entities/` (33 files)
- **Debt:** Wrapper over `InternalDatabase` with `query{}` / `transaction{}` on fixed 4+4 pools, `UniversalMigration` via in-memory `expectedDb + SchemaTools.reconcileDatabase`, `fallbackToDestructiveMigration`, `PRAGMA` tuning, `cleanupDuplicatePlaylistsOnOpen` with raw SQL in `onOpen`, and `MIGRATION_1_2` with a manual SimpMusic schema port. Every schema deviation is healed by drop and recreate.
- **Do not touch:** Editing `reconcileDatabase` / `ensureTableSchema` destroys the user library; `fallbackToDestructiveMigration` hides migration errors.
- **Refactor criterion:** Pin a schema snapshot (`schemas/`) with tests, remove the destructive fallback, and cover migrations 33→36 with autotests on real `.db` files.

### B3. Direct service exposure via `PlayerConnection`
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/playback/PlayerConnection.kt` (306 lines: `database`, `service.player`, `service.currentMediaMetadata`, `toggleLike→service.toggleLike`, Together gates), `app/src/main/kotlin/moe/rukamori/archivetune/playback/PlayerConnectionHolder.kt` (`MutableStateFlow<PlayerConnection?>`)
- **Debt:** UI holds a direct `MusicService` reference via `binder.service`; `MainActivity.onServiceConnected` fills and clears the holder. `PlayerConnection` mixes Room (`database.song` / `format` / `lyrics`), player state, and Together roles, violating the service→UI boundary from `MODULES.md`.
- **Do not touch:** Replacing it with an interface without proxying every `MutableStateFlow` breaks 10+ subscribers in `PlayerViewModel` (`flatMapLatest` on `connection.*`).
- **Refactor criterion:** Narrow `PlayerController` interface (`mediaMetadata`, `isPlaying`, `queueWindows`) plus a fake for Compose previews and tests.

### B4. `PlayerViewModel.kt` hub (812 lines)
- **Path:** `app/src/main/kotlin/moe/rukamori/archivetune/ui/PlayerViewModel.kt` (`LyricsDelegate`, `ProgressTicker`, `handleAction(PlayerAction)`, `handleDeepLinkAction`, `requestSheetCollapse`)
- **Debt:** 12+ `connectionHolder.connection.flatMapLatest` subscriptions, palette state (`vibrantColor` / `darkMuted` / `gradient` via `PreferenceStore` + DataStore), queue, shuffle / repeat, sleep timer, deep links (`YouTube.queue` / `playlist` / `albumSongs`), and lyrics / slider delegation to external callback helpers. UDF is formally observed, but the class is the coupling point of the whole player.
- **Do not touch:** `resetLyrics()` ordering on `trackUrl` change and `manageTicker(isPlaying)` are tied to a metadata-vs-playbackState race; refactoring desynchronizes lyrics.
- **Refactor criterion:** Split into `PlayerMetadataViewModel`, `LyricsViewModel`, and `QueueViewModel` over a shared `PlayerSession`; trigger when one new `PlayerAction` requires edits in 3+ places.

### B5. `HomeViewModel.kt` boot god-object (996 lines)
- **Path:** `app/src/main/kotlin/moe/rukamori/archivetune/viewmodels/HomeViewModel.kt` (`quickPicks` / `speedDial` / `forgottenFavorites` / `keepListening` / `similarRecommendations` / `accountPlaylists` / `homePage`, `delay(150)`, `delay(3000)`, `delay(100)`)
- **Debt:** Room queries (`quickPicks()`, `mostPlayedSongs` / `Albums` / `Artists`, `forgottenFavorites`), network (`YouTube.home` / `artist` / `next` / `related` / `library`), AI filtering, artist blocklists, account channels, SpeedDial pins, and a `supervisorScope` with four parallel launches are mixed. `screenState` combines seven flows.
- **Do not touch:** `quickPicksWithFallback` (related→recent→all) and `previousHomePage` for chips; any reordering yields an empty Home while offline.
- **Refactor criterion:** Extract `HomeLocalDataSource`, `HomeRemoteDataSource`, and `SimilarRecommendationsUseCase`; trigger on repeated `load()` failures during chip rotation.

### B6. Room Entity leaks into Compose
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/db/entities/Song.kt` (`@Immutable data class Song(@Embedded song: SongEntity, artists, album, format)`), `app/src/main/kotlin/moe/rukamori/archivetune/viewmodels/HomeViewModel.kt:100-105,152-159` (`ImmutableList.copyOf(quickPicks: List<Song>)` inside `HomeUiState`), `app/src/main/kotlin/moe/rukamori/archivetune/ui/screens/HomeScreen.kt`, `app/src/main/kotlin/moe/rukamori/archivetune/spotify/SpotifySync.kt:69-108` (`SongEntity` in signatures), `app/src/main/kotlin/moe/rukamori/archivetune/playback/PlayerConnection.kt:68-79` (`database.song` / `format` / `lyrics` directly in Flows)
- **Debt:** Room entities (`Song`, `SongEntity`, `Album`, `Artist`) flow into Composables and the sync layer without Domain / UI mapping, directly violating ARCHITECTURE (UI Stateless → Domain) and `MODULES.md` §5.3 (Leaky Integration Models).
- **Do not touch:** Introducing mappers now breaks `distinctUntilSongIdsChanged`, `filterBlockedArtists`, and `liked` / `likedSpotify` flags across dozens of screens.
- **Refactor criterion:** Introduce `SongUiModel` plus a ViewModel-boundary mapper; start with one screen (`HomeScreen`) behind a flag and compare scroll performance before and after.

---

## 4. Section C — Temporary Workarounds and Hot Fixes

### C1. `DataStore.get` via `runBlocking` plus `PreferenceStore` cache
- **Path:** `app/src/main/kotlin/moe/rukamori/archivetune/utils/DataStore.kt:101-126` (`runBlocking(Dispatchers.IO){withTimeoutOrNull(1500){data.first()}}`, `if (Looper.main==currentThread) return default`), `App.kt` (`PreferenceStore.start(this)`)
- **Debt:** Synchronous DataStore access from Java-style code (`enumPreference`, `preference`, `AppModule.providePlayerCache`). On Main it silently returns `null` / default; off Main it blocks up to 1.5s. `PreferenceStore` is a global `MutableStateFlow<Preferences?>` without TTL.
- **Do not touch:** Migrating to suspend breaks `by enumPreference` in `MusicService` and `rememberPreference`; removing the Main guard causes cold-start ANR before `PreferenceStore.start`.
- **Refactor criterion:** Forbid sync `get` in new code via a detekt rule, keep only `getAsync` / Flow; trigger when all `dataStore.get` calls disappear from `:app`.

### C2. Hardcoded boot and load delays
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/viewmodels/HomeViewModel.kt:457` (`delay(150)` before load), `:939` (`delay(3000)` before `cleanupDuplicatePlaylists`), `:967` (`delay(100)` before `refreshAccountIdentity`), `app/src/main/kotlin/moe/rukamori/archivetune/ui/PlayerViewModel.kt:486` (`delay(150)` in `requestSheetCollapse`), `app/src/main/kotlin/moe/rukamori/archivetune/MainActivity.kt:757-760` (`while playerConnection==null delay(100)`; `delay(500)` before update check), `app/src/main/kotlin/moe/rukamori/archivetune/App.kt:324` (`Thread.sleep(100)` before `killProcess`)
- **Debt:** Delays mask races: DataStore collect not ready, service not bound, sheet not collapsed. Magic 100 / 150 / 500 / 3000ms values were tuned empirically.
- **Do not touch:** Removing `delay(150)` in `HomeViewModel.load()` causes double loading (the `isLoading` flag lags); removing `delay(500)` in `MainActivity` shows the update sheet over the splash.
- **Refactor criterion:** Replace with explicit readiness signals (`queueRestoreCompleted.first{}`, `isReady`, `snapshotFlow`); remove one at a time with low-RAM cold-start verification.

### C3. Splash / DI / DataStore init race
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/MainActivity.kt:568-570` (`setKeepOnScreenCondition{isOnboardingCompleted==null||!isReady}`), `:628-634` (`SplashVectorLoader.loadPath` on `Dispatchers.Default`), `:802-806` (`dataStore.data.first` + `snapshotFlow{splashEnabled}` → `isReady=true`), `app/src/main/kotlin/moe/rukamori/archivetune/App.kt:162-238` (`initializeDeferredAsync`: proxy / DoH / locale / BotGuard preWarm), `app/src/main/kotlin/moe/rukamori/archivetune/di/AppModule.kt:47-139` (`LazyCache` with `synchronized` + `@Volatile`)
- **Debt:** Splash is held until the first DataStore read; `LazyCache` defers `SimpleCache` creation to avoid reading `MaxSongCacheSizeKey` inside the DI graph. Parallel `applicationScope.launch(IO)` calls in `App` run without a readiness barrier.
- **Do not touch:** Eager cache init means `runBlocking` in `providePlayerCache` and ANR; premature `isReady=true` flashes theme and locale.
- **Refactor criterion:** Explicit `StartupGate` (suspend `awaitReady()`), replace `LazyCache` with a Hilt `Provider<Cache>`; trigger on stable <2s cold start on low-RAM devices without `LazyCache`.

### C4. Compose 120fps workarounds: draw-phase fraction reads only (ADR-010 §1)
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/ui/player/player_0/UnifiedPlayerSheetLayers.kt:150-217` (`expansionFractionProvider()` / `lyricsFractionProvider()` / `queueFractionProvider()` only inside `graphicsLayer{}`), `app/src/main/kotlin/moe/rukamori/archivetune/ui/player/player_0/UnifiedPlayerSheetV2.kt:164-168,215-232` (`snapshotFlow{expansionFraction.value}`, `snapTo(0f)` when `fraction==0f`), `app/src/main/kotlin/moe/rukamori/archivetune/ui/player/player_0/PlayerSeekBar.kt:65-77` (fixed `delay(250)` ticker instead of per-frame subscription)
- **Debt (ADR-010):** Reading `Animatable.value` in a Composable body recomposes on every gesture frame. All fractions therefore live exclusively in `graphicsLayer` / `drawWithContent`; tickers are fixed at 250ms; `snapshotFlow` is the only bridge outward.
- **Do not touch:** Moving a fraction into `derivedStateOf`, text, or alpha outside `graphicsLayer` drops 120Hz screens toward 30fps.
- **Refactor criterion:** Only together with sheet-physics changes and a Perfetto trace proving <8ms frames; otherwise the ADR-010 §5 freeze applies.

### C5. Discrete `BackHandler` flags instead of thresholds (ADR-010 §2)
- **Path:** `app/src/main/kotlin/moe/rukamori/archivetune/ui/player/player_0/UnifiedPlayerSheetV2.kt:363-375` (`BackHandler(enabled=isLyricsVisible||isQueueVisible)`, inner check `lyricsFraction.value>0.01f||isLyricsVisible`), `PlayerSheetPredictiveBackHandler(enabled=EXPANDED&&!isLyricsVisible&&!isQueueVisible)`
- **Debt (ADR-010):** Subscribing BackHandler to a continuous `fraction>0.05f` threshold or scattering handlers across `QueueScreen` / `LyricsColumn` produces phantom back events mid-swipe. The anchor is strictly the discrete `isQueueVisible` / `isLyricsVisible` flags from `PlayerUiState` in one place.
- **Do not touch:** A second `BackHandler` in child screens double-closes queue plus lyrics on a single back press.
- **Refactor criterion:** Only when migrating to a next-generation predictive-back API while preserving the "one BackHandler per sheet" invariant.

### C6. `CompositingStrategy` and sheet layers (ADR-010 §3–4)
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/ui/player/player_0/UnifiedPlayerSheetLayers.kt`, `app/src/main/kotlin/moe/rukamori/archivetune/ui/player/player_0/UnifiedPlayerSheetV2.kt:402-447` (dim background in a separate `Box` plus sibling content, `dynamicShape` with a radius fix at `>0.99f`), `app/src/main/kotlin/moe/rukamori/archivetune/MainActivity.kt:128` (`CompositingStrategy`)
- **Debt (ADR-010):** Permanent `Offscreen` allocates an FBO on every gesture frame and stalls mobile GPUs. Rule: `Auto` while `fraction<0.99f`, `Offscreen` only at rest for fade masks. Border-trimmed background is an isolated `matchParentSize` underlay; content (`QueueScreen`, `LyricsColumn`) renders as siblings with `fillMaxSize`, otherwise touch coordinates break. Zeroing corner radius at `>0.99f` fixes a shape artifact.
- **Do not touch:** Forced `Offscreen` or `layout{placeRelative(-borderPx)}` on content means jank plus shifted queue taps.
- **Refactor criterion:** Only with GPU profiling (FrameTimeline) and the "Auto in motion, Offscreen at rest" invariant enforced in review.

### C7. Fixed-window 250ms progress tickers
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/ui/ProgressTicker.kt:26-42` (`while(isActive){currentPosition; delay(250)}`), `app/src/main/kotlin/moe/rukamori/archivetune/ui/player/player_0/PlayerSeekBar.kt:70-76` (duplicate `while+delay(250)` plus `snap()` at `<=500ms`), `app/src/main/kotlin/moe/rukamori/archivetune/ui/PlayerViewModel.kt:108` (`progressMsProvider`)
- **Debt:** Two independent pollers (`ProgressTicker` for lyrics and duration plus `PlayerSeekBar` for the slider) instead of a single `Player.listen`. `localSeekTarget` with a 1500ms window masks post-seek slider rollback.
- **Do not touch:** Merging tickers without `isUserSeeking` handling makes the slider jump during drag; shrinking to 50ms adds wasteful recompositions.
- **Refactor criterion:** Single `PlaybackProgressFlow` (200ms throttle, paused while `isUserSeeking`) with battery and lyric-highlight A/B validation.

### C8. `MusicService` playback quirks: caches, silence-skip vs offload, idle stop
- **Paths:** `app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt:362-451` (`audioQuality` / `preferredStreamClient`, four `ConcurrentHashMap` caches, two `OkHttpClient` instances with `StreamClientUtils.applyRequestProfile`), `:1200-1216` (offload / crossfade mutual exclusion, forced `SkipSilence=false` under offload), `:905-961` (`scheduleStopIfIdle` 30s / 60s), `:612-636` (`promptLoginRecovery` with 10s throttle plus `archivetune://login`)
- **Debt:** URL caches have no TTL manager (only targeted `invalidatePlaybackUrlCache`); offload is incompatible with skip-silence and crossfade and is disabled silently; idle-stop timers were hand-tuned; login recovery is throttled by a `(mediaId,timestamp)` pair.
- **Do not touch:** Enabling offload plus skipSilence produces silence or DSP crackle on some chips; shortening idle stop kills queue restoration (`PersistQueue`).
- **Refactor criterion:** `PlaybackPolicy` compatibility table (offload / crossfade / skipSilence) plus an expiring `UrlCache`; trigger on a reproducible chip-specific bug.
