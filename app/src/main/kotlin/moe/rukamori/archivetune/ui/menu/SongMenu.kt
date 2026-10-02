/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.utils.LikeSourceResolver
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
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

    val splitArtists = rememberSongMenuSplitArtists(orderedArtists, artistSeparators)

    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

    SongMenuEditDialog(
        isVisible = showEditDialog,
        song = song,
        database = database,
        coroutineScope = coroutineScope,
        onDismiss = { showEditDialog = false },
        onDismissMenu = onDismiss,
    )

    SongMenuAddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        songId = song.id,
        context = context,
        onDismiss = { showChoosePlaylistDialog = false },
    )

    SongMenuErrorDialog(
        isVisible = showErrorPlaylistAddDialog,
        song = song,
        onDismiss = {
            showErrorPlaylistAddDialog = false
            onDismiss()
        },
    )

    SongMenuSelectArtistDialog(
        isVisible = showSelectArtistDialog,
        splitArtists = splitArtists,
        navController = navController,
        onDismiss = { showSelectArtistDialog = false },
    )

    val likeSource =
        remember(song.song.id, playlistBrowseId, song.song.isLocal) {
            if (playlistBrowseId?.startsWith("spotify:") == true) {
                LikeSource.SPOTIFY
            } else {
                LikeSourceResolver.resolve(
                    mediaId = song.song.id,
                    isLocal = song.song.isLocal,
                )
            }
        }
    val isLiked =
        when (likeSource) {
            LikeSource.SPOTIFY -> song.song.likedSpotify
            LikeSource.YTM -> song.song.likedYtm
        }

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val isLocalSong = song.song.isLocal

    SongMenuHeader(
        song = song,
        likeSource = likeSource,
        isLiked = isLiked,
        database = database,
        syncUtils = syncUtils,
    )

    Spacer(modifier = Modifier.height(16.dp))

    SongMenuContent(
        song = song,
        event = event,
        playlistSong = playlistSong,
        playlistBrowseId = playlistBrowseId,
        isFromCache = isFromCache,
        isLocalSong = isLocalSong,
        isInSpeedDial = isInSpeedDial,
        songPin = songPin,
        speedDialPins = speedDialPins,
        download = download,
        playbackSource = playbackSource,
        externalDownloaderEnabled = externalDownloaderEnabled,
        externalDownloaderPackage = externalDownloaderPackage,
        rotationAnimation = rotationAnimation,
        splitArtists = splitArtists,
        cacheViewModel = cacheViewModel,
        playerConnection = playerConnection,
        database = database,
        coroutineScope = coroutineScope,
        bottomSheetPageState = bottomSheetPageState,
        navController = navController,
        context = context,
        onSpeedDialSongIdsChange = onSpeedDialSongIdsChange,
        onShowChoosePlaylistDialog = { showChoosePlaylistDialog = true },
        onShowEditDialog = { showEditDialog = true },
        onShowSelectArtistDialog = { showSelectArtistDialog = true },
        onRotateRefetch = { refetchIconDegree -= 360 },
        onDismiss = onDismiss,
    )
}
