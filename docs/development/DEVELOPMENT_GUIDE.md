# YumaPlayer Development Guide

This document provides standardized workflows for implementing common features, layers, and modules in YumaPlayer.

---

## 1. How to Add a New Lyrics Provider

All lyrics provider implementations reside under the `:lyrics:*` submodule namespace (e.g., `:lyrics:lrclib`, `:lyrics:kugou`).

1. **Create Submodule Directory:**
   Create a new module directory under `lyrics/<provider_name>/` containing a standard `build.gradle.kts` file that depends on `:core` shared contracts.

2. **Register in `settings.gradle.kts`:**
   ```kotlin
   include(":lyrics:myprovider")
   ```

3. **Implement Provider Contract:**
   Implement the shared domain interface (e.g., `LyricsProvider`):
   ```kotlin
   class MyLyricsProvider(
       private val client: HttpClient
   ) : LyricsProvider {
       override suspend fun getLyrics(track: Track): Result<Lyrics> {
           return runCatching {
               client.fetchLyricsPayload(track.id).toDomain()
           }
       }
   }
   ```

4. **Register in DI:**
   Expose the provider instance through the application's dependency injection container graph.

---

## 2. How to Add a New UI Screen / Feature Flow

1. **Create Feature Package:**
   Place new features inside `:app` under the corresponding domain package (e.g. `app/src/main/kotlin/moe/rukamori/archivetune/<feature>/`) with screens under `ui/screens/`. (`:feature:<name>` modules are reserved for a future split, not real — see `MODULES.md` §6.)
2. **Define UI State (`UiState`):**
   ```kotlin
   sealed interface MyFeatureUiState {
       data object Loading : MyFeatureUiState
       data class Success(val items: List<SongUiModel>) : MyFeatureUiState
       data class Error(val messageRes: Int) : MyFeatureUiState
   }
   ```
3. **Define UI Intents (`UiIntent`):**
   ```kotlin
   sealed interface MyFeatureUiIntent {
       data object Refresh : MyFeatureUiIntent
       data class SelectSong(val songId: String) : MyFeatureUiIntent
   }
   ```
4. **Create ViewModel:**
   ```kotlin
   class MyFeatureViewModel(
       private val getSongsUseCase: GetSongsUseCase
   ) : ViewModel() {
       private val _uiState = MutableStateFlow<MyFeatureUiState>(MyFeatureUiState.Loading)
       val uiState: StateFlow<MyFeatureUiState> = _uiState.asStateFlow()

       fun onIntent(intent: MyFeatureUiIntent) {
           when (intent) {
               is MyFeatureUiIntent.Refresh -> loadData()
               is MyFeatureUiIntent.SelectSong -> handleSelection(intent.songId)
           }
       }
   }
   ```
5. **Implement Stateless Screen Composables:**
   ```kotlin
   @Composable
   fun MyFeatureScreen(
       viewModel: MyFeatureViewModel,
       onNavigateBack: () -> Unit
   ) {
       val state by viewModel.uiState.collectAsStateWithLifecycle()
       MyFeatureContent(
           state = state,
           onIntent = viewModel::onIntent,
           onNavigateBack = onNavigateBack
       )
   }

   @Composable
   fun MyFeatureContent(
       state: MyFeatureUiState,
       onIntent: (MyFeatureUiIntent) -> Unit,
       onNavigateBack: () -> Unit
   ) {
       when (state) {
           is MyFeatureUiState.Loading -> LoadingContent()
           is MyFeatureUiState.Success -> SuccessContent(
               items = state.items,
               onIntent = onIntent
           )
           is MyFeatureUiState.Error -> ErrorContent(
               messageRes = state.messageRes
           )
       }
   }
   ```

---

## 3. How to Add a New Use Case

1. **Locate Domain Package:** Place the Use Case in the owning `:app` domain package (e.g. `search/`, `library/`, `artist/`) alongside its repository interfaces.
2. **Implement Single Action Class:**
   Use Cases represent a single unit of business logic. Do not handle Thread/Dispatcher context switching inside Use Cases — Repository implementations are responsible for executing I/O operations on `Dispatchers.IO`.
   ```kotlin
   class ToggleFavoriteUseCase(
       private val songRepository: SongRepository
   ) {
       suspend operator fun invoke(songId: String): Result<Unit> {
           return songRepository.toggleFavorite(songId)
       }
   }
   ```
3. **Inject into ViewModel:** Inject Use Cases into ViewModels. ViewModels must not access Repositories directly when domain logic or business constraints apply.

---

## 4. How to Add a New Gradle Module

1. **Create Directory Structure:** Create the new module folder matching its architectural layer (e.g. `lyrics/myprovider/`).
2. **Add `build.gradle.kts`:** Configure necessary Android or Kotlin plugins:
   ```kotlin
   plugins {
       alias(libs.plugins.android.library)
       alias(libs.plugins.kotlin.android)
       alias(libs.plugins.compose.compiler)
   }
   ```
3. **Include in `settings.gradle.kts`:**
   ```kotlin
   include(":lyrics:myprovider")
   ```
4. **Wire Dependencies:** Declare dependencies strictly in compliance with the module graph rules defined in `MODULES.md`. Never declare cyclic dependencies or bypass layer boundaries.

---

## 5. Maintaining Documentation Sync

Documentation must stay in sync with the codebase as the system evolves:

1. **Code Changes:** When modifying architectural boundaries, workflows, or layer contracts, update the relevant files under `docs/` in the same pull request.
2. **Architectural Shifts:** If an architectural pattern or library decision changes, draft or update an Architecture Decision Record (ADR) in `docs/architecture/DECISIONS.md`.
3. **Module Catalog:** When adding, removing, or renaming modules, update the matrix in `MODULES.md`.
