/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.LastSpotifySyncKey
import moe.rukamori.archivetune.spotify.sync.SpotifyLikedSync
import moe.rukamori.archivetune.spotify.sync.SpotifyPlaylistSync
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifySyncOps
    @Inject
    constructor(
        private val state: SyncState,
        private val playlistSync: SpotifyPlaylistSync,
        private val likedSync: SpotifyLikedSync,
    ) {
        constructor(state: SyncState) : this(
            state = state,
            playlistSync = SpotifyPlaylistSync(state),
            likedSync = SpotifyLikedSync(state),
        )

        private val isAutoSyncInFlight = AtomicBoolean(false)

        suspend fun syncSpotifyPlaylists(authoritative: Boolean = false) =
            playlistSync.syncSpotifyPlaylists(authoritative)

        suspend fun syncSpotifyLikedSongs(
            authoritative: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = likedSync.syncSpotifyLikedSongs(authoritative, onProgress)

        fun triggerAutoSyncIfDue(authoritative: Boolean = false) {
            trySpotifyAutoSync(authoritative)
        }

        fun trySpotifyAutoSync(authoritative: Boolean = false) {
            if (!isAutoSyncInFlight.compareAndSet(false, true)) {
                Timber.d("Spotify auto-sync already in flight, skipping")
                return
            }
            state.syncScope.launch {
                try {
                    val session = state.spotifyRepository.restoreSession()
                    if (!session.isAuthenticated) return@launch

                    val lastSync = state.context.dataStore.data.map { it[LastSpotifySyncKey] ?: 0L }.first()
                    val currentTime = System.currentTimeMillis()
                    if (!authoritative && lastSync > 0 && (currentTime - lastSync) < SPOTIFY_SYNC_COOLDOWN_MS) {
                        Timber.d("Skipping Spotify auto-sync - cooldown active")
                        return@launch
                    }

                    syncSpotifyPlaylists(authoritative = authoritative)
                    syncSpotifyLikedSongs(authoritative = authoritative)
                } catch (e: Exception) {
                    Timber.e(e, "Failed trySpotifyAutoSync")
                } finally {
                    try {
                        state.context.dataStore.edit { prefs ->
                            prefs[LastSpotifySyncKey] = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to update LastSpotifySyncKey")
                    } finally {
                        isAutoSyncInFlight.set(false)
                    }
                }
            }
        }

        companion object {
            private const val SPOTIFY_SYNC_COOLDOWN_MS = 30 * 60 * 1000L
        }
    }
