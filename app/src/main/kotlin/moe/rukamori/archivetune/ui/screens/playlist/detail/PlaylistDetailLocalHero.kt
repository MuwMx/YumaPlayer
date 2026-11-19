/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.playlist.source.RoomPlaylistSource
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

/**
 * LOCAL PLAYLIST SLOT — the hero's leading circle, verbatim
 * LocalPlaylistHeroSection.kt:301-364: editable playlists show DELETE,
 * non-editable ones show the bookmark LIKE toggle (bookmarked state from the
 * live playlist row). Null on every other route (the screen's leadingAction
 * slot falls through).
 */
@Composable
internal fun localPlaylistHeroLeadingAction(
    room: RoomPlaylistSource,
    editable: Boolean,
    liked: Boolean,
    onDelete: () -> Unit,
    onToggleLike: () -> Unit,
): (@Composable () -> Unit)? {
    if (!editable) {
        val likeLabel = stringResource(R.string.liked)
        return {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = onToggleLike,
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
                            if (liked) R.drawable.favorite else R.drawable.favorite_border,
                        ),
                    contentDescription = null,
                    tint =
                        if (liked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }
        }
    }
    val deleteLabel = stringResource(R.string.delete)
    return {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .yumaClickable(
                        pressedScale = SettingsAnimations.PressScale,
                        onClick = onDelete,
                    ).yumaGlassCard(
                        shape = CircleShape,
                        backgroundColor = LocalYumaColors.current.glassBackground,
                    ).clip(CircleShape)
                    .semantics(mergeDescendants = true) {
                        contentDescription = deleteLabel
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.delete),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
            )
        }
    }
}

/**
 * LOCAL PLAYLIST SLOT — the pick-cover button overlays the hero artwork's
 * top-end corner, verbatim LocalPlaylistHeroSection.kt:177-208 (40dp glass
 * circle, edit icon, positioned under the app bar when artwork shows).
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.LocalPlaylistPickCoverOverlay(
    systemBarsTopPadding: Dp,
    hasArtwork: Boolean,
    onPickCover: () -> Unit,
) {
    val changeCoverLabel = stringResource(R.string.change_playlist_cover)
    Box(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = if (hasArtwork) systemBarsTopPadding + AppBarHeight + 8.dp else 8.dp,
                    end = 16.dp,
                ).size(40.dp)
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onPickCover,
                ).yumaGlassCard(
                    shape = CircleShape,
                    backgroundColor = LocalYumaColors.current.glassBackground,
                ).clip(CircleShape)
                .semantics(mergeDescendants = true) {
                    contentDescription = changeCoverLabel
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.edit),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}
