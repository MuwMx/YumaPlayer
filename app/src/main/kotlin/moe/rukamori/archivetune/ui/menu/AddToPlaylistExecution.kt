/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.innertube.YouTube

internal data class AddToPlaylistExecutionResult(
    val songIds: List<String>,
    val withDuplicates: List<Playlist>,
    val duplicatesMap: Map<String, List<String>>,
    val addedPlaylistNames: List<String>,
)

internal suspend fun addSongsToPlaylistSafely(
    playlist: Playlist,
    requestedSongIds: List<String>,
    isLoggedIn: Boolean,
    database: MusicDatabase,
): Int {
    if (requestedSongIds.isEmpty()) return 0

    val browseId = playlist.playlist.browseId
    if (isLoggedIn && browseId != null) {
        val acceptedSongEntries = mutableListOf<Pair<String, String?>>()
        requestedSongIds.forEach { songId ->
            var remoteAdded = false
            var addedSetVideoId: String? = null
            for (attempt in 0 until 3) {
                val result = YouTube.addToPlaylist(browseId, songId)
                if (result.isSuccess) {
                    remoteAdded = true
                    addedSetVideoId = result.getOrNull()
                    break
                }
                if (attempt < 2) delay(250)
            }
            if (remoteAdded) {
                acceptedSongEntries += songId to addedSetVideoId
            }
        }
        if (acceptedSongEntries.isNotEmpty()) {
            database.addSongEntriesToPlaylist(playlist, acceptedSongEntries)
        }
        return acceptedSongEntries.size
    }

    database.addSongToPlaylist(playlist, requestedSongIds)
    return requestedSongIds.size
}

internal suspend fun executeAddToPlaylists(
    database: MusicDatabase,
    availablePlaylists: List<Playlist>,
    selectedPlaylistIdsSnapshot: Set<String>,
    cachedSongIds: List<String>?,
    isLoggedIn: Boolean,
    onGetSong: suspend () -> List<String>,
): AddToPlaylistExecutionResult? {
    val currentSongIds = cachedSongIds ?: onGetSong()
    if (currentSongIds.isEmpty()) return null

    val selectedPlaylists = availablePlaylists.filter { it.id in selectedPlaylistIdsSnapshot }
    if (selectedPlaylists.isEmpty()) return null

    val tempDuplicatesMap = mutableMapOf<String, List<String>>()
    val addedPlaylistIds = mutableSetOf<String>()

    val (playlistsWithDups, playlistsWithoutDups) =
        selectedPlaylists.partition { playlist ->
            val dups = database.playlistDuplicates(playlist.id, currentSongIds)
            if (dups.isNotEmpty()) {
                tempDuplicatesMap[playlist.id] = dups
                true
            } else {
                false
            }
        }

    playlistsWithoutDups.forEach { playlist ->
        val addedCount = addSongsToPlaylistSafely(playlist, currentSongIds, isLoggedIn, database)
        if (addedCount > 0) {
            addedPlaylistIds += playlist.id
        }
    }

    val addedPlaylistNames =
        selectedPlaylists
            .filter { addedPlaylistIds.contains(it.id) }
            .map { it.playlist.name }

    return AddToPlaylistExecutionResult(
        songIds = currentSongIds,
        withDuplicates = playlistsWithDups,
        duplicatesMap = tempDuplicatesMap,
        addedPlaylistNames = addedPlaylistNames,
    )
}

internal fun kotlinx.coroutines.CoroutineScope.launchAddToPlaylists(
    database: MusicDatabase,
    availablePlaylists: List<Playlist>,
    selectedPlaylistIdsSnapshot: Set<String>,
    cachedSongIds: List<String>?,
    isLoggedIn: Boolean,
    onGetSong: suspend () -> List<String>,
    onStart: () -> Unit,
    onEnd: (AddToPlaylistExecutionResult?) -> Unit,
) {
    onStart()
    launch {
        val result =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                executeAddToPlaylists(
                    database = database,
                    availablePlaylists = availablePlaylists,
                    selectedPlaylistIdsSnapshot = selectedPlaylistIdsSnapshot,
                    cachedSongIds = cachedSongIds,
                    isLoggedIn = isLoggedIn,
                    onGetSong = onGetSong,
                )
            }
        onEnd(result)
    }
}
