/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playlist.source.CachePlaylistSource
import moe.rukamori.archivetune.playlist.source.InnerTubePlaylistSource
import moe.rukamori.archivetune.playlist.source.PlaylistCapabilities
import moe.rukamori.archivetune.playlist.source.PlaylistTracksSource
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.playlist.source.SpotifyPlaylistSource
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadStateHolder
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads

/**
 * Hero action-row wiring, extracted from PlaylistDetailScreen to keep it
 * under the 250-line rule. Play/shuffle dispatch per origin through
 * [onPlaylistDetailPlay]/[onPlaylistDetailShuffle]; download follows the
 * legacy Online/Auto/Top shape (Completed -> remove downloads, else ->
 * add missing and un-dismiss the progress toolbar).
 *
 * SPOTIFY PLAYLIST SLOT: the legacy hero's sync/reload button
 * (SpotifyPlaylistControls.kt:63-88) renders as the leading action on
 * Spotify routes; other routes pass [leadingAction] through untouched.
 * ONLINE PLAYLIST SLOT: the legacy hero's leading BOOKMARK heart
 * (saves the playlist into the Room library via
 * InnerTubePlaylistMutations.togglePlaylistBookmark) or, for LM playlists
 * with no like, the radio button (OnlinePlaylistHeroSection.kt:181-254).
 * LOCAL PLAYLIST SLOT: the delete-or-like circle rides [leadingAction],
 * and [localOnRemoveDownloads] overrides the Completed-download confirm's
 * onConfirm (a non-editable local playlist also clears its songs — legacy
 * removeLocalPlaylistDownloads) — keep these signatures stable.
 */
@Composable
internal fun PlaylistDetailHeroActionsHost(
    viewModel: PlaylistDetailViewModel,
    capabilities: PlaylistCapabilities,
    source: PlaylistTracksSource,
    playerConnection: PlayerConnection,
    downloadHolder: PlaylistDownloadStateHolder,
    title: String,
    tracks: List<PlaylistTrack>,
    leadingAction: (@Composable () -> Unit)? = null,
    localOnRemoveDownloads: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Legacy heroes route a Completed download button through a confirm
    // dialog (Top/Auto/Local); never remove downloads un-confirmed.
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    if (showRemoveDownloadDialog) {
        PlaylistDetailRemoveDownloadDialog(
            playlistName = title,
            songIds = tracks.map { it.id },
            onDismiss = { showRemoveDownloadDialog = false },
            onConfirm = localOnRemoveDownloads,
        )
    }

    // SPOTIFY PLAYLIST SLOT — the legacy hero's sync/reload button,
    // re-fetching the playlist from the network (pull-to-refresh's twin).
    val spotifyLeadingAction: (@Composable () -> Unit)? =
        if (source is SpotifyPlaylistSource) {
            { PlaylistDetailHeroSyncButton(onReload = viewModel::refresh) }
        } else {
            null
        }

    // ONLINE PLAYLIST SLOT — the legacy hero's leading bookmark heart (LM
    // playlists have no like; they fall back to the radio button,
    // OnlinePlaylistHeroSection.kt:181-254). The bookmark state reads the
    // live Room row keyed by browseId; the toggle ports
    // toggleOnlinePlaylistLike (InnerTubePlaylistMutations).
    val onlineLeadingAction: (@Composable () -> Unit)? =
        if (source is InnerTubePlaylistSource) {
            onlinePlaylistLeadingAction(
                source = source,
                playerConnection = playerConnection,
            )
        } else {
            null
        }

    val currentDownloadState by downloadHolder.state.collectAsStateWithLifecycle()

    PlaylistDetailHeroActions(
        modifier = modifier,
        capabilities = capabilities,
        downloadState = currentDownloadState.downloadState,
        // Empty-list gating, legacy parity: Spotify disables Play/Shuffle on
        // an empty playlist (SpotifyPlaylistControls.kt:97,139); Cache also
        // DIMS the disabled buttons (CachePlaylistScreen.kt:716,773,806);
        // Local/Online/Top/Auto legacy heroes never gated (stays enabled).
        playbackEnabled = tracks.isNotEmpty() || !(source is SpotifyPlaylistSource || source is CachePlaylistSource),
        dimDisabled = source is CachePlaylistSource,
        onPlay = {
            onPlaylistDetailPlay(
                source = source,
                playerConnection = playerConnection,
                title = title,
                tracks = tracks,
            )
        },
        onShuffle = {
            onPlaylistDetailShuffle(
                source = source,
                playerConnection = playerConnection,
                title = title,
                tracks = tracks,
            )
        },
        onDownload = {
            when (currentDownloadState.downloadState) {
                HeaderDownloadState.Completed ->
                    showRemoveDownloadDialog = true

                else -> {
                    downloadHolder.setDismissed(false)
                    sendAddMissingDownloads(
                        context = context,
                        songs =
                            tracks.map { track ->
                                HeaderDownloadItem(
                                    id = track.id,
                                    title = track.title,
                                )
                            },
                        downloads = currentDownloadState.downloads,
                    )
                }
            }
        },
        onAddToQueue = {
            onPlaylistDetailAddToQueue(
                playerConnection = playerConnection,
                tracks = tracks,
            )
        },
        leadingAction = spotifyLeadingAction ?: onlineLeadingAction ?: leadingAction,
    )
}

/**
 * The legacy Spotify hero's sync/reload button, verbatim
 * SpotifyPlaylistControls.kt:63-88: a 48dp glass circle with the sync icon.
 */
@Composable
private fun PlaylistDetailHeroSyncButton(onReload: () -> Unit) {
    val syncLabel = stringResource(R.string.spotify_reload_playlist)
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onReload,
                ).yumaGlassCard(
                    shape = CircleShape,
                    backgroundColor = LocalYumaColors.current.glassBackground,
                ).clip(CircleShape)
                .semantics(mergeDescendants = true) {
                    contentDescription = syncLabel
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.sync),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
        )
    }
}
