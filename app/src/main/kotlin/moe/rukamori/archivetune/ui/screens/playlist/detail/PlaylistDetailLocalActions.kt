/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.playlist.source.RoomPlaylistSource
import moe.rukamori.archivetune.playlist.source.deletePlaylist
import moe.rukamori.archivetune.playlist.source.removeDownloads
import moe.rukamori.archivetune.playlist.source.rename
import moe.rukamori.archivetune.playlist.source.toggleLike
import moe.rukamori.archivetune.playlist.source.updateCover
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads
import moe.rukamori.archivetune.utils.reportException

/**
 * LOCAL PLAYLIST SLOT — the local-only mutations on [PlaylistDetailViewModel],
 * kept here so the ViewModel class stays under the 250-line rule. Each routes
 * through RoomPlaylistMutations (the verbatim legacy wiring ports); every
 * other source ignores them (null roomSource()).
 */
internal fun PlaylistDetailViewModel.roomSource(): RoomPlaylistSource? =
    source as? RoomPlaylistSource

internal fun PlaylistDetailViewModel.renamePlaylist(name: String) {
    val room = roomSource() ?: return
    viewModelScope.launch(Dispatchers.IO) {
        room.rename(name).onFailure { reportException(it) }
    }
}

internal fun PlaylistDetailViewModel.deletePlaylist(onDeleted: () -> Unit) {
    val room = roomSource() ?: return
    viewModelScope.launch(Dispatchers.IO) {
        room.deletePlaylist(onDeleted).onFailure { reportException(it) }
    }
}

internal fun PlaylistDetailViewModel.updateCover(
    context: Context,
    uri: android.net.Uri?,
) {
    val room = roomSource() ?: return
    viewModelScope.launch(Dispatchers.IO) {
        room.updateCover(context, uri).onFailure { reportException(it) }
    }
}

internal fun PlaylistDetailViewModel.togglePlaylistLike() {
    val room = roomSource() ?: return
    viewModelScope.launch(Dispatchers.IO) {
        room.toggleLike().onFailure { reportException(it) }
    }
}

internal fun PlaylistDetailViewModel.removeDownloads(
    context: Context,
    editable: Boolean,
) {
    val room = roomSource() ?: return
    viewModelScope.launch(Dispatchers.IO) {
        room.removeDownloads(editable) { songIds ->
            sendRemoveDownloads(context, songIds)
        }.onFailure { reportException(it) }
    }
}
