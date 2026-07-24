# Architecture

This document describes the internal architecture of the lyrics providers and how they fit together.

## Module Structure

Each provider is an independent Gradle submodule with the same skeleton:

```
<name>/
├── build.gradle.kts          # Gradle config (Ktor, kotlinx-serialization)
└── src/
    ├── main/kotlin/moe/rukamori/archivetune/<name>/
    │   ├── <Provider>.kt      # Main singleton object
    │   └── models/            # Response data classes
    └── test/                  # Unit tests
```

No module depends on another — they are completely decoupled. All cross-module wiring happens in the Android app layer (`LyricsHelper`).

## The Provider Object

Every provider follows the same pattern:

```kotlin
object ProviderName {
    // 1. HTTP client (lazy or val)
    private val client = HttpClient { ... }

    // 2. Public API — always returns Result<String>
    suspend fun getLyrics(title: String, artist: String, duration: Int): Result<String>

    // 3. Optional — batch fetch with callback
    suspend fun getAllLyrics(title: String, artist: String, duration: Int, callback: (String) -> Unit)
}
```

### `getLyrics()` contract

| Parameter | Type | Description |
|---|---|---|
| `title` | `String` | Track title from the player |
| `artist` | `String` | Artist name(s) from the player |
| `duration` | `Int` | Track duration in seconds (`-1` if unknown) |

Returns `Result.success(lyrics)` or `Result.failure(exception)`. The caller (app) decides how to handle failures.

## HTTP Client Patterns

There are two patterns used across providers:

### 1. Direct `val` client (KuGou, BetterLyrics, Unison)

```kotlin
private val client = HttpClient {
    install(ContentNegotiation) { ... }
    install(HttpTimeout) { ... }
}
```

Initialized once when the class loads. Simple and predictable.

### 2. Lazy `by lazy` client (LrcLib, Paxsenix)

```kotlin
private val client by lazy {
    HttpClient(CIO) {
        install(ContentNegotiation) { ... }
        install(HttpTimeout) { ... }
        defaultRequest { url("https://api.example.com") }
    }
}
```

Deferred initialization — the client is created on first use. Useful when the constructor has side effects or you want config defaults.

### Common configuration

```kotlin
install(ContentNegotiation) {
    json(Json {
        ignoreUnknownKeys = true   // tolerate extra fields
        explicitNulls = false      // omit nulls from serialization
        isLenient = true           // lenient parsing
    })
}

install(HttpTimeout) {
    requestTimeoutMillis = 15000   // 15s request timeout
    connectTimeoutMillis = 10000   // 10s connect timeout
    socketTimeoutMillis = 15000    // 15s socket timeout
}
```

## Response Parsing

All JSON responses are deserialized with `kotlinx.serialization`:

```kotlin
@Serializable
data class Track(
    val id: String,
    val title: String,
    @SerialName("lyrics_text") val lyrics: String? = null,
    val synced: Boolean = false,
)
```

Use `@SerialName` for snake_case or non-Kotlin field names. Default values (`= null`, `= false`) prevent crashes when fields are missing.

## Duration Matching

When an API returns multiple candidates, providers filter by duration:

```kotlin
// KuGou: 8-second tolerance
abs(song.duration - duration) <= DURATION_TOLERANCE

// LrcLib: 2-second tolerance, sorted by closest match
abs(track.duration.toInt() - duration) <= MAX_DURATION_DELTA_SECONDS
```

## LRC Format

Most providers return lyrics in LRC format:

```
[00:12.34] First line of lyrics
[00:18.56] Second line of lyrics
[00:25.00] Third line of lyrics
```

Some return TTML (XML-based, word-by-word timing) — see the `betterlyrics` module for TTML parsing.

## Error Handling

Errors bubble up through `Result.failure()`:

- Network errors (timeout, DNS failure) → Ktor throws, caught by `runCatching`
- API errors (404, 403) → checked via `expectSuccess = true` or manual status checks
- Parsing errors (malformed JSON) → kotlinx.serialization throws, caught by `runCatching`
- Business errors (no matching lyrics) → explicit `IllegalStateException("Lyrics not found")`
