# EDGE_CASES — Playback and Network Boundary Scenarios (Happy Path Is Forbidden)

**Status:** Mandatory.
**Purpose:** Agents must design from failure, not from the ideal scenario.
**Global rule:** Every failure maps to `UiState.Error` or a structured domain error. Empty `catch` blocks, `null` instead of an error, crashes, and infinite `Loading` are forbidden (`YUMA_RULES.md`).

---

## 1. Network and Streaming Sources

| Case | Trigger | Expected behavior |
| :--- | :--- | :--- |
| 403 / 401 from InnerTube / Spotify | Expired cookie, revoked `sp_dc`, rotated signature | Single token refresh under mutex (ADR-011) with exactly one retry. On repeated 403, emit `Error` with a "Re-login" action and zero network spam |
| 429 Too Many Requests | Frequent URL resolves, N+1 like requests | Exponential backoff plus batching (Spotify chunks of up to 50 URIs via `SyncUtils`). Looped retries without delay are forbidden |
| Mid-stream network drop | Tunnel, Wi-Fi to LTE handover, VPN flap | Cancel the stale job, show buffering with a retry button. On reconnect, resume the stream instead of restarting the queue |
| Empty / malformed JSON | Provider-side API change | Parser returns `Result.failure`; UI shows `Empty` or `Error`; logs contain no PII |

## 2. Playback Fallback (FLAC to Opus / AAC)

| Case | Trigger | Expected behavior |
| :--- | :--- | :--- |
| FLAC resolve exceeds 10000ms or returns 404 | No lossless source, empty qbdlx token | Silent fallback to high-bitrate YouTube Music (Opus / AAC); `PlaybackSource` stays consistent in `MediaSession`; UI does not flicker |
| User has no FLAC token | Empty Qobuz settings | qbdlx branch is disabled upfront through the lazy token provider, without blocking DataStore reads in the DI graph (ADR-009) |
| Forbidden | — | Holding UI in `Loading` during resolve, spawning parallel resolve jobs for one `songId`, or caching a failed URL in `StreamUrlCache` is forbidden |

## 3. Audio Focus and System Interruptions

| Case | Trigger | Expected behavior |
| :--- | :--- | :--- |
| Transient call / assistant | `AUDIOFOCUS_LOSS_TRANSIENT` | Pause with position retained; resume on focus return only if playback was active before the interruption (owned by `MediaSession`, never by a ViewModel flag) |
| Parallel media / navigation prompts | `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` | Duck volume without pausing the queue |
| Permanent focus loss / headset disconnect | `AUDIOFOCUS_LOSS`, `ACTION_AUDIO_BECOMING_NOISY` | Full pause; notification and widget stay synchronized through `MediaController` |
| Forbidden | — | Driving pause directly from a Composable or duplicating `isPlaying` outside `MediaSession` is forbidden |

## 4. Input Debouncing and Sheet Gestures

| Case | Trigger | Expected behavior |
| :--- | :--- | :--- |
| Double / triple track taps | Rapid taps under 300ms | Debounce the intent; keep exactly one active load job per screen inside `viewModelScope` and cancel the stale one. Double stream starts are forbidden |
| Fast queue swipe / sheet drag | Continuous `fraction` sweep across 0.0–1.0 | Zero fraction reads in the composition phase; reads only inside `graphicsLayer` / `drawWithContent`. `BackHandler` reacts only to `isQueueVisible` / `isLyricsVisible` (ADR-010) |
| Shuffle or clear during playback | Shuffle pressed or queue cleared while a track plays | Player queue in `MediaSession` is the Single Source of Truth. UI only mirrors it; the current track index is never lost |
| Offline launch | No network at startup | Cached YT tracks play; FLAC-offline is labeled in development; sync actions are deferred without WorkManager spam |

## 5. Empty and Degraded States

- Empty playlist, history, or lyric result renders `UiState.Empty` with an explanation and a call to action, never a blank screen.
- No lyric provider reachable (all 7+ sources down: LRCLIB, Kugou, Paxsenix, and others): show the plain unsynced text without karaoke highlighting; keep the failure in logs only.
- AI translation unavailable: show the source text without blocking the player.
- Last.fm / ListenBrainz scrobble unavailable: fire and forget, with no UI-thread retry.
