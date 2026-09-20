/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.artist

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ALBUM
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ARTIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_HEADER
import moe.rukamori.archivetune.constants.CONTENT_TYPE_LIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_PLAYLIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.viewmodels.ArtistBlockState

internal const val ARTIST_KEY_SHIMMER = "shimmer"
internal const val ARTIST_KEY_HEADER = "header"

fun artistLocalSongKey(id: String, index: Int): Any = "local_song_${id}_$index"
fun artistLocalAlbumKey(id: Long, index: Int): Any = "local_album_${id}_$index"
fun artistOnlineSectionHeaderKey(title: String, firstItemId: String, browseId: String): Any =
    "youtube_section_header_${title}_${firstItemId}_${browseId}"
fun artistOnlineSongKey(id: String, index: Int): Any = "youtube_song_${id}_$index"
fun artistOnlineSectionGridKey(title: String, firstItemId: String, browseId: String): Any =
    "youtube_section_grid_${title}_${firstItemId}_${browseId}"
fun artistOnlineGridItemKey(type: String, id: String): Any = "youtube_${type}_$id"

@Immutable
data class ArtistUiState(
    val artistPage: ArtistPage? = null,
    val libraryArtist: Artist? = null,
    val librarySongs: List<Song> = emptyList(),
    val libraryAlbums: List<Album> = emptyList(),
    val blockState: ArtistBlockState = ArtistBlockState.Loading,
    val showLocal: Boolean = false,
)

@Immutable
data class ArtistActions(
    val onPlay: () -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onRadio: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onMenu: (Any) -> Unit = {},
    val onBlock: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onShowLocal: (Boolean) -> Unit = {},
)

@Immutable
data class ArtistStatItemUiModel(
    val icon: ImageVector,
    val value: String,
    val contentDescription: String,
)
