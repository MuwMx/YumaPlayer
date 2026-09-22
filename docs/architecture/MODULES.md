# YumaPlayer Module Directory & Dependency Graph

This document serves as the official reference for the modular structure of YumaPlayer. It defines module categories, layer responsibilities, and explicit dependency rules to maintain architectural integrity and prevent circular dependencies.

---

## 1. Module Design Principles

- **Single Responsibility:** Each module has a focused purpose and encapsulates a single logical domain or subsystem.
- **Explicit Interfaces:** Modules communicate only through exposed domain interfaces or public API surfaces.
- **Feature Isolation:** Feature modules must remain strictly isolated from each other.
- **Centralized Core:** Shared business logic, domain entities, and common utilities belong in `:core:*`.
- **Acyclic Graph:** Circular dependencies between modules are strictly prohibited.

---

## 2. Architecture & Category Hierarchy

YumaPlayer is organized into specialized Gradle modules grouped into logical categories. Dependencies point inward toward shared core abstractions.

```
                ┌──────────────┐
                │    :app      │ (Composition Root)
                └──────┬───────┘
                       │
┌──────────────────────┼─────────────────────────┐
▼                      ▼                         ▼
┌──────────────┐ ┌──────────────┐ ┌───────────────────────────┐
│  :feature:*  │ │  :service:*  │ │ Integrations & Lyrics     │
└──────┬───────┘ └──────┬───────┘ │ (:lyrics:*, :spotifycore, │
       │                │         │  :shazamkit, :canvas,     │
       │                │         │  :lastfm)                 │
       └──────────────┬─┘         └─────────────┬─────────────┘
                      ▼                         ▼
┌─────────────────────────────────────────────────────────────┐
│                           :core:*                           │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Low-Level Data Engines                      │
│            (:moriextractor, :morideobfuscator)              │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Module Categories & Responsibilities

### 📱 Application Root
- **`:app`**
  - **Responsibility:** Application entry point, Dependency Injection graph initialization, main navigation graph, and top-level container hosting.
  - **Rule:** Composition root. No other module may depend on `:app`.

### 🧩 Feature Modules (`:feature:*`)
- **Responsibility:** Isolated UI features and user flows (e.g., search, library, settings, player screen).
- **Rule:** Must depend only on `:core:*` or defined service interfaces. Features cannot import code from other features directly.

### 🎵 Service Modules (`:service:*`)
- **Responsibility:** Background application services, including system media session management (`:service:playback`) and background tasks.
- **Rule:** Operates independently of the UI lifecycle.

### 🔌 Integrations & Extensions
- **`:lyrics:*`** — Standalone providers responsible for parsing and fetching lyrics data (e.g., `:lyrics:lrclib`, `:lyrics:kugou`, `:lyrics:paxsenix`). All submodules implement shared domain interfaces.
- **`:spotifycore`** — Integration with Spotify services for supplemental metadata and video loop assets.
- **`:shazamkit`** — Audio recognition engine integration.
- **`:canvas`** — Video background rendering engine.
- **`:lastfm`** — Scrobbling integration and metadata synchronization.

### ⚙️ Core Infrastructure (`:core:*`)
- **`:core`** — Shared domain abstractions, common math/color utilities, and packed models.
- **`:core:innertube`** — InnerTube API client for YouTube Music (facade, request/response models, page parsers, and proxy rotation).
- **Responsibility:** Shared domain abstractions, common models, networking, persistence, repositories, and application-wide utilities.
- **Rule:** Single source of truth for business logic and data contracts.

### 🛠️ Low-Level Engines
- **`:moriextractor`** — Media extraction engine and stream link resolution utilities.
- **`:morideobfuscator`** — Low-level code execution engine for decoding streaming signatures.
- **Rule:** Isolated data components. Independent of Android UI and upper layers.

---

## 4. Layer Dependency Rules

| Category / Layer | Allowed Dependencies | Forbidden Dependencies |
| :--- | :--- | :--- |
| **Application Root** (`:app`) | `:feature:*`, `:service:*`, `:core:*`, Integrations, `:lyrics:*` | None (Root container) |
| **Features** (`:feature:*`) | `:core:*`, `:service:*` (via controllers/interfaces) | `:app`, other `:feature:*` modules |
| **Services** (`:service:*`) | `:core:*` | `:app`, `:feature:*` |
| **Integrations & Lyrics** | `:core:*` | `:app`, Direct dependencies between independent integration modules |
| **Core Infrastructure** (`:core:*`) | `:moriextractor`, `:morideobfuscator` | `:app`, `:feature:*`, `:service:*`, Integrations, `:lyrics:*` |
| **Low-Level Engines** | Internal utility dependencies only | Higher-level modules (`:core:*`, `:app`, etc.) |

---

## 5. Anti-Patterns & Enforcement Rules

1. **Cross-Provider / Cross-Feature Imports:** Direct imports between `:lyrics:*` submodules or between distinct `:feature:*` modules are forbidden. Shared functionality must be hoisted to `:core:*`.
2. **Circular Dependencies:** Module A depending on Module B while Module B depends on Module A is strictly blocked at the build system level.
3. **Leaky Integration Models:** Exposing third-party DTOs or network response models directly to UI features instead of mapping them through Domain models defined in `:core:*`.
4. **Shared Utility Duplication:** Common utilities, models, or helpers must not be duplicated across modules. Shared functionality belongs in `:core:*`.
