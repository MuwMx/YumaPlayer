package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.constants.SpotifyProfileArtistsKey
import moe.rukamori.archivetune.constants.SpotifyProfileCacheTsKey
import moe.rukamori.archivetune.constants.SpotifyProfileRecentItemsKey
import moe.rukamori.archivetune.constants.SpotifyProfileTopTracksKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.models.SpotifyRecentItem
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyProfileCachedData(
    val recentItems: List<SpotifyRecentItem> = emptyList(),
    val topTracks: List<SpotifyTrack> = emptyList(),
    val frequentArtists: List<SpotifyArtist> = emptyList(),
    val timestamp: Long = 0L,
)

@Singleton
class SpotifyProfileCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    suspend fun restoreFromDataStore(): SpotifyProfileCachedData {
        val prefs = context.dataStore.data.firstOrNull() ?: return SpotifyProfileCachedData()
        val ts = prefs[SpotifyProfileCacheTsKey] ?: 0L
        val recentItemsJson = prefs[SpotifyProfileRecentItemsKey]
        val topTracksJson = prefs[SpotifyProfileTopTracksKey]
        val artistsJson = prefs[SpotifyProfileArtistsKey]

        val recentItems = recentItemsJson?.let {
            runCatching { json.decodeFromString(ListSerializer(SpotifyRecentItem.serializer()), it) }.getOrNull()
        }.orEmpty()

        val topTracks = topTracksJson?.let {
            runCatching { json.decodeFromString(ListSerializer(SpotifyTrack.serializer()), it) }.getOrNull()
        }.orEmpty()

        val artists = artistsJson?.let {
            runCatching { json.decodeFromString(ListSerializer(SpotifyArtist.serializer()), it) }.getOrNull()
        }.orEmpty()

        return SpotifyProfileCachedData(
            recentItems = recentItems,
            topTracks = topTracks,
            frequentArtists = artists,
            timestamp = ts,
        )
    }

    suspend fun persistToDataStore(
        recentItems: List<SpotifyRecentItem>,
        topTracks: List<SpotifyTrack>,
        frequentArtists: List<SpotifyArtist>,
    ) {
        context.dataStore.edit { prefs ->
            prefs[SpotifyProfileCacheTsKey] = System.currentTimeMillis()
            if (recentItems.isNotEmpty()) {
                runCatching { prefs[SpotifyProfileRecentItemsKey] = json.encodeToString(ListSerializer(SpotifyRecentItem.serializer()), recentItems) }
            }
            if (topTracks.isNotEmpty()) {
                runCatching { prefs[SpotifyProfileTopTracksKey] = json.encodeToString(ListSerializer(SpotifyTrack.serializer()), topTracks) }
            }
            if (frequentArtists.isNotEmpty()) {
                runCatching { prefs[SpotifyProfileArtistsKey] = json.encodeToString(ListSerializer(SpotifyArtist.serializer()), frequentArtists) }
            }
        }
    }

    suspend fun clearCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(SpotifyProfileRecentItemsKey)
            prefs.remove(SpotifyProfileTopTracksKey)
            prefs.remove(SpotifyProfileArtistsKey)
            prefs.remove(SpotifyProfileCacheTsKey)
        }
    }
}
