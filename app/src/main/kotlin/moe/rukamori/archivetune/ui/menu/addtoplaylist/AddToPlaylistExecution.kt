/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu.addtoplaylist

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed

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
        val entriesBefore = database.playlistSongMaps(playlist.id, 0)

        val batchSetVideoIds =
            YouTube
                .addSongsToPlaylist(browseId, requestedSongIds)
                .getOrNull()
                ?.filterNotNull()
                ?.takeIf { it.size == requestedSongIds.size }
        if (batchSetVideoIds != null) {
            database.addSongEntriesToPlaylist(playlist, requestedSongIds.zip(batchSetVideoIds))
            return requestedSongIds.size
        }

        val remoteEntries = YouTube.playlist(browseId).completed().getOrNull()?.songs ?: return 0
        val addedByFailedBatch = setVideoIdsAddedByFailedBatch(entriesBefore, remoteEntries, requestedSongIds)
        val acceptedSongEntries = mutableListOf<Pair<String, String>>()
        requestedSongIds.forEach { songId ->
            val setVideoId = addedByFailedBatch[songId]?.removeFirstOrNull() ?: addSongWithRetries(browseId, songId)
            if (setVideoId != null) {
                acceptedSongEntries += songId to setVideoId
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

private fun setVideoIdsAddedByFailedBatch(
    entriesBefore: List<PlaylistSongMap>,
    remoteEntries: List<SongItem>,
    requestedSongIds: List<String>,
): Map<String, ArrayDeque<String>> {
    val knownSetVideoIds = entriesBefore.mapNotNullTo(HashSet()) { it.setVideoId }
    val countsBefore = entriesBefore.groupingBy { it.songId }.eachCount()
    return requestedSongIds.toSet().associateWith { songId ->
        val remoteCopies = remoteEntries.filter { it.id == songId }
        val addedCount = (remoteCopies.size - (countsBefore[songId] ?: 0)).coerceAtLeast(0)
        val unknownSetVideoIds = remoteCopies.mapNotNull { it.setVideoId }.filter { it !in knownSetVideoIds }
        ArrayDeque(unknownSetVideoIds.takeLast(addedCount))
    }
}

private suspend fun addSongWithRetries(
    browseId: String,
    songId: String,
): String? {
    for (attempt in 0 until 3) {
        val result = YouTube.addToPlaylist(browseId, songId)
        if (result.isSuccess) return result.getOrNull()
        if (attempt < 2) delay(250)
    }
    return null
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
