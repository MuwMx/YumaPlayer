/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    isVisible: Boolean,
    allowSyncing: Boolean = true,
    initialTextFieldValue: String? = null,
    onGetSong: suspend () -> List<String>,
    onDismiss: () -> Unit,
    onAddComplete: ((songCount: Int, playlistNames: List<String>) -> Unit)? = null,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val allPlaylists by database.playlistsByCreateDateAsc().collectAsStateWithLifecycle(initialValue = emptyList())
    val playlistPlayCounts by database.playlistPlayCounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { hasYouTubeLoginCookie(innerTubeCookie) }
    var sortOption by rememberSaveable { mutableStateOf(AddToPlaylistSortOption.RECENTLY_CREATED) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearchField by rememberSaveable { mutableStateOf(false) }
    val availablePlaylists = remember(allPlaylists) { playlistsForAddToPlaylist(allPlaylists) }
    val playlists =
        remember(availablePlaylists, sortOption, searchQuery, playlistPlayCounts) {
            visiblePlaylistsForAddToPlaylist(
                playlists = availablePlaylists,
                sortOption = sortOption,
                query = searchQuery,
                playlistPlayCounts = playlistPlayCounts.associate { it.playlistId to it.playCount },
            )
        }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var playlistsWithDuplicates by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var duplicateSongsMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var songIds by remember { mutableStateOf<List<String>?>(null) }
    var selectedPlaylistIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isAddingToPlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            songIds = null
            selectedPlaylistIds = emptySet()
            isAddingToPlaylist = false
            showDuplicateDialog = false
            playlistsWithDuplicates = emptyList()
            duplicateSongsMap = emptyMap()
            searchQuery = ""
            showSearchField = false
            sortOption = AddToPlaylistSortOption.RECENTLY_CREATED
        }
    }

    LaunchedEffect(availablePlaylists) {
        if (selectedPlaylistIds.isEmpty()) return@LaunchedEffect

        val availableIds = availablePlaylists.mapTo(linkedSetOf()) { it.id }
        val nextSelection = selectedPlaylistIds.filterTo(linkedSetOf()) { it in availableIds }
        if (nextSelection != selectedPlaylistIds) {
            selectedPlaylistIds = nextSelection
        }
    }

    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .imePadding()
                        .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .heightIn(max = maxHeight),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AddToPlaylistHeader(
                            selectedCount = selectedPlaylistIds.size,
                            showSearchField = showSearchField,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onToggleSearch = {
                                if (showSearchField) {
                                    showSearchField = false
                                    searchQuery = ""
                                } else {
                                    showSearchField = true
                                }
                            },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        AddToPlaylistContent(
                            playlists = playlists,
                            selectedPlaylistIds = selectedPlaylistIds,
                            sortOption = sortOption,
                            searchQuery = searchQuery,
                            isAddingToPlaylist = isAddingToPlaylist,
                            modifier = Modifier.weight(1f, fill = false),
                            onSortOptionChange = { sortOption = it },
                            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                            onTogglePlaylist = { playlistId ->
                                selectedPlaylistIds =
                                    if (selectedPlaylistIds.contains(playlistId)) {
                                        selectedPlaylistIds - playlistId
                                    } else {
                                        selectedPlaylistIds + playlistId
                                    }
                            },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        AddToPlaylistButtons(
                            selectedPlaylistCount = selectedPlaylistIds.size,
                            isAddingToPlaylist = isAddingToPlaylist,
                            onDismiss = onDismiss,
                            onConfirm = {
                                coroutineScope.launchAddToPlaylists(
                                    database = database,
                                    availablePlaylists = availablePlaylists,
                                    selectedPlaylistIdsSnapshot = selectedPlaylistIds,
                                    cachedSongIds = songIds,
                                    isLoggedIn = isLoggedIn,
                                    onGetSong = onGetSong,
                                    onStart = { isAddingToPlaylist = true },
                                    onEnd = { result ->
                                        isAddingToPlaylist = false
                                        if (result == null) {
                                            onDismiss()
                                        } else {
                                            songIds = result.songIds
                                            if (result.addedPlaylistNames.isNotEmpty()) {
                                                onAddComplete?.invoke(result.songIds.size, result.addedPlaylistNames)
                                            }
                                            if (result.withDuplicates.isNotEmpty()) {
                                                playlistsWithDuplicates = result.withDuplicates
                                                duplicateSongsMap = result.duplicatesMap
                                                showDuplicateDialog = true
                                            }
                                            onDismiss()
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
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

    AddToPlaylistDuplicateDialog(
        isVisible = showDuplicateDialog,
        duplicateSongsMap = duplicateSongsMap,
        playlistsWithDuplicates = playlistsWithDuplicates,
        songIds = songIds,
        coroutineScope = coroutineScope,
        onAddSafely = { playlist, ids -> addSongsToPlaylistSafely(playlist, ids, isLoggedIn, database) },
        onAddComplete = onAddComplete,
        onDismiss = { showDuplicateDialog = false },
        onDismissAll = onDismiss,
    )
}
