# YumaPlayer Coding Standard

This document defines the official code style, architectural conventions, and implementation patterns for YumaPlayer. All Kotlin and Compose code must comply with these standards.

---

## 1. Naming Conventions

Standardized naming ensures instant clarity across all modules and layers:

| Component | Convention | Example |
| :--- | :--- | :--- |
| **Domain Model** | `<Entity>` | `Song`, `Album`, `Lyrics` |
| **Network DTO** | `<Entity>Dto` | `SongDto`, `SearchResponseDto` |
| **Database Entity** | `<Entity>Entity` | `SongEntity`, `HistoryEntity` |
| **UI State** | `<Screen/Component>UiState` | `PlayerUiState`, `LibraryUiState` |
| **UI Intent** | `<Screen/Component>UiIntent` | `PlayerUiIntent`, `LibraryUiIntent` |
| **UI One-off Effect** | `<Screen/Component>UiEffect` | `PlayerUiEffect`, `NavigationEffect` |
| **Repository Interface** | `<Entity>Repository` | `SongRepository` |
| **Repository Impl** | `<Entity>RepositoryImpl` | `SongRepositoryImpl` |
| **Use Case** | `<Verb><Noun>UseCase` | `GetLyricsUseCase`, `ToggleFavoriteUseCase` |
| **Mapper Extensions** | `<Entity>Mappers.kt` | `fun SongDto.toDomain(): Song` |

---

## 2. Kotlin Style & Idioms

- **Immutability First:** Prefer `val` over `var` wherever possible. Use immutable collections (`listOf`, `mapOf`) by default.
- **Early Returns & Guard Clauses:** Avoid deeply nested `if-else` blocks. Return early to keep the main execution path flat and legible.
- **Expression Bodies:** Prefer single-expression bodies for short, straightforward functions:
  ```kotlin
  fun isPlaying(): Boolean = playbackState.value == State.PLAYING
  ```
- **Extension Functions for Transformations:** Use extension functions for data mapping and non-intrusive domain conversions.
- **Explicit Returns:** Explicitly state return types for public API surfaces and non-trivial functions.

---

## 3. Data Model Separation & Layer Mapping

### 3.1 Strict Model Categorization

Network DTOs, Database Entities, Domain Models, and UI States **must be distinct data classes**.

- **Network DTOs (`:core:innertube` + `:app` data sources):** Annotate with `@Serializable` or `@SerializedName`. Expose strictly inside data sources.
- **DB Entities (`:database`):** Annotate with `@Entity`, `@PrimaryKey`. Internal to the persistence module.
- **Domain Models (`:app` colocated domain packages + `:core` shared contracts):** Pure Kotlin data classes. Zero framework or serialization annotations.
- **UI State Models (`:app` screens):** Immutable state classes representing exact screen states.

### 3.2 Mappers & Parsing Utilities

- Mappers are written as pure extension functions (e.g., `fun SongDto.toDomain(): Song`).
- Complex Regex, string parsing, or format transformations belong in isolated, testable utility objects (e.g., `object LyricParser`) without Android Context dependencies.

---

## 4. Repository Pattern Standards

- **Single Responsibility:** Repositories coordinate data fetching, local caching, and synchronization across network and persistence sources.
- **Domain Model Outputs:** Repositories must return pure Domain models or domain-specific result types (`Result<T>`). DTOs and DB Entities must never leak outside data layers.
- **Explicit Thread Dispatching:** All disk, network, and database operations inside Repository implementations MUST explicitly execute on `Dispatchers.IO`.

---

## 5. ViewModel & State Management Standards

- **UDF Flow:** ViewModels maintain a private `MutableStateFlow` and expose an immutable, read-only `StateFlow<UiState>`.
- **Sealed Intent Processing:** User actions are passed to ViewModels via sealed interfaces (`UiIntent`).
- **One-off Effects:** Single-shot events (navigation, toasts, triggers) must use a `Channel<UiEffect>` exposed as a `Flow<UiEffect>`.
- **No Direct Context/File Access:** ViewModels must not reference `Context`, access file systems, or invoke HTTP clients directly — interact strictly via Use Cases or Repositories.
- **ViewModel Scope:** All coroutine executions inside ViewModels must be bound to `viewModelScope`.

---

## 6. Jetpack Compose Standards

### 6.1 Stateless Components & State Hoisting

- Accept state objects as parameters (`state: SongUiModel`).
- Hoist callbacks upward as lambdas (`onPlayClick: () -> Unit`).
- Contain zero business logic, repository calls, or direct state mutations.

### 6.2 Modifier Guidelines

- Every top-level composable component should accept an optional modifier parameter: `modifier: Modifier = Modifier`.
- The `modifier` parameter must be the first optional parameter in the composable parameter list.

### 6.3 State Management (`remember`, `rememberSaveable`, `derivedStateOf`)

- Use `remember` strictly for local UI-only visual state (e.g., animation controllers, expanded states).
- Use `rememberSaveable` when local UI state needs to survive configuration changes or process death.
- Use `derivedStateOf` to buffer high-frequency state updates (e.g., scroll offsets) when computing conditional properties.
- Avoid allocating complex objects directly inside the composable body without `remember`.

### 6.4 Explicit Lazy Layout Keys

All `LazyColumn`, `LazyRow`, and `LazyVerticalGrid` items **MUST specify an explicit, unique key**:

```kotlin
LazyColumn {
    items(
        items = songs,
        key = { song -> song.id }
    ) { song ->
        SongRow(song = song, onClick = { onSongClick(song.id) })
    }
}
```

### 6.5 Lifecycle-Aware Flow Collection

Flows in Compose must always be collected using `collectAsStateWithLifecycle()`:

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

---

## 7. Concurrency & Coroutines

- **Dispatcher Assignment:**
  - `Dispatchers.IO` — Network calls, File I/O, and Database queries.
  - `Dispatchers.Default` — Heavy CPU calculations, palette extraction, and complex parsing.
  - `Dispatchers.Main` — UI updates and light state changes.
- **No Async Work in Composables:** Never launch long-running background tasks directly inside Composable lifecycle blocks (`LaunchedEffect`). Delegate work to ViewModels.
- **Structured Concurrency:** Always execute coroutines within established scopes (`viewModelScope`, `coroutineScope`).

---

## 8. Code Comments Standard

- Explain non-obvious intent, architectural rationale, business constraints, or implementation details that are not immediately apparent from the code.
- **No Self-Explanatory Comments:** Do not add redundant comments that simply restate what clean code already expresses (e.g., `title = song.title`).
- **Maintainability:** Remove or update comments immediately whenever the associated implementation changes to prevent documentation rot.
- **No Tutorial Style:** Avoid decorative header lines, boilerplate comments, or explanatory comments intended for teaching basic Kotlin/Compose syntax.
