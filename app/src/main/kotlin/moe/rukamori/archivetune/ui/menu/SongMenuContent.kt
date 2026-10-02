/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.viewmodels.CachePlaylistViewModel

@Composable
internal fun SongMenuContent(
    song: Song,
    event: Event?,
    playlistSong: PlaylistSong?,
    playlistBrowseId: String?,
    isFromCache: Boolean,
    isLocalSong: Boolean,
    isInSpeedDial: Boolean,
    songPin: SpeedDialPin,
    speedDialPins: List<SpeedDialPin>,
    download: Download?,
    playbackSource: PlaybackSource,
    externalDownloaderEnabled: Boolean,
    externalDownloaderPackage: String,
    rotationAnimation: Float,
    splitArtists: List<SongMenuSplitArtist>,
    cacheViewModel: CachePlaylistViewModel,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    bottomSheetPageState: BottomSheetPageState,
    navController: NavController,
    context: Context,
    onSpeedDialSongIdsChange: (String) -> Unit,
    onShowChoosePlaylistDialog: () -> Unit,
    onShowEditDialog: () -> Unit,
    onShowSelectArtistDialog: () -> Unit,
    onRotateRefetch: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            SongMenuActionGrid(
                song = song,
                isLocalSong = isLocalSong,
                playerConnection = playerConnection,
                context = context,
                onDismiss = onDismiss,
                onShowChoosePlaylistDialog = onShowChoosePlaylistDialog,
                onShowEditDialog = onShowEditDialog,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            SongMenuLibraryAndSpeedDialSection(
                song = song,
                isLocalSong = isLocalSong,
                isInSpeedDial = isInSpeedDial,
                songPin = songPin,
                speedDialPins = speedDialPins,
                database = database,
                onSpeedDialSongIdsChange = onSpeedDialSongIdsChange,
                onDismiss = onDismiss,
            )
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
                        SongMenuMutationHistoryItem(
                            event = event,
                            database = database,
                            index = eventIndex,
                            count = mutationItemCount,
                            onDismiss = onDismiss,
                        )
                    }

                    if (playlistSong != null) {
                        SongMenuMutationPlaylistItem(
                            playlistSong = playlistSong,
                            playlistBrowseId = playlistBrowseId,
                            database = database,
                            coroutineScope = coroutineScope,
                            context = context,
                            index = playlistSongIndex,
                            count = mutationItemCount,
                            onDismiss = onDismiss,
                        )
                    }

                    if (isFromCache) {
                        SongMenuMutationCacheItem(
                            song = song,
                            cacheViewModel = cacheViewModel,
                            index = cacheIndex,
                            count = mutationItemCount,
                            onDismiss = onDismiss,
                        )
                    }

                    if (!isLocalSong) {
                        SongMenuDownloadItems(
                            song = song,
                            download = download,
                            playbackSource = playbackSource,
                            externalDownloaderEnabled = externalDownloaderEnabled,
                            externalDownloaderPackage = externalDownloaderPackage,
                            downloadIndex = downloadIndex,
                            flacIndex = flacIndex,
                            externalDownloaderIndex = externalDownloaderIndex,
                            mutationItemCount = mutationItemCount,
                            context = context,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            SongMenuArtistAndAlbumSection(
                splitArtists = splitArtists,
                albumId = song.song.albumId,
                navController = navController,
                onDismiss = onDismiss,
                onShowSelectArtistDialog = onShowSelectArtistDialog,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            SongMenuRefetchAndDetailsSection(
                song = song,
                isLocalSong = isLocalSong,
                rotationAnimation = rotationAnimation,
                database = database,
                coroutineScope = coroutineScope,
                bottomSheetPageState = bottomSheetPageState,
                onRotateRefetch = onRotateRefetch,
                onDismiss = onDismiss,
            )
        }
    }
}
