# API Reference

## KuGou

**File:** `kugou/src/main/kotlin/moe/rukamori/archivetune/kugou/KuGou.kt`
**Object:** `KuGou`

### Methods

#### `getLyrics(title, artist, duration)`

| Parameter | Type | Description |
|---|---|---|
| `title` | `String` | Track title |
| `artist` | `String` | Artist name |
| `duration` | `Int` | Duration in seconds (`-1` to skip filtering) |

Returns `Result<String>` — LRC-formatted lyrics.

#### `getAllPossibleLyricsOptions(title, artist, duration, callback)`

Fetches all matching lyric variants. Calls `callback` for each candidate found. Duration tolerance: 8 seconds.

#### `getLyricsCandidate(keyword, duration)`

Lower-level: returns the best `Candidate` without downloading. Useful for inspecting available matches.

---

## LRC Lib

**File:** `lrclib/src/main/kotlin/moe/rukamori/archivetune/lrclib/LrcLib.kt`
**Object:** `LrcLib`

### Methods

#### `getLyrics(title, artist, duration, album?)`

| Parameter | Type | Description |
|---|---|---|
| `title` | `String` | Track title |
| `artist` | `String` | Artist name |
| `duration` | `Int` | Duration in seconds |
| `album` | `String?` | Optional album name for better matching |

Returns `Result<String>` — synced LRC preferred, falls back to plain lyrics.

#### `getAllLyrics(title, artist, duration, album?, callback)`

Yields up to 5 results, sorted by duration proximity. Emits synced lyrics before plain lyrics for each candidate.

#### `lyrics(artist, title)`

Quick lookup — returns raw `List<Track>` from the API without ranking.

---

## SimpMusic

**File:** `simpmusic/src/main/kotlin/moe/rukamori/archivetune/simpmusic/SimpMusicLyrics.kt`
**Object:** `SimpMusicLyrics`

### Methods

#### `getLyrics(videoId, duration)`

| Parameter | Type | Description |
|---|---|---|
| `videoId` | `String` | YouTube video ID |
| `duration` | `Int` | Duration in seconds |

Returns `Result<String>`. Uses YouTube video ID as the key.

#### `getAllLyrics(videoId, duration, callback)`

Batch variant — calls callback with each available variant.

---

## Paxsenix

**File:** `paxsenix/src/main/kotlin/moe/rukamori/archivetune/paxsenix/PaxsenixLyrics.kt`
**Object:** `PaxsenixLyrics`

### Methods

#### `getLyrics(title, artist, duration)`

Auto-select backend. Tries Apple Music → NetEase → Spotify → Musixmatch, returns the first success.

#### `getAppleMusicLyrics(title, artist, duration)`

Apple Music via Paxsenix proxy. Supports TTML + LRC formats.

#### `getNeteaseLyrics(title, artist, duration)`

NetEase Music via Paxsenix proxy. Karaoke word-by-word + LRC.

#### `getSpotifyLyrics(title, artist, duration)`

Spotify via Paxsenix proxy.

#### `getMusixmatchLyrics(title, artist, duration)`

Musixmatch via Paxsenix proxy. Word-by-word + default formats.

#### `getYouTubeLyrics(title, artist, duration)`

YouTube Music via Paxsenix proxy.

#### `getStats()`

Returns server health stats from the Paxsenix proxy.

---

## BetterLyrics

**File:** `betterlyrics/src/main/kotlin/moe/ArchiveTuneApp/rukamori/betterlyrics/BetterLyrics.kt`
**Object:** `BetterLyrics`

### Methods

#### `getLyrics(title, artist, album?, duration)`

| Parameter | Type | Description |
|---|---|---|
| `title` | `String` | Track title |
| `artist` | `String` | Artist name |
| `album` | `String?` | Optional album name |
| `duration` | `Int` | Duration in seconds |

Returns `Result<String>` — TTML XML format with word-by-word timing.

#### `getAllLyrics(title, artist, album?, duration, callback)`

Batch variant with KuGou proxy fallback.

### `TTMLParser`

**File:** `TTMLParser.kt`
**Object:** `TTMLParser`

Parses TTML XML into word-by-word timed entries. Handles CJK characters, transliteration, and syllable continuation.

---

## Unison

**File:** `unison/src/main/kotlin/moe/rukamori/archivetune/unison/Unison.kt`
**Object:** `Unison`

### Methods

#### `getLyrics(videoId?, title?, artist?, album?, durationSeconds?)`

| Parameter | Type | Description |
|---|---|---|
| `videoId` | `String?` | YouTube video ID |
| `title` | `String?` | Track title |
| `artist` | `String?` | Artist name |
| `album` | `String?` | Optional album |
| `durationSeconds` | `Int?` | Duration in seconds |

At least one of `videoId` or `(title + artist)` is required. Returns `Result<String>`.

#### `getAllLyrics(videoId?, title?, artist?, album?, durationSeconds?, callback)`

Batch variant — searches by metadata and yields results through callback.
