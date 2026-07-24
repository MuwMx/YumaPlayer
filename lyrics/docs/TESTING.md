# Testing

## Running Tests

Run tests for a single provider:

```bash
./gradlew :kugou:test
```

Run tests for all providers:

```bash
./gradlew test
```

## Writing Tests

### Unit test structure

Place tests in `src/test/kotlin/` matching the provider's package:

```
kugou/src/test/kotlin/moe/rukamori/archivetune/kugou/
└── KuGouTest.kt
```

### Mocking HTTP requests

Use `MockEngine` from Ktor to simulate API responses:

```kotlin
import io.ktor.client.engine.mock.*
import io.ktor.client.*
import io.ktor.utils.io.*
import io.ktor.http.*

val mockClient = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            when (request.url.encodedPath) {
                "/api/search" -> {
                    respond(
                        content = ByteReadChannel("""[{ "id": "1", "lyrics": "test" }]"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type" to "application/json"),
                    )
                }
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }
    }
}
```

### Test pattern

```kotlin
class MyProviderTest {
    @Test
    fun `getLyrics returns lyrics on success`() = runTest {
        val result = MyProvider.getLyrics("Test Song", "Test Artist", 200)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contains("expected lyrics"))
    }

    @Test
    fun `getLyrics returns failure when lyrics not found`() = runTest {
        val result = MyProvider.getLyrics("Unknown", "Unknown", -1)
        assertTrue(result.isFailure)
    }
}
```

### What to test

| Scenario | Expected |
|---|---|
| Valid track with known lyrics | `Result.success` with non-empty string |
| Unknown track | `Result.failure` |
| Duration = -1 | Should still attempt lookup |
| Network timeout | `Result.failure` (caught by runCatching) |
| Malformed API response | `Result.failure` (caught by serialization) |
| Empty lyrics from API | `Result.failure` |

### CI

Tests run automatically on every PR via GitHub Actions. The CI configuration mirrors the parent ArchiveTune project's JVM test setup.
