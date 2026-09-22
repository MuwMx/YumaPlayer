# YumaPlayer Documentation

This directory contains internal documentation for YumaPlayer.

It is the primary entry point for contributors and AI assistants working on the project.

---

## About YumaPlayer

YumaPlayer is a modular Android music player focused on maintainability, modularity, and a consistent user experience.

The project combines YouTube Music streaming, lossless FLAC streaming, local playback, synchronized lyrics, Spotify Canvas visualizers, Shazam track recognition, and multiple optional service integrations while keeping every subsystem isolated in dedicated modules.

Originally evolved from ArchiveTune (with core dependencies traced back to Metrolist and SimpMusic), YumaPlayer has developed into an independent project with its own architecture, design system, and engineering standards.

### Architecture Overview

The codebase is organized into independent modules with clearly defined responsibilities. Each module has a single responsibility and communicates through well-defined interfaces:

- **`:app`** — Entry point, application class, and DI composition root (UI, playback, colocated domain).
- **`:designsystem`** — YDS tokens and Yuma UI Kit.
- **`:database`** — Room persistence (v36).
- **`:core` / `:core:innertube`** — Shared logic and InnerTube API client.
- **`:lyrics:*`** — Isolated lyrics providers (`lrclib`, `kugou`, `paxsenix`, `simpmusic`, `betterlyrics`, `unison`, `youlyplus`).
- **`:spotifycore` / `:shazamkit` / `:canvas` / `:lastfm` / `:flaccore`** — Dedicated third-party, recognition, visual, scrobbling, and lossless integrations (19 modules total, see `architecture/MODULES.md`).

---

## Documentation Structure

Documentation is organized into three main areas:

### 1. 🏛️ [Architecture](./architecture/)
System design, module boundaries, dependencies, and architectural decision records.
- **ARCHITECTURE.md** — High-level Clean Architecture rules and data flow.
- **MODULES.md** — Comprehensive catalog of all project modules and dependency matrix.
- **DECISIONS.md** — Architectural Decision Records (ADRs: M3 Expressive, HCT, Media3, UDF).

### 2. 🛠️ [Development](./development/)
Development rules, standards, step-by-step guides, and agent guidelines.
- **YUMA_RULES.md** — Non-negotiable project constitution and strict constraints.
- **CODING_STANDARD.md** — Code style, ViewModel conventions, Mappers, and Coroutine safety.
- **DEVELOPMENT_GUIDE.md** — Step-by-step recipes (adding screens, lyrics providers, new modules).

### 3. 🎨 [Design](./design/)
UI design system, tokens, and component guidelines.
- **YDS.md** — Yuma Design System 2.1 specification (tokens, HCT color engine, elevation, motion).
- **COMPONENT_GUIDE.md** — Guidelines for creating stateless Yuma UI Kit components.

---

## Documentation Paths

### 🆕 If you are new to the project:
1. **docs/README.md** (this file) — Overview and layout.
2. **docs/architecture/ARCHITECTURE.md** — Basic architecture concept.
3. **docs/development/YUMA_RULES.md** — Absolute project prohibitions and rules.
4. **docs/design/YDS.md** — UI principles and visual language.

### 🎨 If you are implementing UI / Compose:
1. **docs/development/YUMA_RULES.md** — UI prohibitions (no raw Material buttons, no nested cards).
2. **docs/design/YDS.md** — Colors, surfaces, typography, elevation.
3. **docs/design/COMPONENT_GUIDE.md** — Stateless component rules, previews, state hoisting.
4. **docs/development/CODING_STANDARD.md** — ViewModel state flows, Compose stability (`@Immutable`, `collectAsStateWithLifecycle`).

### ⚙️ If you are working on Data / Core / Network:
1. **docs/architecture/ARCHITECTURE.md** — Domain layer boundaries and UDF.
2. **docs/architecture/MODULES.md** — Module dependencies (`:app`, `:designsystem`, `:database`, `:core`, `:core:innertube`).
3. **docs/development/CODING_STANDARD.md** — Repositories, DTO mapping, `Dispatchers.IO` rules.

### 🎵 If you are working on Lyrics Providers:
1. **docs/architecture/MODULES.md** — `:lyrics` submodules catalog.
2. **docs/development/DEVELOPMENT_GUIDE.md** — How to add a new Lyrics Provider.

---

## Documentation Principles

- **Single Responsibility:** Each document addresses a specific architectural, design, or workflow domain.
- **Long-term Conventions:** Documentation describes long-term project standards rather than temporary implementation details.
- **Synchronization:** Documentation should reflect the current architecture and implementation. Whenever architectural decisions change, the relevant documentation should be updated accordingly.

---

## Documentation Maintenance

Documentation is considered part of the source code.

Any architectural, UI, or workflow change must update the relevant documentation within the same pull request.
