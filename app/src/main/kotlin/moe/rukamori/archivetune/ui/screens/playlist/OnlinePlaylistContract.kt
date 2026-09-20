/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.ui.utils.ItemWrapper

typealias WrappedSongItem = ItemWrapper<Pair<Int, SongItem>>

internal const val ONLINE_PLAYLIST_KEY_SHIMMER = "shimmer"
internal const val ONLINE_PLAYLIST_KEY_HEADER = "header"
internal const val ONLINE_PLAYLIST_KEY_EMPTY = "empty"
internal const val ONLINE_PLAYLIST_KEY_LOADING_MORE = "loading_more"
internal const val ONLINE_PLAYLIST_KEY_ERROR = "error"

internal const val CONTENT_TYPE_ONLINE_PLAYLIST_SHIMMER = "shimmer"
internal const val CONTENT_TYPE_ONLINE_PLAYLIST_HEADER = "header"
internal const val CONTENT_TYPE_ONLINE_PLAYLIST_EMPTY = "empty"
internal const val CONTENT_TYPE_ONLINE_PLAYLIST_SONG = "online_playlist_song"
internal const val CONTENT_TYPE_ONLINE_PLAYLIST_LOADING_MORE = "loading_more"
internal const val CONTENT_TYPE_ONLINE_PLAYLIST_ERROR = "error"

fun onlinePlaylistSongKey(wrappedSong: WrappedSongItem): Any =
    wrappedSong.item.second.setVideoId ?: "${wrappedSong.item.second.id}-${wrappedSong.item.first}"

@Immutable
data class OnlinePlaylistUiState(
    val playlist: PlaylistItem? = null,
    val songs: List<SongItem> = emptyList(),
    val viewCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val isBookmarked: Boolean = false,
    val hasContinuation: Boolean = false,
)

@Immutable
data class OnlinePlaylistActions(
    val onPlay: () -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onRadio: () -> Unit = {},
    val onMix: () -> Unit = {},
    val onSongClick: (WrappedSongItem) -> Unit = {},
    val onSongLongClick: (WrappedSongItem) -> Unit = {},
    val onLike: () -> Unit = {},
    val onMenu: () -> Unit = {},
    val onSongMenu: (SongItem) -> Unit = {},
    val onDownload: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onArtistClick: (String) -> Unit = {},
)
