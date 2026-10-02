/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.ListItem
import moe.rukamori.archivetune.ui.component.PlaylistListItem

@Composable
fun AddToPlaylistDialogOnline(
    isVisible: Boolean,
    allowSyncing: Boolean = true,
    initialTextFieldValue: String? = null,
    songs: SnapshotStateList<Song>,
    onDismiss: () -> Unit,
    onProgressStart: (Boolean) -> Unit,
    onPercentageChange: (Int) -> Unit,
    onStatusChange: (String) -> Unit = {},
) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    var allPlaylists by remember { mutableStateOf(emptyList<Playlist>()) }
    val playlists = remember(allPlaylists) { playlistsForAddToPlaylist(allPlaylists).asReversed() }

    var showCreatePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedPlaylist by remember {
        mutableStateOf<Playlist?>(null)
    }
    val songIds by remember {
        mutableStateOf<List<String>?>(null)
    }

    var showResultDialog by remember { mutableStateOf(false) }
    var processingSummary by remember { mutableStateOf<ProcessingSummary?>(null) }

    LaunchedEffect(Unit) {
        database.playlistsByCreateDateAsc().collect {
            allPlaylists = it
        }
    }

    fun processSongs(
        targetPlaylist: Playlist?,
        addToLiked: Boolean,
    ) {
        coroutineScope.launchOnlineImport(
            songsSnapshot = songs.toList(),
            targetPlaylist = targetPlaylist,
            addToLiked = addToLiked,
            database = database,
            syncUtils = syncUtils,
            onProgressStart = onProgressStart,
            onPercentageChange = onPercentageChange,
            onStatusChange = onStatusChange,
            onDismiss = onDismiss,
            onResult = {
                processingSummary = it
                showResultDialog = true
            },
        )
    }

    if (isVisible) {
        ListDialog(
            onDismiss = onDismiss,
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.create_playlist),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(id = R.drawable.playlist_add),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            showCreatePlaylistDialog = true
                        },
                )
            }

            items(playlists) { playlist ->
                PlaylistListItem(
                    playlist = playlist,
                    modifier =
                        Modifier.clickable {
                            selectedPlaylist = playlist
                            processSongs(targetPlaylist = playlist, addToLiked = false)
                        },
                )
            }

            item {
                ListItem(
                    modifier =
                        Modifier.clickable {
                            processSongs(targetPlaylist = null, addToLiked = true)
                        },
                    title = stringResource(R.string.liked_songs),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(id = R.drawable.favorite),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        )
                    },
                    trailingContent = {},
                )
            }

            item {
                Text(
                    text = stringResource(R.string.playlist_add_local_to_synced_note),
                    fontSize = TextUnit(12F, TextUnitType.Sp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing,
        )
    }

    if (showResultDialog && processingSummary != null) {
        val summary = processingSummary!!
        AddToPlaylistOnlineResultDialog(
            summary = summary,
            onDismissResult = { showResultDialog = false },
        )
    }
}
