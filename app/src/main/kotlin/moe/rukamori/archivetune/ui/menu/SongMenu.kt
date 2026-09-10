/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.download.FlacDownloader
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.shareLocalAudio
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import moe.rukamori.archivetune.viewmodels.CachePlaylistViewModel

@Composable
fun SongMenu(
    originalSong: Song,
    event: Event? = null,
    navController: NavController,
    playlistSong: PlaylistSong? = null,
    playlistBrowseId: String? = null,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong
    val download by LocalDownloadUtil.current
        .getDownload(originalSong.id)
        .collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val cacheViewModel = hiltViewModel<CachePlaylistViewModel>()

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    // Artist separators for splitting artist names
    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (playbackSource) = rememberEnumPreference(PlaybackSourceKey, defaultValue = PlaybackSource.YT_MUSIC)
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(song.id) { SpeedDialPin(type = SpeedDialPinType.SONG, id = song.id) }
    val isInSpeedDial =
        remember(speedDialPins, songPin) {
            speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
        }

    val orderedArtists by produceState(initialValue = emptyList<ArtistEntity>(), song) {
        withContext(Dispatchers.IO) {
            val artistMaps = database.songArtistMap(song.id).sortedBy { it.position }
            val sorted =
                artistMaps.mapNotNull { map ->
                    song.artists.firstOrNull { it.id == map.artistId }
                }
            value = sorted
        }
    }

    // Split artists by configured separators
    data class SplitArtist(
        val name: String,
        val originalArtist: ArtistEntity?,
    )

    val splitArtists =
        remember(orderedArtists, artistSeparators) {
            if (artistSeparators.isEmpty()) {
                orderedArtists.map { SplitArtist(it.name, it) }
            } else {
                val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
                orderedArtists.flatMap { artist ->
                    val parts =
                        artist.name
                            .split(separatorRegex)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    if (parts.size > 1) {
                        // If the name contains separators, create split artists
                        // The first part keeps the original artist reference for navigation
                        parts.mapIndexed { index, name ->
                            SplitArtist(name, if (index == 0) artist else null)
                        }
                    } else {
                        listOf(SplitArtist(artist.name, artist))
                    }
                }
            }
        }

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val TextFieldValueSaver: Saver<TextFieldValue, *> =
        Saver(
            save = { it.text },
            restore = { text -> TextFieldValue(text, TextRange(text.length)) },
        )

    var titleField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.song.title))
    }

    var artistField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(
            TextFieldValue(
                song.artists.mapNotNull { it.name.takeIf(String::isNotBlank) }.joinToString(", "),
            ),
        )
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = null,
                )
            },
            title = {
                Text(text = stringResource(R.string.edit_song))
            },
            textFields =
                listOf(
                    stringResource(R.string.song_title) to titleField,
                    stringResource(R.string.artist_name) to artistField,
                ),
            onTextFieldsChange = { index, newValue ->
                if (index == 0) {
                    titleField = newValue
                } else {
                    artistField = newValue
                }
            },
            onDoneMultiple = { values ->
                val newTitle = values[0]
                val newArtist = values[1]

                coroutineScope.launch {
                    database.query {
                        update(song.song.copy(title = newTitle))
                        val artist = song.artists.firstOrNull()
                        if (artist != null) {
                            update(artist.copy(name = newArtist))
                        }
                    }

                    showEditDialog = false
                    onDismiss()
                }
            },
            onDismiss = { showEditDialog = false },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            listOf(song.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                    else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    if (showErrorPlaylistAddDialog) {
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier = Modifier.clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(listOf(song)) { song ->
                SongListItem(song = song)
            }
        }
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = splitArtists.distinctBy { it.name },
                key = { it.name },
            ) { splitArtist ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = splitArtist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        AsyncImage(
                            model = splitArtist.originalArtist?.thumbnailUrl?.resize(200, 200),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                splitArtist.originalArtist?.let { artist ->
                                    navController.navigate("artist/${artist.id}")
                                    showSelectArtistDialog = false
                                    onDismiss()
                                }
                            },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    val isSpotifyOrigin =
        remember(song.song.id, playlistBrowseId, song.song.likedSpotify, song.song.likedYtm, song.song.liked) {
            song.song.id.startsWith("spotify:") ||
                playlistBrowseId?.startsWith("spotify:") == true ||
                (!song.song.isLocal && song.song.id.length == 22 && song.song.id.all { it.isLetterOrDigit() }) ||
                (song.song.likedSpotify && !song.song.likedYtm && !song.song.liked)
        }
    val likeSource = if (isSpotifyOrigin) LikeSource.SPOTIFY else LikeSource.YTM
    val isLiked = if (isSpotifyOrigin) song.song.likedSpotify else (song.song.likedYtm || song.song.liked)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SongListItem(
            song = song,
            badges = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingContent = {
                IconButton(
                    onClick = {
                        val s = song.song.toggleLike(likeSource)
                        database.query {
                            update(s)
                        }
                        syncUtils.likeSong(s, if (isSpotifyOrigin) s.id else null)
                    },
                ) {
                    Icon(
                        painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null,
                    )
                }
            },
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val isLocalSong = song.song.isLocal

    val startRadioText = stringResource(R.string.start_radio)
    val playNextText = stringResource(R.string.play_next)
    val addToQueueText = stringResource(R.string.add_to_queue)
    val addToPlaylistText = stringResource(R.string.add_to_playlist)
    val shareText = stringResource(R.string.share)
    val editText = stringResource(R.string.edit)

    val primaryActions =
        remember(
            song,
            startRadioText,
            playNextText,
            addToQueueText,
            addToPlaylistText,
            shareText,
            editText,
            isLocalSong,
            onDismiss,
            playerConnection,
        ) {
            buildList {
                if (!isLocalSong) {
                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.radio),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = startRadioText,
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                            },
                        ),
                    )
                }
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_play),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = playNextText,
                        onClick = {
                            onDismiss()
                            playerConnection.playNext(song.toMediaItem())
                        },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = addToQueueText,
                        onClick = {
                            onDismiss()
                            playerConnection.addToQueue(song.toMediaItem())
                        },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = addToPlaylistText,
                        onClick = { showChoosePlaylistDialog = true },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_share),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = shareText,
                        onClick = {
                            onDismiss()
                            if (isLocalSong) {
                                shareLocalAudio(context, song.id, song.format?.mimeType)
                            } else {
                                val intent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
                                    }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = editText,
                        onClick = { showEditDialog = true },
                    ),
                )
            }
        }

    val showMutationSection = event != null || playlistSong != null || isFromCache || !isLocalSong

    LazyColumn(
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
                    actions = primaryActions,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            val sectionCount = if (!isLocalSong) 2 else 1
            MenuSurfaceSection {
                if (!isLocalSong) {
                    NewMenuItem(
                        headlineContent = {
                            Text(
                                text =
                                    stringResource(
                                        if (song.song.inLibrary == null) {
                                            R.string.add_to_library
                                        } else {
                                            R.string.remove_from_library
                                        },
                                    ),
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter =
                                    painterResource(
                                        if (song.song.inLibrary == null) {
                                            R.drawable.library_add
                                        } else {
                                            R.drawable.library_add_check
                                        },
                                    ),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            database.query {
                                update(song.song.toggleLibrary())
                            }
                        },
                        index = 0,
                        count = sectionCount,
                    )
                }

                NewMenuItem(
                    headlineContent = {
                        Text(
                            text =
                                stringResource(
                                    if (isInSpeedDial) {
                                        R.string.remove_from_speed_dial
                                    } else {
                                        R.string.pin_to_speed_dial
                                    },
                                ),
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                        onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                        onDismiss()
                    },
                    index = if (!isLocalSong) 1 else 0,
                    count = sectionCount,
                )
            }
        }

        if (showMutationSection) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                val mutationItemCount =
                    (if (event != null) 1 else 0) +
                        (if (playlistSong != null) 1 else 0) +
                        (if (isFromCache) 1 else 0) +
                        (if (!isLocalSong) {
                            1 + (if (playbackSource == PlaybackSource.FLAC) 1 else 0) + (if (externalDownloaderEnabled) 1 else 0)
                        } else 0)

                val eventIndex = 0
                val playlistSongIndex = if (event != null) 1 else 0
                val cacheIndex = (if (event != null) 1 else 0) + (if (playlistSong != null) 1 else 0)
                val downloadIndex = cacheIndex + (if (isFromCache) 1 else 0)
                val flacIndex = downloadIndex + 1
                val externalDownloaderIndex = flacIndex + (if (playbackSource == PlaybackSource.FLAC) 1 else 0)

                MenuSurfaceSection {
                    if (event != null) {
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
                                    tint = MaterialTheme.colorScheme.error,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                database.query {
                                    delete(event)
                                }
                            },
                            index = eventIndex,
                            count = mutationItemCount,
                        )
                    }

                    if (playlistSong != null) {
                        NewMenuItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.remove_from_playlist),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                val map = playlistSong.map
                                coroutineScope.launch(Dispatchers.IO) {
                                    val browseId = playlistBrowseId
                                    if (browseId != null) {
                                        val remoteResult = removeSongFromRemotePlaylist(browseId, map)
                                        if (remoteResult.isFailure) {
                                            withContext(Dispatchers.Main) {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        context.getString(R.string.error_unknown),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                onDismiss()
                                            }
                                            return@launch
                                        }
                                    }
                                    database.withTransaction {
                                        val maxPosition = maxPlaylistSongPosition(map.playlistId) ?: map.position
                                        if (map.position < maxPosition) {
                                            move(map.playlistId, map.position, maxPosition)
                                        }
                                        delete(map)
                                    }
                                    withContext(Dispatchers.Main) {
                                        onDismiss()
                                    }
                                }
                            },
                            index = playlistSongIndex,
                            count = mutationItemCount,
                        )
                    }

                    if (isFromCache) {
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
                                    tint = MaterialTheme.colorScheme.error,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                cacheViewModel.removeSongFromCache(song.id)
                            },
                            index = cacheIndex,
                            count = mutationItemCount,
                        )
                    }

                    if (!isLocalSong) {
                        when (download?.state) {
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
                                            tint = MaterialTheme.colorScheme.error,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        DownloadService.sendRemoveDownload(
                                            context,
                                            ExoDownloadService::class.java,
                                            song.id,
                                            false,
                                        )
                                    },
                                    index = downloadIndex,
                                    count = mutationItemCount,
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
                                        DownloadService.sendRemoveDownload(
                                            context,
                                            ExoDownloadService::class.java,
                                            song.id,
                                            false,
                                        )
                                    },
                                    index = downloadIndex,
                                    count = mutationItemCount,
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
                                        val downloadRequest =
                                            DownloadRequest
                                                .Builder(song.id, song.id.toUri())
                                                .setCustomCacheKey(song.id)
                                                .setData(song.song.title.toByteArray())
                                                .build()
                                        DownloadService.sendAddDownload(
                                            context,
                                            ExoDownloadService::class.java,
                                            downloadRequest,
                                            false,
                                        )
                                    },
                                    index = downloadIndex,
                                    count = mutationItemCount,
                                )
                            }
                        }

                        if (playbackSource == PlaybackSource.FLAC) {
                            val flacWorkInfos by WorkManager.getInstance(context)
                                .getWorkInfosForUniqueWorkFlow("flac_download_${song.id}")
                                .collectAsState(emptyList())
                            val flacWorkState = flacWorkInfos.firstOrNull()?.state

                            when (flacWorkState) {
                                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                                    NewMenuItem(
                                        headlineContent = { Text(text = stringResource(R.string.downloading)) },
                                        leadingContent = {
                                            CircularWavyProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                            )
                                        },
                                        onClick = {
                                            WorkManager.getInstance(context).cancelUniqueWork("flac_download_${song.id}")
                                        },
                                        index = flacIndex,
                                        count = mutationItemCount,
                                    )
                                }
                                WorkInfo.State.SUCCEEDED -> {
                                    NewMenuItem(
                                        headlineContent = { Text(text = stringResource(R.string.remove_download)) },
                                        leadingContent = {
                                            Icon(
                                                painter = painterResource(R.drawable.offline),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            FlacDownloader.deleteFlac(
                                                context,
                                                song.id,
                                                song.song.title,
                                                song.artists.mapNotNull { it.name.takeIf(String::isNotBlank) }.joinToString(", "),
                                                song.song.albumName.orEmpty(),
                                            )
                                        },
                                        index = flacIndex,
                                        count = mutationItemCount,
                                    )
                                }
                                else -> {
                                    NewMenuItem(
                                        headlineContent = { Text(text = stringResource(R.string.download_flac)) },
                                        leadingContent = {
                                            Icon(
                                                painter = painterResource(R.drawable.download),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            FlacDownloader.downloadFlac(
                                                context,
                                                song.id,
                                                song.song.title,
                                                song.artists.mapNotNull { it.name.takeIf(String::isNotBlank) }.joinToString(", "),
                                                song.song.albumName.orEmpty(),
                                            )
                                        },
                                        index = flacIndex,
                                        count = mutationItemCount,
                                    )
                                }
                            }
                        }

                        if (externalDownloaderEnabled) {
                            NewMenuItem(
                                headlineContent = { Text(text = stringResource(R.string.open_with_downloader)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    val url = "https://music.youtube.com/watch?v=${song.id}"
                                    if (externalDownloaderPackage.isBlank()) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.external_downloader_not_configured),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        return@NewMenuItem
                                    }
                                    val intent =
                                        android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setPackage(externalDownloaderPackage)
                                            data = android.net.Uri.parse(url)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: android.content.ActivityNotFoundException) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.external_downloader_not_installed),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                                index = externalDownloaderIndex,
                                count = mutationItemCount,
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            val navItemCount = 1 + (if (song.song.albumId != null) 1 else 0)
            MenuSurfaceSection {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.artist),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                            navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                            onDismiss()
                        } else {
                            showSelectArtistDialog = true
                        }
                    },
                    index = 0,
                    count = navItemCount,
                )

                if (song.song.albumId != null) {
                    NewMenuItem(
                        headlineContent = { Text(text = stringResource(R.string.view_album)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.album),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            navController.navigate("album/${song.song.albumId}")
                        },
                        index = 1,
                        count = navItemCount,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            val infoItemCount = (if (!isLocalSong) 1 else 0) + 1
            MenuSurfaceSection {
                if (!isLocalSong) {
                    NewMenuItem(
                        headlineContent = { Text(text = stringResource(R.string.refetch)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation),
                            )
                        },
                        onClick = {
                            refetchIconDegree -= 360
                            coroutineScope.launch(Dispatchers.IO) {
                                YouTube.queue(listOf(song.id)).onSuccess {
                                    val newSong = it.firstOrNull()
                                    if (newSong != null) {
                                        database.transaction {
                                            update(song, newSong.toMediaMetadata())
                                        }
                                    }
                                }
                            }
                        },
                        index = 0,
                        count = infoItemCount,
                    )
                }

                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.details)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_about),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onDismiss()
                        bottomSheetPageState.show {
                            ShowMediaInfo(song.id)
                        }
                    },
                    index = if (!isLocalSong) 1 else 0,
                    count = infoItemCount,
                )
            }
        }
    }
}
