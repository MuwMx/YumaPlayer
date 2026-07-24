# Contributing

## Code of Conduct

This project follows the [Contributor Covenant](https://www.contributor-covenant.org/) code of conduct. Be respectful, constructive, and inclusive.

## Pull Request Process

1. Create a feature branch from `main`
2. Make your changes
3. Run tests: `./gradlew test`
4. Submit a PR using the [PR template](../.github/pull_request_template.md)
5. Ensure CI passes
6. Respond to review feedback

## Standards

### Code style

- Follow the existing code style in the module you're modifying
- Use `object` singletons for providers (no classes unless stateful)
- Keep providers stateless — pass everything as parameters
- No Android imports (these are JVM libraries)

### Naming

| Convention | Example |
|---|---|
| Provider object | `KuGou`, `LrcLib`, `PaxsenixLyrics` |
| Source file | `KuGou.kt`, `LrcLib.kt` |
| Package | `com.muwmx.yuma.<name>` |
| Model files | `models/Track.kt`, `models/LyricsResponse.kt` |

### Format

Keep files under 500 lines where possible. Split models into separate files when you have more than 2-3 classes. Use `@SerialName` for non-Kotlin field names in API responses.

## What to contribute

- **New lyrics sources** — see [ADDING_A_PROVIDER.md](./ADDING_A_PROVIDER.md)
- **Bug fixes** — broken parsing, incorrect matching, API changes
- **Improvements** — better duration matching, additional response formats, performance
- **Tests** — unit tests for providers

## What not to contribute

- Android-specific code (belongs in the main ArchiveTune repo)
- Providers that require authentication tokens hardcoded in the module
- Changes that introduce cross-module dependencies
