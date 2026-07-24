# Adding a New Lyrics Provider

This guide walks you through adding a new lyrics source to this repository.

## Overview

Each provider lives in its own Gradle submodule under the `lyrics/` directory. Providers are pure JVM libraries — no Android dependencies. They use **Ktor** for HTTP and **Kotlinx Serialization** for JSON parsing.

## Step-by-Step

### 1. Create the module directory

```
lyrics/yourprovider/
├── build.gradle.kts
└── src/main/kotlin/moe/rukamori/archivetune/yourprovider/
    └── YourProvider.kt
```

### 2. Create `build.gradle.kts`

Copy the standard template:

```kotlin
plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    testImplementation(libs.junit)
}
```

Add any extra Ktor or serialization dependencies your provider needs.

### 3. Register the module

Add your module to **two** files:

**`settings.gradle.kts`** (at the root of the `lyrics` repo):
```kotlin
include(":yourprovider")
```

**`../settings.gradle.kts`** (in the main ArchiveTune repo, on the `feat/lyrics-submodule` branch):
```kotlin
include(":lyrics:yourprovider")
```

**`../app/build.gradle.kts`** (in the main ArchiveTune repo):
```kotlin
implementation(project(":lyrics:yourprovider"))
```

### 4. Implement the provider

Create a singleton object that exposes `getLyrics()` and optionally `getAllLyrics()`. Follow one of the existing patterns:

**Simple pattern** (one endpoint, returns lyrics as string):

```kotlin
package com.muwmx.yuma.yourprovider

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object YourProvider {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 10000
        }
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        val response = client
            .get("https://api.example.com/lyrics") {
                parameter("track", title)
                parameter("artist", artist)
            }.body<LyricsResponse>()

        response.lyrics ?: throw IllegalStateException("Lyrics not found")
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(title, artist, duration).onSuccess(callback)
    }
}

@Serializable
data class LyricsResponse(
    val lyrics: String? = null,
)
```

### 5. Run the integration in the app

Once the module is registered, create a provider wrapper in the app module at:

```
app/src/main/kotlin/moe/rukamori/archivetune/lyrics/YourProvider.kt
```

```kotlin
package com.muwmx.yuma.lyrics

import android.content.Context
import com.muwmx.yuma.yourprovider.YourProvider

object YourProvider : LyricsProvider {
    override val name = "Your Provider"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = YourProvider.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        YourProvider.getAllLyrics(title, artist, duration, callback)
    }
}
```

Then register it in `LyricsHelper.kt` in the `baseProviders` list:

```kotlin
private val baseProviders =
    listOf(
        BetterLyricsProvider,
        LrcLibLyricsProvider,
        YourProvider, // <-- add here
        ...
    )
```

### 6. Handle API responses

Use `@Serializable` data classes for your response models. Place them in a `models/` subdirectory if you have more than one:

```
lyrics/yourprovider/src/main/kotlin/moe/rukamori/archivetune/yourprovider/
├── YourProvider.kt
└── models/
    ├── SearchResponse.kt
    └── LyricsResponse.kt
```

### 7. Duration matching

If your API returns multiple results, use duration-based filtering. Most existing providers use an `abs(duration - candidateDuration) <= tolerance` pattern. See `LrcLib.kt` or `KuGou.kt` for reference.

### 8. Testing

Add unit tests under `src/test/kotlin/`. Run them with:

```bash
./gradlew :yourprovider:test
```

## Conventions

| Rule | Why |
|---|---|
| Use `object` singleton (not `class`) | Stateless, no injection needed |
| Return `Result<String>` from `getLyrics()` | Consistent error handling across providers |
| Name the file after the provider | e.g. `KuGou.kt`, `LrcLib.kt` |
| Package format: `com.muwmx.yuma.<name>` | Matches existing convention |
| Timeout at 15s for requests | Prevents hanging in the lyrics pipeline |
| No Android imports | These are JVM libraries |

## Submitting

1. Commit your changes to a feature branch on your fork
2. Open a pull request using the PR template
3. The PR will be reviewed and, if accepted, merged into `main`
4. Once merged, the main ArchiveTune repo's submodule reference will be updated
