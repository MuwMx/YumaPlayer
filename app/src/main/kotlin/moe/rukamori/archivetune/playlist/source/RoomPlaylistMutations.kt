/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import android.content.Context
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.innertube.YouTube
import java.time.LocalDateTime

/**
 * LOCAL PLAYLIST SLOT — the local-only mutations the unified screen drives
 * beyond the base interface (rename / delete / pick-cover / like /
 * remove-downloads), ported verbatim from LocalPlaylistWiring.kt:356-467 so
 * [RoomPlaylistSource] stays under the 250-line rule. The DB write always
 * wins; remote calls fire after it and never surface fatal failures — the
 * legacy wiring treated them the same way.
 */
internal suspend fun RoomPlaylistSource.removeSongLocalOnly(position: Int): Result<Unit> =
    runCatching {
        val map = currentOrderSongs().getOrNull(position)?.map ?: return@runCatching
        database.withTransaction {
            move(map.playlistId, map.position, Int.MAX_VALUE)
            delete(map.copy(position = Int.MAX_VALUE))
        }
    }

/** Rename (legacy renameLocalPlaylist, Wiring.kt:367-384). */
internal suspend fun RoomPlaylistSource.rename(name: String): Result<Unit> =
    runCatching {
        val playlistRow =
            database.getPlaylistById(playlistId) ?: return@runCatching
        database.query {
            update(
                playlistRow.playlist.copy(
                    name = name,
                    lastUpdateTime = LocalDateTime.now(),
                ),
            )
        }
        playlistRow.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
    }

/**
 * Delete the whole playlist (legacy deleteLocalPlaylist, Wiring.kt:386-399):
 * the local delete fires first, then the remote delete for synced playlists;
 * [onDeleted] runs either way (it pops the screen).
 */
internal suspend fun RoomPlaylistSource.deletePlaylist(onDeleted: () -> Unit): Result<Unit> =
    runCatching {
        val playlistRow = database.getPlaylistById(playlistId)
        if (playlistRow == null) {
            onDeleted()
            return@runCatching
        }
        database.query {
            delete(playlistRow.playlist)
        }
        playlistRow.playlist.browseId?.let { YouTube.deletePlaylist(it) }
        onDeleted()
    }

/**
 * Pick-cover persistence (legacy updateLocalPlaylistCover, Wiring.kt:401-437):
 * take the persistable read grant, release the replaced content URI's grant,
 * then store the new cover with a bumped lastUpdateTime.
 */
internal suspend fun RoomPlaylistSource.updateCover(
    context: Context,
    uri: android.net.Uri?,
): Result<Unit> =
    runCatching {
        if (uri == null) return@runCatching
        val playlistRow =
            database.getPlaylistById(playlistId) ?: return@runCatching
        val oldUriString = playlistRow.playlist.thumbnailUrl
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        if (!oldUriString.isNullOrBlank() && oldUriString != uri.toString()) {
            val oldUri = runCatching { android.net.Uri.parse(oldUriString) }.getOrNull()
            if (oldUri?.scheme == "content") {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        oldUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
        }
        database.query {
            update(
                playlistRow.playlist.copy(
                    thumbnailUrl = uri.toString(),
                    lastUpdateTime = LocalDateTime.now(),
                ),
            )
        }
    }

/** Bookmark toggle for non-editable synced playlists (toggleLocalPlaylistLike, Wiring.kt:356-365). */
internal suspend fun RoomPlaylistSource.toggleLike(): Result<Unit> =
    runCatching {
        val playlistRow =
            database.getPlaylistById(playlistId) ?: return@runCatching
        database.transaction {
            update(playlistRow.playlist.toggleLike())
        }
    }

/** The like state the hero's heart button should show. */
internal suspend fun RoomPlaylistSource.currentLiked(): Boolean =
    database.playlist(playlistId).first()?.playlist?.bookmarkedAt != null

/**
 * Remove-downloads confirm path (legacy removeLocalPlaylistDownloads,
 * Wiring.kt:439-455): non-editable playlists also CLEAR their songs before
 * dropping the downloads. [onSendRemoveDownloads] is a UI-layer lambda that
 * actually dispatches the download-removal command so the domain package never
 * imports UI utilities.
 */
internal suspend fun RoomPlaylistSource.removeDownloads(
    editable: Boolean,
    onSendRemoveDownloads: (List<String>) -> Unit,
): Result<Unit> =
    runCatching {
        if (!editable) {
            database.transaction { clearPlaylist(playlistId) }
        }
        val songs = database.playlistSongs(playlistId).first()
        onSendRemoveDownloads(songs.map { it.song.id })
    }
