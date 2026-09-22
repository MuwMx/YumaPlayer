# Contributing to YumaPlayer

Thanks for considering contributing to YumaPlayer! 

## 🌍 Translations
All translations are managed exclusively via **Hosted Weblate**. Please do not open manual pull requests for `strings.xml` edits directly on GitHub.
👉 **[Join the translation team on Weblate](https://hosted.weblate.org/engage/yumaplayer/)**

## 🐛 Bug Reports & Feature Requests
- Check existing [Issues](https://github.com/MuwMx/YumaPlayer/issues) before opening a new one to avoid duplicates.
- When reporting a playback or metadata bug, please attach your Android version, device model, and Logcat output if possible.

## 💻 Code Contributions
1. Fork the repo and create your branch from `main` (e.g. `feat/new-audio-effect` or `fix/lyrics-timeout`).
2. Adhere to our architectural rules (Compose, UDF, 19-module boundaries — there are no `:feature:*` or `:service:*` modules). See [Architecture Docs](docs/architecture/ARCHITECTURE.md), [Modules](docs/architecture/MODULES.md), and [Yuma Rules](docs/development/YUMA_RULES.md).
3. Follow the [Coding Standard](docs/development/CODING_STANDARD.md) (naming, mappers, Flow collection).
4. Keep `docs/` in sync when your change affects architecture, modules, or workflows.
5. Ensure the project builds cleanly: `./gradlew assembleRelease` (JDK 21, Android SDK 37).
6. Open a Pull Request with a clear explanation of what was changed and why.
