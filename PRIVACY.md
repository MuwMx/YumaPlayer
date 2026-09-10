# YumaPlayer Privacy Notice

Last updated: 2026-09-10

## Scope

This notice covers the Android YumaPlayer app in this repository. It explains what the app stores on your device, what it can send to external services when you use specific features, and what Android permissions it requests.

This notice is based on the current source code and build configuration. It does not replace the privacy terms of YouTube or YouTube Music, Spotify, Qobuz, Last.fm, ListenBrainz, Discord, GitHub, lyrics providers, canvas visualizer services, or any Together server you choose to use.

## Privacy Summary

- Most core app data is stored locally on your device.
- YumaPlayer does not secretly harvest, sell, or broker your personal data.
- YumaPlayer does not silently send your data to unrelated third-party services.
- Optional network features send only the data needed to provide those features.
- If data leaves your device, it is because you used a specific online feature or integration that requires that transfer.
- Android backup and device-transfer features may copy part of the app's local data unless excluded by the app's backup rules.
- The app also includes a user-triggered backup export feature.
- The current Android build configuration does not include mobile advertising SDKs, third-party analytics SDKs, or automatic crash-reporting SDKs.
- The current Android manifest does not request location, contacts, camera, calendar, SMS, or call log permissions.

## Data the App May Store on Your Device

The app stores data locally to provide playback, library, search, lyrics, sync, and customization features.

| Category | Examples visible in the codebase | Why it is stored |
| --- | --- | --- |
| Library and playback data | Song, artist, album, playlist, like state, download state, total play time, audio format metadata | Library management, playback, downloads, and statistics |
| Search and lyrics data | Search queries, cached lyrics, and romanization maps | Search history, offline lyrics, and phonetic displays |
| Listening history data | Playback event records with song ID, timestamp, and play time | Listening stats and history-related features |
| App settings | Language, country, UI settings, audio settings, proxy settings, cache settings, history pause toggles, Together settings | Personalization and feature configuration |
| Optional account and session data | YouTube account name, email, channel handle, visitor data, data sync ID, cookie, PO token values | Signed-in YouTube and YouTube Music functionality |
| Optional third-party integration data | Spotify access and refresh tokens, Qobuz user auth token and credentials, Last.fm session and username, ListenBrainz token, Discord OAuth access and refresh tokens, Together display name and client ID | External integrations you choose to enable |
| Cached files | Streaming cache, download cache, resolved stream URLs, and album artwork | Faster playback, offline use, and feature performance |

## Data the App May Send Off Your Device

YumaPlayer does not silently forward your data to unrelated services. It only contacts external services when you use online features, and the exact payload depends on the feature you use and how you configure it.

| Service or feature | Data that may be sent | When it happens |
| --- | --- | --- |
| YouTube or YouTube Music | Search terms, media playback requests, library or playlist requests, and signed-in session values such as visitor data, sync identifiers, cookies, or token values | When you browse, stream, sync, or sign in |
| Qobuz / Lossless Streaming | Track metadata search queries, playback stream requests, and user-provided API tokens | When lossless FLAC playback or downloading is active |
| Spotify | Library sync requests, track matching queries, like/unlike calls, and OAuth tokens | When Spotify playlist sync, library sync, or Canvas features are enabled |
| ShazamKit / Recognition | Audio fingerprint samples and acoustic hashes | When you use the in-app track recognition feature |
| Lyrics providers (LRCLIB, Paxsenix, etc.) | Song title, artist name, duration, and album identifiers | When lyrics lookup or synchronization is requested |
| Canvas Visualizer Service | Song and artist names, album ID, or album URL | When animated canvas or artwork visualizers are enabled |
| Last.fm | Now playing and scrobble metadata, plus your Last.fm session information | When Last.fm scrobbling is enabled |
| ListenBrainz | Playback history or scrobble metadata and your ListenBrainz token | When ListenBrainz sync is enabled |
| Discord Rich Presence | Current track, artist, album, elapsed time, and Discord OAuth tokens | When Discord Rich Presence is enabled |
| GitHub releases | Update-check requests and cached release metadata | When the app checks for new versions |
| Together | Display name, client ID, session code, playback state, and queue actions | When you host or join a shared Together room |

## Android Permissions

The app declares the following Android permissions in the current manifest.

| Permission | Why the app requests it |
| --- | --- |
| `INTERNET` | Connect to YouTube, Qobuz, Spotify, lyrics services, update endpoints, and streaming backends |
| `POST_NOTIFICATIONS` | Show playback controls and download notifications |
| `ACCESS_NETWORK_STATE` | Detect connectivity and adapt network caching behavior |
| `READ_MEDIA_AUDIO` | Read local audio files on Android 13+ |
| `READ_EXTERNAL_STORAGE` on Android 12 and below | Support local audio access on older Android versions |
| `RECORD_AUDIO` | Support acoustic recognition features (ShazamKit) |
| `BLUETOOTH_CONNECT` | Integrate with Bluetooth audio devices and hardware playback controls |
| `RECEIVE_BOOT_COMPLETED` | Restore playback-related behavior after a device restart |
| `WAKE_LOCK` | Prevent system sleep while audio playback is in progress |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC` | Support background playback, audio streaming, and data synchronization |

## Backups, Device Transfer, and Local Retention

YumaPlayer enables standard Android backup support. Backup and data-transfer rules explicitly exclude streaming caches and download directories (`exoplayer_internal.db`, cached audio segments). Local databases (playlists, likes, history) and app preferences may be backed up according to system settings.

The app also provides a manual backup feature that creates a ZIP archive containing app settings and database files upon explicit user trigger.

Unless removed, app data remains on your device until:
- you clear app storage in system settings,
- you uninstall the application,
- you manually delete items from within the library, or
- Android backup restores or overwrites state.

## Security Notes and Limitations

- **Cleartext Traffic:** The manifest allows cleartext traffic to support local proxy routing, self-hosted streaming endpoints, and custom network setups. External connections to public APIs use HTTPS whenever supported by the endpoint.
- **Audio Capture:** The manifest allows playback capture under standard Android system rules (e.g., system equalizers, accessibility capture, or authorized screen recording).
- **Local Storage:** App databases, credentials, and settings are stored within protected app-internal sandbox storage, without custom database encryption-at-rest.

## Project Contact

For questions or corrections, use the project repository and issue tracker:
- Repository: [https://github.com/MuwMx/YumaPlayer](https://github.com/MuwMx/YumaPlayer)
- Issues: [https://github.com/MuwMx/YumaPlayer/issues](https://github.com/MuwMx/YumaPlayer/issues)

## Technical Appendix

| Topic | What the code shows | Main files |
| --- | --- | --- |
| Permissions and backup behavior | Manifest declares background services, network, audio, and notification permissions. Backup exclusions filter out media caches. | `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml` |
| Local database contents | The Room schema includes songs, artists, albums, playlists, search history, lyrics, Spotify matches, format metadata, and playback history. | `app/schemas/moe.rukamori.archivetune.db.InternalDatabase/35.json` |
| Settings and tokens stored locally | DataStore preference keys include UI preferences, Qobuz tokens, Spotify session tokens, YouTube cookies, Last.fm sessions, and Together configurations. | `core/src/main/kotlin/moe/rukamori/archivetune/constants/PreferenceKeys.kt` |
| Streaming and FLAC handling | Media3 ExoPlayer integration with multi-source resolving (YouTube Innertube and Qobuz FLAC endpoints). | `service/playback/`, `core/src/main/kotlin/.../innertube/YouTube.kt` |
| Spotify Integration | Library synchronization, Spotify URIs resolving, and Canvas fetchers. | `spotifycore/`, `app/src/main/kotlin/.../spotify/` |
| Manual backup export | Backs up local Room database and DataStore preferences into a single user-managed archive. | `app/src/main/kotlin/.../viewmodels/BackupRestoreViewModel.kt` |