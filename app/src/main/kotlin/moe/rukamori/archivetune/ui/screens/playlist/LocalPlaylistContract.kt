/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.models.PlaylistSuggestion

internal const val LOCAL_PLAYLIST_KEY_HEADER = "header"
internal const val LOCAL_PLAYLIST_KEY_SORT_HEADER = "sort_header"
internal const val LOCAL_PLAYLIST_KEY_EMPTY = "empty"
internal const val LOCAL_PLAYLIST_KEY_SUGGESTIONS = "suggestions"

internal const val CONTENT_TYPE_LOCAL_PLAYLIST_HEADER = "header"
internal const val CONTENT_TYPE_LOCAL_PLAYLIST_SORT_HEADER = "sort_header"
internal const val CONTENT_TYPE_LOCAL_PLAYLIST_EMPTY = "empty"
internal const val CONTENT_TYPE_LOCAL_PLAYLIST_SONG = "local_playlist_song"
internal const val CONTENT_TYPE_LOCAL_PLAYLIST_SUGGESTIONS = "suggestions"

fun localPlaylistSongKey(mapId: Int, index: Int): Any = "${mapId}_$index"
fun localPlaylistSongKey(song: PlaylistSong, index: Int): Any = "${song.map.id}_$index"

@Immutable
data class LocalPlaylistUiState(
    val playlist: Playlist? = null,
    val songs: List<PlaylistSong> = emptyList(),
    val sortType: PlaylistSongSortType = PlaylistSongSortType.CUSTOM,
    val sortDescending: Boolean = false,
    val viewCounts: Map<String, Int> = emptyMap(),
    val suggestions: PlaylistSuggestion? = null,
    val isRefreshing: Boolean = false,
    val isLoadingSuggestions: Boolean = false,
)

@Immutable
data class LocalPlaylistActions(
    val onPlay: () -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onMix: () -> Unit = {},
    val onSongClick: (index: Int, song: PlaylistSong) -> Unit = { _, _ -> },
    val onSongLongClick: (PlaylistSong) -> Unit = {},
    val onMenu: (PlaylistSong) -> Unit = {},
    val onSort: (PlaylistSongSortType, Boolean) -> Unit = { _, _ -> },
    val onSortTypeChange: (PlaylistSongSortType) -> Unit = {},
    val onSortDescendingChange: (Boolean) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onReorderCommit: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onDownload: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onToggleLike: () -> Unit = {},
    val onSync: () -> Unit = {},
    val onPickCover: () -> Unit = {},
    val onLockToggle: () -> Unit = {},
)
