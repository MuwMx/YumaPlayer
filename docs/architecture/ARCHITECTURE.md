# YumaPlayer Architecture Overview

This document outlines the architectural blueprint of YumaPlayer. The application is built following **Clean Architecture** principles combined with **Unidirectional Data Flow (UDF)**, ensuring high testability, feature isolation, and strict separation of concerns.

---

## 1. Core Architectural & Dependency Rules

To keep the codebase maintainable as it grows, all modules and layers must strictly follow these fundamental rules:

- **Dependencies Point Inward:** Outer layers depend on inner layers. The Domain layer is the central core of the application.
- **Inner Layers are Unaware of Outer Layers:** Core business logic has zero awareness of UI components, databases, or third-party SDKs.
- **Interface-Driven Communication:** Cross-layer interaction occurs exclusively through domain-defined interfaces.
- **Strict Layer Isolation (Neighbor-Only Rule):** Each layer communicates only with its immediate neighbor.
  - `UI / Presentation` ➔ `Domain (Use Cases / Models)`
  - `Domain` ➔ `Repository Interfaces`
  - `Data` ➔ `Repository Implementations`
  - **Rule:** The Presentation layer must **never** talk directly to the Data layer or data sources.
- **Data Model Ownership:** Every layer owns its specific models (Network DTOs, DB Entities, Domain Entities, UI States). Conversion occurs explicitly at layer boundaries via Mappers.

---

## 2. Layer Structure & Responsibilities

```
┌────────────────────────────────────────────────────────┐
│                   PRESENTATION LAYER                   │
│       Jetpack Compose UI  ◄──►  ViewModels (UDF)       │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                      │
│      Use Cases  │  Domain Entities  │  Interfaces      │
└───────────────────────────▲────────────────────────────┘
                            │
                            │ (Implements Interfaces)
┌───────────────────────────┴────────────────────────────┘
│                       DATA LAYER                       │
│   Repositories Impl  │  Data Sources  │  DTOs & Mappers│
└────────────────────────────────────────────────────────┘
```

### Presentation Layer (`:app` UI + `:designsystem`)
- **Responsibilities:** Declarative Jetpack Compose UI rendering, visual state representation, and user input handling.
- **Components:** Screens under `:app` (`ui/screens/`, `ui/player/player_0/`), Yuma UI Kit + YDS tokens in `:designsystem`, ViewModels (`StateFlow<UiState>`), `UiIntent`, `UiEffect`.

### Domain Layer (colocated `:app` domain packages + `:core` contracts + `:core:audio-dsp` DSP)
- **Responsibilities:** Pure business rules, application use cases, and core data structures.
- **Components:** Use Cases / Interactors colocated in `:app` domain packages (`artist/`, `search/`, `library/`, `spotify/`, `playlisttags/`, …), Domain Models, Repository & Service Interfaces, shared contracts in `:core` (`core/common/`, `core/model/`), domain-pure playback DSP in `:core:audio-dsp` (`TransitionPlanner`, `MusicServiceCrossfadeMath` + `CrossfadeConstants`, `DjFilterAudioProcessor`, forwarding set, `AudioDeck`/`DeckController` interfaces — see [MODULES.md](MODULES.md) §3 Core Infrastructure).

### Data Layer (`:core:innertube`, `:database`, `:app` data sources)
- **Responsibilities:** Fetching, caching, persisting, and transforming data from local databases and remote network APIs.
- **Components:** Repository Implementations, Room Entities in `:database` (`db/entities/`, `MusicDatabase.kt` v38), InnerTube client in `:core:innertube`, Ktor Data Sources, Network DTOs, Mappers.

### Playback Subsystem (`:app` `playback/` + `:core:audio-dsp`)
- **Responsibilities:** Android Media3 session lifecycle, audio focus and hardware effects, stream resolving (FLAC/YT/Extractor), queue orchestration, dual-engine crossfade, and background integrations.
- **Architecture (ADR-014):** Centered on `MusicService` as a thin coordinator delegating to four domain-isolated subsystem holders:
  - `playback/audio/ServiceAudioPolicyHolder`: Focus, audio device routing, mute recovery, normalization, and EQ effects.
  - `playback/engine/PlayerEngineHolder`: ExoPlayer instances, audio decks, connection pools, caches, load control, and renderers.
  - `playback/session/ServiceSessionHolder`: MediaLibrarySession, notifications, and foreground service management.
  - `playback/host/ServiceConfigCollector`: DataStore preferences, metadata state flows, and Together room/session state.
  - Stream resolving is isolated in specialist classes (`FlacPlaybackResolver`, `YtPlaybackResolver`, `DiskCacheDataSpecProbe`, `MemoryPlaybackUrlPolicy`, `PlaybackFormatPersist`, `ArchiveTuneExtractorSpec`, `PlaybackSpecSupport`). YouTube in-memory stream URLs are unified in `utils.StreamUrlCache` (keyed by `mediaId`, storing `url`, `itag`, expiry with 60s margin, and `authFingerprint`), shared between playback and prefetch pipelines.
  - Queue management is decoupled into `QueuePlayOrchestrator`, `QueueMutationCommands`, and `TogetherGuestCoordinator`. Together room coordination behavior (`applyHostControl`, `applyHostAddTrack`, `buildTogetherRoomState`, `applyRemoteRoomState`) is encapsulated in `playback/together/ServiceTogetherCoordinator`.
  - External integrations are isolated in `playback/discord/` and `playback/history/`. `ServicePlaybackHistoryCoordinator` owns the service-local history session state and pending jobs; `PlaybackHistoryTracker` and `PlaybackHistoryStore` retain tracking and persistence behavior.

#### Spotify Queue Lazy Matching (forward + backward)

`SpotifyTracksQueue`, `SpotifyLikedSongsQueue`, and `SpotifyPlaylistQueue` share `SpotifyLazyTrackMatcher` for resolving Spotify entries into Media3 items. Source adapters still own their transport (liked-tracks pages, playlist GraphQL pages, or a caller-provided custom order) and retain their public constructors.

- **Initial window:** the first `Queue.Status` resolves at most 5 loaded entries before the selected index, the selected entry, and 10 entries after it (11–16 items). The selected entry reuses the caller's `preloadItem` when present; the rest of the loaded collection is not resolved before the first status. The 5/10 split matches the existing playlist window and keeps more than the orchestrator's 5-remaining pagination trigger available ahead of playback.
- **Forward batches:** each `nextPage()` resolves at most 20 already-loaded entries. When no unmatched loaded entries remain, the queue fetches one source page of 50 entries first, then resolves its next batch. Both counts reuse the existing playlist resolve batch and Spotify page size.
- **Backward batches:** each `previousPage()` resolves at most 20 already-loaded entries immediately before the initial window start, then walks toward index 0. Within a batch entries stay in source order; the service prepends each batch at index 0, so repeated user requests rebuild ascending source order in the playlist. Backward matching never re-fetches earlier source pages — it only consumes the entries already loaded by the initial window and forward pagination — and stops deterministically at index 0 (`hasPreviousPage()` becomes false). `SpotifyQueue.PreviousPage` carries the matcher generation at resolution time; `SpotifyLazyTrackMatcher.reset()` increments it, so a batch resolved before a same-instance queue reinitialization is rejected rather than inserted into the fresh timeline.
- **Separate cursors:** the source fetch offset advances over raw API entries (including local entries filtered out of the queue), while the forward and backward matching cursors split the loaded range around the initial window. Appending a source page never rewinds either cursor or duplicates already returned entries.
- **Pagination contract:** `hasNextPage()`/`hasPendingContextItems` are true while unmatched loaded entries or source continuation remain. Unresolvable entries are skipped deterministically with the matching cursor advanced and a warning logged; an initial window with no resolvable entry sets `isLoadFailed`, while a fully unresolvable forward or backward batch returns an empty page and still advances the cursor, so pagination terminates.
- **Backward service path:** `MusicService.requestPreviousQueuePage()` → `MusicServicePlayerListeners.triggerBackwardPagination()` → `QueueBackwardPaging` is the only backward entrypoint. It runs on explicit user intent, never on timeline transitions or `onInfiniteQueueEnabled()`. Capability is exposed as `MusicService.hasPreviousQueuePage()` (Spotify queues only, including a retained batch) and loading as `MusicService.previousQueueLoading`. A batch is applied with `player.addMediaItems(0, items)` after the same `HideExplicit`/`HideVideo` filters as forward paging; the path issues no seek, prepare, play or pause request, so Media3 keeps the current media item identity, position and playback state. Concurrent backward requests are rejected (backpressure), a request for a different queue cancels the previous job, and results are discarded when the active queue changed or its generation advanced. If a resolved batch cannot be inserted because the queue is initializing or the player is temporarily idle, it is retained for the same queue and retried on the next explicit request instead of being dropped; the matching cursor is not advanced again for that retry. Forward and backward matching serialize on the queue's own `pagingMutex`; timeline mutation happens on the service main scope.
- **Limitations:** backward paging covers only the already-loaded Spotify prefix; it does not fetch Spotify source pages earlier than the initial fetch and does not apply to YouTube or local queues. Shuffle applies to the ordering supplied by the caller or the loaded subset, not to the complete remote collection, and a full-source shuffle of a 1300-track collection is not provided. The `QueuePlayOrchestrator` preload fast-path contract is unchanged.

---

## 3. Request & Data Flow Lifecycle

The following diagram illustrates the complete execution path of a single data request (e.g., searching for a track or loading lyrics):

```
User Action (Click / Input)
│
▼
┌───────────────┐        UiIntent        ┌───────────────┐
│ Compose UI    ├───────────────────────►│ ViewModel     │
└──────▲────────┘                        └───────┬───────┘
       │                                         │ Invokes
       │ UiState                                 ▼
┌──────┴────────┐  Domain Model          ┌───────────────┐
│ StateFlow     │◄───────────────────────┤ UseCase       │
└───────────────┘                        └───────┬───────┘
                                         │ Calls Interface
                                         ▼
┌───────────────┐  Domain Model          ┌───────────────┐
│ RepositoryImpl├───────────────────────►│ Repository    │
└──────▲────────┘ (via Mapper)           │ (Interface)   │
       │                                 └───────────────┘
       ├────────────────────────┐
       │ DTO                    │ Entity
┌──────┴────────┐        ┌──────┴────────┐
│ Remote Source │        │ Local DB      │
│ (Network API) │        │ (Room Cache)  │
└───────────────┘        └───────────────┘
```

---

## 4. Module Graph Reference

For the complete module catalog, category definitions, layer boundaries, and dependency enforcement rules, see **[MODULES.md](MODULES.md)** — including `:core:audio-dsp` (playback DSP single home) under Core Infrastructure.
