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
