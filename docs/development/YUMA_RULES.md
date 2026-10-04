# Yuma Rules

This document defines the mandatory engineering rules for YumaPlayer.

These rules apply to all contributions, whether written by human contributors or AI assistants.

If a rule conflicts with an implementation, the rule takes precedence.

---

## 1. Project Principles

- **No Shortcuts or Placeholders:** Never use `TODO`, `FIXME`, mock code, or temporary hacks as a final solution.
- **Single Source of Truth:** Every state or piece of data must have one unambiguous owner (StateFlow, Repository cache, or MediaSession).
- **Single Responsibility:** Classes, functions, and modules must have one clear responsibility.
- **Root Cause First:** Fix the underlying cause of an issue instead of introducing workarounds.
- **No Duplicate Implementations:** Reuse existing abstractions, helpers, and components before creating new ones.
- **Fail Gracefully:** Errors must be mapped to structured UI states (`Error`), never swallowed silently or causing unhandled crashes.
- **File Length Limit:** Production source files should generally target under 250 lines. Large or multi-responsibility files must be decomposed into SRP-compliant subcomponents, coordinators, and state holders without compromising architecture (see §1.1 for split vs cohesion criteria).

### 1.1 Split Criteria (SRP, not line count)
- Split by reasons to change, not by ruler. Monolith = mixes auth, cache, parsing, UI-state or different lifecycles.
- Keep cohesive class untouched even at 250-350 lines if it owns one end-to-end process (transport + retries, scoring math, JNI bridge).
- Facade keeps old signatures, <100 lines, delegates to specialists. No ViewModel/DI rewrite on internal split.
- Single state owner. No shared MutableStateFlow/lists. Playlists request token from Session, never store it.
- Dependencies one-way only, no cycles.
- No micro-files for digit beauty. Never extract 10-line private method to `SomethingHelper.kt` or break one algorithm chain into 3-4 files.

### 1.2 Error Handling & Pipeline Integrity
- No silent drops in playback and queue pipelines: `if (items.isEmpty()) return`, empty `CoroutineExceptionHandler`, raw `runCatching { }.getOrNull()` / `getOrDefault` without state update are forbidden. Dropped error = infinite loader.
- Typed pipeline failures: resolvers and fetchers propagate `Result<T>` with typed cause (`StreamError` sealed: NoInternet, Timeout, SessionExpired, RateLimited, StreamNotFound, BotDetected, LoginRequired, CacheError, Unknown) or typed exception. Raw `Throwable` / `Exception` only at system boundary for logging via `reportException`.
- Explicit failure reaction: every service/queue failure ends deterministically — auto-skip to next playable, prompt user action (relogin, retry), or emit mapped UI error. No hanging states.
- Safe cache fallbacks: corrupt cache read purges damaged entry and falls back to network. No silent empty lists, no crash.

---

## 2. Architecture & Module Isolation

- **Strict Layer Inversion:** Inner domain packages (`:app` colocated domain packages, `:core` contracts) must never import outer UI/playback layers (`:app` `ui/`, `playback/`, `:designsystem`).
- **Feature Isolation:** UI flows inside `:app` cannot depend on each other directly (no screen-to-screen imports). Shared code belongs in `:core` or `:designsystem`. (A future `:feature:*` split is reserved, not real — see `MODULES.md` §6.)
- **UI Logic Separation:** Composable functions must not contain business logic. ViewModels coordinate UI state but must not implement business rules.
- **No Direct API Access from ViewModel:** ViewModels must interact with data strictly via UseCases or Repositories.
- **No Context in ViewModels:** Android `Context` objects must never be referenced inside ViewModel instances.

---

## 3. UI & Design System (YDS)

- **YDS Is the Source of Truth:** New UI components and screens must follow Yuma Design System. Do not introduce screen-specific design patterns unless they become part of YDS.
- **Use Yuma UI Kit:** Prefer Yuma UI Kit components over raw Material 3 components. Direct Material components should only be used when no Yuma equivalent exists or within Yuma UI Kit implementations.
- **No Material Dividers:** Never use `HorizontalDivider` or `VerticalDivider`. Separate content using spacing tokens and surface color contrast.
- **No Card-in-Card Nesting:** Never nest a `Card` inside another `Card`. Use surface elevation or container fills.
- **Glass Effect Restrictions:** Translucency and blur effects are permitted ONLY for Overlay surfaces (Player bar, Bottom Sheets, Dialogs).
- **No Hardcoded UI Values:** User-facing strings must reside in `strings.xml`. Colors, dimensions, spacing, typography, and shapes must use YDS tokens whenever available.

---

## 4. Jetpack Compose & State Management

- **Reusable UI Components are Stateless:** Reusable UI components must be stateless, receiving immutable state objects and emitting intent lambdas (`onClick: () -> Unit`).
- **State Hoisting:** Components expose state and callbacks instead of owning business state.
- **Explicit Lazy Layout Keys:** Every item in `LazyColumn`, `LazyRow`, and `LazyVerticalGrid` must define an explicit unique `key`.
- **Compose Stability:** UI state models should be immutable whenever practical. Use `@Immutable` or `@Stable` where appropriate. Collect flows using `collectAsStateWithLifecycle()`.
- **No State Mutation in Composition:** Side effects and state changes inside Composable functions are strictly prohibited.
- **No Prop Drilling & Deep Parameter Cascades:** Passing state, offsets, or callbacks through more than two intermediate Composable layers is strictly forbidden. Cohesive UI parameters must be encapsulated within a dedicated `@Stable` state holder class or supplied via a scoped `CompositionLocal`.
- **Phase-Isolated Recomposition Guard:** Continuously changing values (gesture deltas, scroll progress, animation fractions) must never be read directly in the composition body or passed as static dimension values (`Dp`, `Float`). Pass them as state holders or lambda providers (`() -> Float`) and consume them strictly inside the Draw or Layout phase (`Modifier.graphicsLayer`, `Modifier.drawWithContent`, or `Modifier.offset { ... }`).
- **Screen Coordinator (Wiring) Pattern:** Every decomposed screen or complex module must have a single top-level coordinator (`*Screen.kt` or `*Wiring.kt`). The coordinator is exclusively responsible for UDF state collection (`collectAsStateWithLifecycle`), launching side-effects (`*Effects.kt`), and preparing event handlers.
- **No Infrastructure Leaks in Leaf UI:** Passing infrastructure objects (`NavController`, `PlayerConnection`, `CoroutineScope`, `MenuState`, or `ViewModel`) into leaf items (`LazyItemScope.*Row`, `*Item`, small cards) is strictly forbidden. Leaf components must be pure stateless layouts accepting domain data models and primitive click/action lambdas (`onClick`, `onMenuClick`).
- **Action & Media Context Encapsulation:** When a component requires more than 3–4 callbacks or shared infrastructure instances across sub-sections, encapsulate them into a dedicated `@Immutable data class` (e.g. `AlbumActions` or `HomeFeedMediaContext`).

---

## 5. Concurrency & Performance

- **Explicit Thread Dispatching:** Disk/Network I/O MUST use `Dispatchers.IO`. Heavy CPU calculations/parsing MUST use `Dispatchers.Default`.
- **Zero Main-Thread Blocking:** Blocking the main thread (`runBlocking`, synchronous I/O) is strictly forbidden.
- **ViewModel Scope:** All ViewModel coroutines must be launched within `viewModelScope` and properly handle cancellation.
- **Playback Authority:** Playback control must occur exclusively through AndroidX Media3 `MediaController`.

---

## 6. Code Hygiene & Security

- **No Unsafe Casts:** Avoid unchecked casts (`as`) unless correctness is guaranteed and documented.
- **Avoid `@Suppress`:** Do not suppress compiler warnings or lint checks without a documented reason.
- **No Secrets in Code:** Hardcoded API keys, tokens, or absolute local paths are strictly prohibited. Use `local.properties` / `BuildConfig`.
- **Code Shrinking Compatibility:** Types used by serialization, reflection, or code generation must remain compatible with R8/ProGuard (e.g. `@Serializable`, `@Keep`, or explicit keep rules).

---

## 7. Documentation

- **Keep Specs Updated:** Update documentation whenever architecture, APIs, workflows, or design rules change.
- **No Hidden Conventions:** Do not introduce undocumented conventions.
- **Centralized Decisions:** Long-term project decisions belong in `docs/`, not in pull request discussions or commit messages.

---

## 8. Gestures, Custom Layouts & Effect Guardrails

- **Single-Root Screen Requirement:** Every screen Composable must have exactly one root layout container (`Scaffold` or `Box`). Placing toolbars (`TopAppBar`), dialogs, or overlay layers as sibling nodes outside the screen's root container is strictly prohibited (multi-root breaks parent `NavHost` measurement constraints and touch gesture dispatch).
- **Mandatory ScrollBehavior Wiring:** If a screen accepts a `scrollBehavior: TopAppBarScrollBehavior` parameter, it MUST be passed to the top bar (`TopAppBar(..., scrollBehavior = scrollBehavior)`). Dangling unattached scroll behavior parameters lock or desynchronize parent `nestedScroll` connections.
- **TopAppBar Alignment in Free-Form Screens:** On screens utilizing free-form overlays (e.g. `Box` with `LazyColumn` and `YumaMorphingHeader`), the `TopAppBar` must be placed inside the common root `Box` with explicit `Modifier.align(Alignment.TopCenter)` above the content lists.
- **BackHandler Cascading Hierarchy:** The top-level player `BackHandler` must never be disabled via child overlay flags (`!isLyricsVisible`, `!isOverlayVisible`). Back navigation must cascade strictly from top to bottom:
  1. Dismiss open modal overlays (lyrics, queue).
  2. Collapse the expanded player sheet.
  3. Allow navigation pop in the underlying screen stack.
- **Custom Layout Trimming (Borders):** Clipping modifiers (`clipToBounds`, offset layout shifts) must be applied directly to the container that renders the border or background (`sheetBackground`), never to nested child content. In custom `layout { ... }`, the layout must report `constraints.maxWidth` to the parent to enforce boundaries.
- **Semantic Theme Tokens Only:** Never hardcode `Color.Black`, `Color.White`, or arbitrary hex codes for containers and text. Always use semantic design tokens (`surfaceContainerHighest`, `onSurface`, or YDS tokens) to ensure contrast in both light and dark themes.
- **No Duplicate Side-Effect Writers:** Never keep duplicate `LaunchedEffect` blocks writing to the same `MutableState`. Before adding or extending an effect, remove or refactor existing writers.
- **Single Source for UI Visibility:** Never pipe manual visibility callbacks (`onVisibilityChanged`) across composable layers if that state is already exposed by the ViewModel's UDF StateFlow. Read directly from the state source.
- **120fps Gesture Kinematics & Sheet Freeze Rules:**
  - **Draw Phase Isolation:** Never read continuous animation fractions (`queueFraction.value`, `lyricsFraction.value`, `fractionProvider()`) inside Composable function bodies (Composition phase). Read continuous values strictly inside `graphicsLayer { ... }` or `drawWithContent { ... }` (Draw phase).
  - **Discrete BackHandler Attachment:** Do not bind `BackHandler` to continuous floating-point thresholds (`fraction > 0.05f`) or place dynamic `BackHandler` inside child screens (`QueueScreen`, `LyricsColumn`). `BackHandler` must be attached strictly to discrete `StateFlow` flags (`isQueueVisible`, `isLyricsVisible`).
  - **Lazy List CompositingStrategy:** Never force constant `CompositingStrategy.Offscreen` during active swipe gestures. Use `CompositingStrategy.Auto` during drag (`fraction < 0.99f`), and switch to `Offscreen` only when resting statically at `fraction >= 0.99f` for edge fade masks.
  - **Isolated MatchParentSize Underlays:** Background styling with border shifts must be isolated to a dedicated underlay `Box(Modifier.matchParentSize().layout { placeRelative(-borderPx, 0) }.sheetBackground())`. Content composables (`QueueScreen`, `LyricsColumn`) render as siblings on top with standard `Modifier.fillMaxSize()`.
  - **Agent Freeze Policy:** AI agents are strictly forbidden from modifying or refactoring gesture kinematics, swipe physics, animation controllers, and sheet layers (`UnifiedPlayerSheetV2`, `UnifiedPlayerSheetLayers`, `QueueScreen`, `LyricsColumn`) without explicit, direct user instructions.
- **Diff Cleanliness:** Zero trailing whitespace. Verify that any newly added modifier produces a verifiable layout or visual change before finalizing changes.
- **Zero-Remeasurement Kinematics:** Dynamic motion, sliding, collapsing, or docking of UI elements must never modify container layout constraints, bounds, or dimension multipliers (e.g. `height * factor`) during active interaction. All real-time movement must be purely transform-based via GPU-accelerated draw properties (`graphicsLayer { translationX/Y, scaleX/Y }`).
- **Deferred Routing & State Commits in Gestures:** Continuous touch gestures (swipes, drags) must remain entirely decoupled from structural navigation or heavy composition tree changes. Visual feedback during a gesture must be lightweight and isolated. Route changes, screen navigations, and heavy state commits are permitted exclusively upon gesture completion (`onDragEnd`, `onFling`).
