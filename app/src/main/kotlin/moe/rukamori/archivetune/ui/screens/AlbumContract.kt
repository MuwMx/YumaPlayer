/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.utils.ItemWrapper

typealias AlbumUiState = moe.rukamori.archivetune.viewmodels.AlbumUiState
typealias WrappedSong = ItemWrapper<Song>

internal const val ALBUM_KEY_HEADER = "header"
internal const val ALBUM_KEY_SONGS_HEADER = "songs_header"
internal const val ALBUM_KEY_OTHER_VERSIONS_HEADER = "other_versions_header"
internal const val ALBUM_KEY_OTHER_VERSIONS_LIST = "other_versions_list"
internal const val ALBUM_KEY_SHIMMER = "shimmer"
internal const val ALBUM_KEY_EMPTY = "empty"
internal const val ALBUM_KEY_ERROR = "error"

internal const val CONTENT_TYPE_ALBUM_HEADER = "header"
internal const val CONTENT_TYPE_ALBUM_SONGS_HEADER = "songs_header"
internal const val CONTENT_TYPE_ALBUM_SONG = "song"
internal const val CONTENT_TYPE_ALBUM_OTHER_VERSIONS_HEADER = "other_versions_header"
internal const val CONTENT_TYPE_ALBUM_OTHER_VERSIONS_LIST = "other_versions_list"
internal const val CONTENT_TYPE_ALBUM_SHIMMER = "shimmer"
internal const val CONTENT_TYPE_ALBUM_EMPTY = "empty"
internal const val CONTENT_TYPE_ALBUM_ERROR = "error"

@Immutable
data class AlbumActions(
    val onPlay: () -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onSongClick: (startIndex: Int) -> Unit = {},
    val onLike: () -> Unit = {},
    val onMenu: () -> Unit = {},
    val onDownload: () -> Unit = {},
)
