/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.constants.SpotifyLibraryPlaylistsCacheKey
import moe.rukamori.archivetune.constants.SpotifyLikedSongsCacheKey
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyCacheStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun loadPlaylists(): List<SpotifyPlaylist> =
            withContext(Dispatchers.IO) {
                val cached =
                    context.dataStore.data
                        .first()[SpotifyLibraryPlaylistsCacheKey]
                        .orEmpty()
                if (cached.isBlank()) return@withContext emptyList()
                runCatching {
                    spotifyCacheJson.decodeFromString(
                        ListSerializer(SpotifyPlaylist.serializer()),
                        cached,
                    )
                }.getOrElse { error ->
                    reportException(error)
                    context.dataStore.edit { prefs ->
                        prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    }
                    emptyList()
                }
            }

        suspend fun savePlaylists(playlists: List<SpotifyPlaylist>) {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { prefs ->
                    prefs[SpotifyLibraryPlaylistsCacheKey] =
                        spotifyCacheJson.encodeToString(
                            ListSerializer(SpotifyPlaylist.serializer()),
                            playlists,
                        )
                }
            }
        }

        suspend fun loadLikedSongs(): List<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                val cached =
                    context.dataStore.data
                        .first()[SpotifyLikedSongsCacheKey]
                        .orEmpty()
                if (cached.isBlank()) return@withContext emptyList()
                runCatching {
                    spotifyCacheJson.decodeFromString(
                        ListSerializer(SpotifyTrack.serializer()),
                        cached,
                    )
                }.getOrElse { error ->
                    reportException(error)
                    context.dataStore.edit { prefs ->
                        prefs.remove(SpotifyLikedSongsCacheKey)
                    }
                    emptyList()
                }
            }

        suspend fun saveLikedSongs(tracks: List<SpotifyTrack>) {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { prefs ->
                    prefs[SpotifyLikedSongsCacheKey] =
                        spotifyCacheJson.encodeToString(
                            ListSerializer(SpotifyTrack.serializer()),
                            tracks,
                        )
                }
            }
        }

        suspend fun clearPlaylistsCache() {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { prefs ->
                    prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                }
            }
        }

        suspend fun clearLikedSongsCache() {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { prefs ->
                    prefs.remove(SpotifyLikedSongsCacheKey)
                }
            }
        }

        companion object {
            private val spotifyCacheJson =
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
        }
    }
