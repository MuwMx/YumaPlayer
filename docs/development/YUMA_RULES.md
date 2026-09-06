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

---

## 2. Architecture & Module Isolation

- **Strict Layer Inversion:** Inner layers (`:core:domain`, `:core:model`) must never import outer layers (`:data:*`, `:feature:*`, `:service:*`).
- **Feature Isolation:** Feature modules (`:feature:*`) cannot depend on each other directly. Shared code belongs in `:core:*`.
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

- **BackHandler Cascading Hierarchy:** The top-level player `BackHandler` must never be disabled via child overlay flags (`!isLyricsVisible`, `!isOverlayVisible`). Back navigation must cascade strictly from top to bottom:
  1. Dismiss open modal overlays (lyrics, queue).
  2. Collapse the expanded player sheet.
  3. Allow navigation pop in the underlying screen stack.
- **Custom Layout Trimming (Borders):** Clipping modifiers (`clipToBounds`, offset layout shifts) must be applied directly to the container that renders the border or background (`sheetBackground`), never to nested child content. In custom `layout { ... }`, the layout must report `constraints.maxWidth` to the parent to enforce boundaries.
- **Semantic Theme Tokens Only:** Never hardcode `Color.Black`, `Color.White`, or arbitrary hex codes for containers and text. Always use semantic design tokens (`surfaceContainerHighest`, `onSurface`, or YDS tokens) to ensure contrast in both light and dark themes.
- **No Duplicate Side-Effect Writers:** Never keep duplicate `LaunchedEffect` blocks writing to the same `MutableState`. Before adding or extending an effect, remove or refactor existing writers.
- **Single Source for UI Visibility:** Never pipe manual visibility callbacks (`onVisibilityChanged`) across composable layers if that state is already exposed by the ViewModel's UDF StateFlow. Read directly from the state source.
- **Diff Cleanliness:** Zero trailing whitespace. Verify that any newly added modifier produces a verifiable layout or visual change before finalizing changes.