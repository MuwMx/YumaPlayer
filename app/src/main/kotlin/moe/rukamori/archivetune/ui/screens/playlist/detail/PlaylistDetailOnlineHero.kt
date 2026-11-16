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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playlist.source.InnerTubePlaylistSource
import moe.rukamori.archivetune.playlist.source.togglePlaylistBookmark
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

/**
 * ONLINE PLAYLIST SLOT — the hero's leading action for the online route,
 * verbatim the legacy shape (OnlinePlaylistHeroSection.kt:181-254):
 *  - the BOOKMARK heart for bookmark-able playlists (id != "LM"): saves the
 *    playlist into the Room library via
 *    [togglePlaylistBookmark] (the legacy toggleOnlinePlaylistLike port),
 *    with the like state read off the live Room row keyed by browseId;
 *  - else, for LM (no like), the RADIO button when a radio endpoint exists
 *    (starts the playlist radio queue).
 */
@Composable
internal fun onlinePlaylistLeadingAction(
    source: InnerTubePlaylistSource,
    playerConnection: PlayerConnection,
): (@Composable () -> Unit)? {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val dbPlaylist by remember(source.id) {
        database.playlistByBrowseId(source.browseId)
    }.collectAsState(initial = null)
    val isBookmarked = dbPlaylist?.playlist?.bookmarkedAt != null
    val hasLike = source.playlistItem?.id != "LM"

    if (!hasLike) {
        val radioEndpoint = source.radioEndpoint ?: return null
        val radioLabel = stringResource(R.string.radio)
        return {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = {
                                playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                            },
                        ).yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        ).clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = radioLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.radio),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }
        }
    }

    val likeLabel = stringResource(R.string.liked)
    return {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .yumaClickable(
                        pressedScale = SettingsAnimations.PressScale,
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                source.togglePlaylistBookmark(database, dbPlaylist)
                            }
                        },
                    ).yumaGlassCard(
                        shape = CircleShape,
                        backgroundColor = LocalYumaColors.current.glassBackground,
                    ).clip(CircleShape)
                    .semantics(mergeDescendants = true) {
                        contentDescription = likeLabel
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter =
                    painterResource(
                        if (isBookmarked) {
                            R.drawable.favorite
                        } else {
                            R.drawable.favorite_border
                        },
                    ),
                contentDescription = null,
                tint =
                    if (isBookmarked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
            )
        }
    }
}
