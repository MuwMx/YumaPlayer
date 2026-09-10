/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.utils.LikeSourceResolver
import java.time.LocalDateTime

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SelectionSongMenu(
    songSelection: List<Song>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
    songPosition: List<PlaylistSongMap>? = emptyList(),
    isFromCache: Boolean = false,
    onRemoveFromCache: ((List<Song>) -> Unit)? = null,
    likeSourceHint: moe.rukamori.archivetune.constants.LikeSource? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val syncUtils = LocalSyncUtils.current

    val allInLibrary by remember {
        mutableStateOf(
            songSelection.all {
                it.song.inLibrary != null
            },
        )
    }

    val allLiked by remember(songSelection, likeSourceHint) {
        mutableStateOf(
            songSelection.isNotEmpty() &&
                songSelection.all {
                    when (likeSourceHint) {
                        moe.rukamori.archivetune.constants.LikeSource.YTM -> it.song.likedYtm
                        moe.rukamori.archivetune.constants.LikeSource.SPOTIFY -> it.song.likedSpotify
                        null -> it.song.liked
                    }
                },
        )
    }

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    LaunchedEffect(songSelection) {
        if (songSelection.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songSelection.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songSelection.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<Song>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            songSelection.map { it.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    songCount == 1 && playlistNames.size == 1 -> {
                        context.getString(R.string.added_to_playlist, playlistNames.first())
                    }

                    songCount > 1 && playlistNames.size == 1 -> {
                        context.getString(
                            R.string.added_n_songs_to_playlist,
                            songCount,
                            playlistNames.first(),
                        )
                    }

                    songCount == 1 -> {
                        context.getString(R.string.added_to_n_playlists, playlistNames.size)
                    }

                    else -> {
                        context.getString(R.string.added_n_songs_to_n_playlists, songCount, playlistNames.size)
                    }
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, "selection"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songSelection.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false,
                            )
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        userScrollEnabled = true,
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            MenuSurfaceSection {
                NewActionGrid(
                    actions =
                        listOf(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.play),
                                onClick = {
                                    onDismiss()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Selection",
                                            items = songSelection.map { it.toMediaItem() },
                                        ),
                                    )
                                    clearAction()
                                },
                            ),
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.shuffle),
                                onClick = {
                                    onDismiss()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Selection",
                                            items = songSelection.shuffled().map { it.toMediaItem() },
                                        ),
                                    )
                                    clearAction()
                                },
                            ),
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.playlist_add),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.add_to_playlist),
                                onClick = {
                                    showChoosePlaylistDialog = true
                                },
                            ),
                        ),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            val actionCount = 3
            MenuSurfaceSection {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onDismiss()
                        playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                        clearAction()
                    },
                    index = 0,
                    count = actionCount,
                )

                NewMenuItem(
                    headlineContent = {
                        Text(
                            text =
                                stringResource(
                                    if (allInLibrary) R.string.remove_from_library else R.string.add_to_library,
                                ),
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter =
                                painterResource(
                                    if (allInLibrary) R.drawable.library_add_check else R.drawable.library_add,
                                ),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val shouldAdd = !allInLibrary
                            val now = LocalDateTime.now()
                            val failed = LinkedHashSet<String>()
                            val updatedSongs = ArrayList<moe.rukamori.archivetune.db.entities.SongEntity>()
                            for (song in songSelection.asSequence().map { it.song }.distinctBy { it.id }) {
                                val remoteResult = YouTube.likeVideo(song.id, shouldAdd)
                                if (remoteResult.isFailure) {
                                    failed += song.id
                                    continue
                                }
                                updatedSongs +=
                                    song.copy(
                                        liked = shouldAdd,
                                        likedDate = if (shouldAdd) now else null,
                                        inLibrary = if (shouldAdd) now else null,
                                    )
                            }

                            if (updatedSongs.isNotEmpty()) {
                                database.withTransaction {
                                    updatedSongs.forEach(::update)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                onDismiss()
                                clearAction()
                                if (failed.isNotEmpty()) {
                                    Toast
                                        .makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                    },
                    index = 1,
                    count = actionCount,
                )

                NewMenuItem(
                    headlineContent = {
                        Text(
                            text =
                                stringResource(
                                    if (allLiked) R.string.dislike_all else R.string.like_all,
                                ),
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter =
                                painterResource(
                                    if (allLiked) R.drawable.favorite else R.drawable.favorite_border,
                                ),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onDismiss()
                        val shouldUnlikeAll = songSelection.all {
                            when (likeSourceHint) {
                                moe.rukamori.archivetune.constants.LikeSource.YTM -> it.song.likedYtm
                                moe.rukamori.archivetune.constants.LikeSource.SPOTIFY -> it.song.likedSpotify
                                null -> it.song.liked
                            }
                        }
                        val updatedSongs =
                            songSelection
                                .asSequence()
                                .map { it.song }
                                .distinctBy { it.id }
                                .filter { song ->
                                    val isLikedForHint = when (likeSourceHint) {
                                        moe.rukamori.archivetune.constants.LikeSource.YTM -> song.likedYtm
                                        moe.rukamori.archivetune.constants.LikeSource.SPOTIFY -> song.likedSpotify
                                        null -> song.liked
                                    }
                                    shouldUnlikeAll || !isLikedForHint
                                }
                                .map { song ->
                                    val src = likeSourceHint ?: LikeSourceResolver.resolve(mediaId = song.id, isLocal = song.isLocal)
                                    song.localToggleLike(src)
                                }
                                .toList()

                        if (updatedSongs.isEmpty()) return@NewMenuItem

                        coroutineScope.launch(Dispatchers.IO) {
                            database.withTransaction {
                                updatedSongs.forEach(::update)
                            }
                            syncUtils.likeSongs(updatedSongs, likeSourceHint)
                        }
                    },
                    index = 2,
                    count = actionCount,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection {
                when (downloadState) {
                    Download.STATE_COMPLETED -> {
                        NewMenuItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.remove_download),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.offline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showRemoveDownloadDialog = true
                            },
                            index = 0,
                            count = 1,
                        )
                    }

                    Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                        NewMenuItem(
                            headlineContent = { Text(text = stringResource(R.string.downloading)) },
                            leadingContent = {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            onClick = {
                                showRemoveDownloadDialog = true
                            },
                            index = 0,
                            count = 1,
                        )
                    }

                    else -> {
                        NewMenuItem(
                            headlineContent = { Text(text = stringResource(R.string.action_download)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                sendAddMissingDownloads(
                                    context = context,
                                    songs =
                                        songSelection.map { song ->
                                            HeaderDownloadItem(
                                                id = song.id,
                                                title = song.song.title,
                                            )
                                        },
                                    downloads = downloadUtil.downloads.value,
                                )
                            },
                            index = 0,
                            count = 1,
                        )
                    }
                }
            }
        }

        if (songPosition?.size != 0) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                MenuSurfaceSection {
                    NewMenuItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val positions = songPosition.orEmpty()
                                if (positions.isEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        onDismiss()
                                        clearAction()
                                    }
                                    return@launch
                                }

                                val browseIdByPlaylistId = HashMap<String, String?>()
                                for (playlistId in positions.asSequence().map { it.playlistId }.distinct()) {
                                    browseIdByPlaylistId[playlistId] = database.getPlaylistById(playlistId)?.playlist?.browseId
                                }

                                val failed = LinkedHashSet<PlaylistSongMap>()
                                val succeeded = ArrayList<PlaylistSongMap>(positions.size)

                                for (cur in positions) {
                                    val browseId = browseIdByPlaylistId[cur.playlistId]
                                    if (browseId != null) {
                                        val remoteResult = removeSongFromRemotePlaylist(browseId, cur)
                                        if (remoteResult.isFailure) {
                                            failed += cur
                                        } else {
                                            succeeded += cur
                                        }
                                    } else {
                                        succeeded += cur
                                    }
                                }

                                if (succeeded.isNotEmpty()) {
                                    database.withTransaction {
                                        val offsetByPlaylistId = HashMap<String, Int>()
                                        succeeded
                                            .sortedWith(compareBy<PlaylistSongMap> { it.playlistId }.thenBy { it.position })
                                            .forEach { cur ->
                                                val offset = offsetByPlaylistId.getOrPut(cur.playlistId) { 0 }
                                                move(cur.playlistId, cur.position - offset, Int.MAX_VALUE)
                                                delete(cur.copy(position = Int.MAX_VALUE))
                                                offsetByPlaylistId[cur.playlistId] = offset + 1
                                            }
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    onDismiss()
                                    clearAction()
                                    if (failed.isNotEmpty()) {
                                        Toast
                                            .makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                }
                            }
                        },
                        index = 0,
                        count = 1,
                    )
                }
            }
        }

        if (isFromCache && onRemoveFromCache != null) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                MenuSurfaceSection {
                    NewMenuItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.remove_from_cache),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            onDismiss()
                            onRemoveFromCache(songSelection)
                            clearAction()
                        },
                        index = 0,
                        count = 1,
                    )
                }
            }
        }
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SelectionMediaMetadataMenu(
    songSelection: List<MediaMetadata>,
    currentItems: List<Timeline.Window>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
    onRemoveFromQueue: ((List<Timeline.Window>) -> Unit)? = null,
    onRemoveFromHistory: (() -> Unit)? = null,
    likeSourceHint: moe.rukamori.archivetune.constants.LikeSource? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val syncUtils = LocalSyncUtils.current

    val allLiked by remember(songSelection) {
        mutableStateOf(songSelection.isNotEmpty() && songSelection.all { it.liked })
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<Song>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            songSelection.map {
                database.insert(it)
                it.id
            }
        },
        onDismiss = { showChoosePlaylistDialog = false },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    songCount == 1 && playlistNames.size == 1 -> {
                        context.getString(R.string.added_to_playlist, playlistNames.first())
                    }

                    songCount > 1 && playlistNames.size == 1 -> {
                        context.getString(
                            R.string.added_n_songs_to_playlist,
                            songCount,
                            playlistNames.first(),
                        )
                    }

                    songCount == 1 -> {
                        context.getString(R.string.added_to_n_playlists, playlistNames.size)
                    }

                    else -> {
                        context.getString(R.string.added_n_songs_to_n_playlists, songCount, playlistNames.size)
                    }
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    LaunchedEffect(songSelection) {
        if (songSelection.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songSelection.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songSelection.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, "selection"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songSelection.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false,
                            )
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        userScrollEnabled = true,
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            MenuSurfaceSection {
                NewActionGrid(
                    actions =
                        listOf(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.play),
                                onClick = {
                                    onDismiss()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Selection",
                                            items = songSelection.map { it.toMediaItem() },
                                        ),
                                    )
                                    clearAction()
                                },
                            ),
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.shuffle),
                                onClick = {
                                    onDismiss()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Selection",
                                            items = songSelection.shuffled().map { it.toMediaItem() },
                                        ),
                                    )
                                    clearAction()
                                },
                            ),
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.playlist_add),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.add_to_playlist),
                                onClick = {
                                    showChoosePlaylistDialog = true
                                },
                            ),
                        ),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            val actionCount =
                (if (onRemoveFromHistory != null) 1 else 0) +
                    (if (currentItems.isNotEmpty()) 1 else 0) +
                    1 +
                    1
            val removeHistoryOffset = if (onRemoveFromHistory != null) 1 else 0
            val removeQueueOffset = removeHistoryOffset + (if (currentItems.isNotEmpty()) 1 else 0)

            MenuSurfaceSection {
                if (onRemoveFromHistory != null) {
                    NewMenuItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.remove_from_history),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            onDismiss()
                            onRemoveFromHistory()
                            clearAction()
                        },
                        index = 0,
                        count = actionCount,
                    )
                }

                if (currentItems.isNotEmpty()) {
                    NewMenuItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.remove_from_queue),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            onDismiss()
                            if (onRemoveFromQueue != null) {
                                onRemoveFromQueue(currentItems)
                            } else {
                                var i = 0
                                currentItems.forEach { cur ->
                                    if (playerConnection.player.availableCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                                        playerConnection.player.removeMediaItem(cur.firstPeriodIndex - i++)
                                    }
                                }
                            }
                            clearAction()
                        },
                        index = removeHistoryOffset,
                        count = actionCount,
                    )
                }

                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onDismiss()
                        playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                        clearAction()
                    },
                    index = removeQueueOffset,
                    count = actionCount,
                )

                NewMenuItem(
                    headlineContent = {
                        Text(
                            text =
                                stringResource(
                                    if (allLiked) R.string.dislike_all else R.string.like_all,
                                ),
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter =
                                painterResource(
                                    if (allLiked) R.drawable.favorite else R.drawable.favorite_border,
                                ),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onDismiss()
                        val updatedSongs =
                            songSelection
                                .asSequence()
                                .distinctBy { it.id }
                                .map { it.toSongEntity() }
                                .filter { song ->
                                    val isLikedForHint = when (likeSourceHint) {
                                        moe.rukamori.archivetune.constants.LikeSource.YTM -> song.likedYtm
                                        moe.rukamori.archivetune.constants.LikeSource.SPOTIFY -> song.likedSpotify
                                        null -> song.liked
                                    }
                                    allLiked || !isLikedForHint
                                }
                                .map { entity ->
                                    val src = likeSourceHint ?: LikeSourceResolver.resolve(mediaId = entity.id, isLocal = entity.isLocal)
                                    entity.localToggleLike(src)
                                }
                                .toList()

                        if (updatedSongs.isEmpty()) return@NewMenuItem

                        coroutineScope.launch(Dispatchers.IO) {
                            database.withTransaction {
                                updatedSongs.forEach(::update)
                            }
                            syncUtils.likeSongs(updatedSongs, likeSourceHint)
                        }
                    },
                    index = removeQueueOffset + 1,
                    count = actionCount,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection {
                when (downloadState) {
                    Download.STATE_COMPLETED -> {
                        NewMenuItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.remove_download),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.offline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showRemoveDownloadDialog = true
                            },
                            index = 0,
                            count = 1,
                        )
                    }

                    Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                        NewMenuItem(
                            headlineContent = { Text(text = stringResource(R.string.downloading)) },
                            leadingContent = {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            onClick = {
                                showRemoveDownloadDialog = true
                            },
                            index = 0,
                            count = 1,
                        )
                    }

                    else -> {
                        NewMenuItem(
                            headlineContent = { Text(text = stringResource(R.string.action_download)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                sendAddMissingDownloads(
                                    context = context,
                                    songs =
                                        songSelection.map { song ->
                                            HeaderDownloadItem(
                                                id = song.id,
                                                title = song.title,
                                            )
                                        },
                                    downloads = downloadUtil.downloads.value,
                                )
                            },
                            index = 0,
                            count = 1,
                        )
                    }
                }
            }
        }
    }
}
