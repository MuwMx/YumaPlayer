/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads

fun SongItem.toPlaylistPlaybackEndpoint(
    playlistId: String,
    playlistPlayParams: String?,
): WatchEndpoint {
    val baseEndpoint = endpoint ?: WatchEndpoint(videoId = id)
    return baseEndpoint.copy(
        videoId = baseEndpoint.videoId ?: id,
        playlistId = baseEndpoint.playlistId ?: playlistId,
        playlistSetVideoId = baseEndpoint.playlistSetVideoId ?: setVideoId,
        params = baseEndpoint.params ?: playlistPlayParams,
    )
}

fun toggleOnlinePlaylistLike(
    database: MusicDatabase,
    playlist: PlaylistItem?,
    dbPlaylist: Playlist?,
    songs: List<SongItem>,
) {
    val current = playlist ?: return
    if (dbPlaylist?.playlist == null) {
        database.transaction {
            val existingPlaylist = playlistEntityByBrowseId(current.id)
            val targetPlaylistId =
                if (existingPlaylist == null) {
                    val playlistEntity =
                        PlaylistEntity(
                            name = current.title,
                            browseId = current.id,
                            thumbnailUrl = current.thumbnail,
                            isEditable = current.isEditable,
                            playEndpointParams = current.playEndpoint?.params,
                            shuffleEndpointParams = current.shuffleEndpoint?.params,
                            radioEndpointParams = current.radioEndpoint?.params,
                        ).toggleLike()
                    insert(playlistEntity)
                    playlistEntityByBrowseId(current.id)?.id ?: playlistEntity.id
                } else {
                    val refreshedPlaylist =
                        existingPlaylist.copy(
                            name = current.title,
                            browseId = current.id,
                            thumbnailUrl = current.thumbnail,
                            isEditable = current.isEditable,
                            playEndpointParams = current.playEndpoint?.params,
                            shuffleEndpointParams = current.shuffleEndpoint?.params,
                            radioEndpointParams = current.radioEndpoint?.params,
                        )
                    update(
                        if (existingPlaylist.bookmarkedAt == null) {
                            refreshedPlaylist.toggleLike()
                        } else {
                            refreshedPlaylist
                        },
                    )
                    existingPlaylist.id
                }
            if (songs.isNotEmpty()) {
                clearPlaylist(targetPlaylistId)
                songs
                    .onEach { song -> insert(song.toMediaMetadata()) }
                    .mapIndexed { index, song ->
                        PlaylistSongMap(
                            songId = song.id,
                            playlistId = targetPlaylistId,
                            position = index,
                            setVideoId = song.setVideoId,
                        )
                    }.forEach(::insert)
            }
        }
    } else {
        database.transaction {
            val currentPlaylist = dbPlaylist.playlist
            update(currentPlaylist, current)
            update(currentPlaylist.toggleLike())
        }
    }
}

@Composable
fun rememberOnlinePlaylistActions(
    playlist: PlaylistItem?,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    downloadUiState: OnlinePlaylistDownloadUiState,
    searchState: OnlinePlaylistSearchState,
    mediaMetadataId: String?,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    navController: NavController,
    menuState: MenuState,
    context: Context,
    haptics: YumaHaptics,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
): OnlinePlaylistActions =
    remember(
        playlist,
        songs,
        dbPlaylist,
        downloadUiState.downloadState,
        downloadUiState.downloads,
        searchState.selection,
        mediaMetadataId,
    ) {
        OnlinePlaylistActions(
            onPlay = {
                playlist?.playEndpoint?.let { playEndpoint ->
                    playerConnection.playQueue(
                        YouTubeQueue.playlist(playEndpoint),
                    )
                }
            },
            onShuffle = {
                playlist?.shuffleEndpoint?.let { shuffleEndpoint ->
                    playerConnection.playQueue(
                        YouTubeQueue.playlist(shuffleEndpoint),
                    )
                }
            },
            onRadio = {
                playlist?.radioEndpoint?.let { radioEndpoint ->
                    playerConnection.playQueue(
                        YouTubeQueue(radioEndpoint),
                    )
                }
            },
            onMix = {
                val currentPlaylist = playlist ?: return@OnlinePlaylistActions
                val mixEndpoint = currentPlaylist.shuffleEndpoint ?: currentPlaylist.radioEndpoint
                if (mixEndpoint != null) {
                    playerConnection.playQueue(
                        if (mixEndpoint == currentPlaylist.shuffleEndpoint) {
                            YouTubeQueue.playlist(mixEndpoint)
                        } else {
                            YouTubeQueue(mixEndpoint)
                        },
                    )
                }
            },
            onSongClick = { song ->
                if (!searchState.selection) {
                    if (song.item.second.id == mediaMetadataId) {
                        playerConnection.player.togglePlayPause()
                    } else {
                        val currentPlaylist = playlist ?: return@OnlinePlaylistActions
                        playerConnection.playQueue(
                            YouTubeQueue.playlist(
                                endpoint =
                                    song.item.second
                                        .toPlaylistPlaybackEndpoint(
                                            playlistId = currentPlaylist.id,
                                            playlistPlayParams =
                                                currentPlaylist.playEndpoint
                                                    ?.params,
                                        ),
                                preloadItem = song.item.second.toMediaMetadata(),
                            ),
                        )
                    }
                } else {
                    song.isSelected = !song.isSelected
                }
            },
            onSongLongClick = { song ->
                haptics.longPress()
                if (!searchState.selection) {
                    searchState.selection = true
                }
                searchState.wrappedSongs.forEach { it.isSelected = false }
                song.isSelected = true
            },
            onLike = {
                toggleOnlinePlaylistLike(
                    database = database,
                    playlist = playlist,
                    dbPlaylist = dbPlaylist,
                    songs = songs,
                )
            },
            onMenu = {
                val current = playlist ?: return@OnlinePlaylistActions
                menuState.show {
                    YouTubePlaylistMenu(
                        playlist = current,
                        songs = songs,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss,
                        selectAction = { searchState.selection = true },
                        canSelect = true,
                        snackbarHostState = snackbarHostState,
                    )
                }
            },
            onSongMenu = { songItem ->
                menuState.show {
                    YouTubeSongMenu(
                        song = songItem,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
            onSongToggleLike = { songItem ->
                coroutineScope.launch(Dispatchers.IO) {
                    val currentSong = database.getSongById(songItem.id)
                        ?: run {
                            database.insert(songItem.toMediaMetadata())
                            database.getSongById(songItem.id)
                        }
                    currentSong?.let {
                        val updated = it.song.localToggleLike(LikeSource.YTM)
                        database.update(updated)
                        playerConnection.service.syncUtils.likeSong(updated, LikeSource.YTM)
                    }
                }
            },
            onDownload = {
                when (downloadUiState.downloadState) {
                    HeaderDownloadState.Completed -> {
                        sendRemoveDownloads(
                            context = context,
                            songIds = songs.map { it.id },
                        )
                    }

                    else -> {
                        downloadUiState.dismissed = false
                        sendAddMissingDownloads(
                            context = context,
                            songs =
                                songs.map { song ->
                                    HeaderDownloadItem(
                                        id = song.id,
                                        title = song.title,
                                    )
                                },
                            downloads = downloadUiState.downloads,
                        )
                    }
                }
            },
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onArtistClick = { artistId ->
                navController.navigate("artist/$artistId")
            },
        )
    }
