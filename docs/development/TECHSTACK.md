# TECHSTACK — Approved Technology Stack and Dependency Freeze Policy

**Status:** Mandatory.
**Purpose:** Hard boundary for AI agents. Forbids autonomous selection of technologies, libraries, and versions.
**Single Source of Truth for versions:** `gradle/libs.versions.toml`.

In case of conflict between this file and code, this file takes precedence. In case of conflict with `gradle/libs.versions.toml`, the version catalog takes precedence and the discrepancy is filed as a documentation bug.

---

## 1. Toolchain and Core

| Component | Pinned Version | Notes |
| :--- | :--- | :--- |
| Language | Kotlin 2.4.0 | Kotlin only. Java is allowed solely in generated code |
| JVM | 21 | Build and runtime toolchain |
| AGP | 9.2.1 | Version taken exclusively from the version catalog |
| compileSdk / targetSdk | 37 | — |
| minSdk | 26 (Android 8.0+) | — |

## 2. Approved Stack

| Category | Technology | Notes |
| :--- | :--- | :--- |
| UI | Jetpack Compose + Material 3 Expressive | XML layouts are forbidden for new screens (ADR-005) |
| Design tokens | YDS 2.1 (`docs/design/YDS.md`) | Colors, radii, spacing, and shapes only from tokens |
| Playback | AndroidX Media3 / ExoPlayer (`MediaSessionService`, `MediaController`, `ExoPlayer`) | Sole playback engine (ADR-004) |
| Local storage | Room (schema v36) | DTOs and Entities must never leave the data layer |
| Network | Ktor / OkHttp | Single project-wide HTTP client, singleton via DI |
| Images | Coil | Load only with resize to display size |
| Cryptography | Google Tink (AES-256-GCM) | Tokens and cookies only in encrypted DataStore |
| Concurrency | Kotlinx Coroutines + Flow (`StateFlow`, `SharedFlow` / `Channel`) | `Dispatchers.IO` for I/O, `Dispatchers.Default` for CPU-bound work |
| Serialization | Kotlinx Serialization | DTOs must carry `@Serializable` / `@Keep` for R8 |
| DI | Hilt (composition root in `:app`) | No Service Locator, no manual singletons |

## 3. Blacklist — Strictly Forbidden

| Forbidden | Reason | Replacement |
| :--- | :--- | :--- |
| Retrofit | Ktor / OkHttp is the approved stack. Two HTTP stacks are forbidden | OkHttp singleton from `:app` (`di/NetworkModule.kt`), Ktor client in `:core:innertube` |
| RxJava / RxKotlin / LiveData for new logic | Approved: Coroutines + `StateFlow` + UDF (ADR-003) | `StateFlow` + `collectAsStateWithLifecycle()` |
| 3rd-party UI kits, dialog, animation, shimmer libraries | Break YDS 2.1 and 120fps kinematics | Yuma UI Kit + `docs/design/COMPONENT_GUIDE.md` |
| Legacy XML layouts, Material 2, `AppCompat` widgets | Violates ADR-005 | Jetpack Compose |
| Glide / Picasso / Fresco | Duplicate Coil | Coil |
| Gson / Jackson / Moshi | Duplicate Kotlinx Serialization, break R8 | Kotlinx Serialization |
| EventBus, Otto, SharedFlow event bus instead of UDF | Breaks Single Source of Truth | `UiIntent` -> `ViewModel` -> `UiState` |
| `runBlocking`, `Thread.sleep`, synchronous I/O on Main | Main-thread blocking is forbidden by `YUMA_RULES.md` | `suspend` functions with an explicit dispatcher |

## 4. Dependency Modification Rules

1. **Modifying `gradle/libs.versions.toml`, `settings.gradle.kts`, or any `build.gradle.kts` without explicit developer approval is forbidden.** Every version change is a standalone task, never a side effect of a feature.
2. **Adding a new dependency for a single task without checking `:core:*` and existing providers is forbidden.**
3. **Downgrading or upgrading a major version** of Kotlin, AGP, Compose, Media3, Room, or Ktor without a migration plan is forbidden.
4. A new library is admissible only when: no in-project analogue exists, official documentation is available, R8/ProGuard compatibility is confirmed, and no forbidden transitive stack is pulled in.
5. Any dependency addition must update this file in the same change.
