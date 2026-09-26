/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@Composable
internal fun SpotifyPlaylistControls(
    hasTracks: Boolean,
    onReload: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val syncLabel = stringResource(R.string.spotify_reload_playlist)
    val playLabel = stringResource(R.string.play)
    val shuffleLabel = stringResource(R.string.shuffle)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .yumaClickable(
                        pressedScale = SettingsAnimations.PressScale,
                        onClick = onReload,
                    )
                    .yumaGlassCard(
                        shape = CircleShape,
                        backgroundColor = LocalYumaColors.current.glassBackground,
                    )
                    .clip(CircleShape)
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

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .yumaClickable(
                        pressedScale = SettingsAnimations.PressScale,
                        enabled = hasTracks,
                        onClick = onPlay,
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
                    .clip(CircleShape)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = playLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .yumaClickable(
                        pressedScale = SettingsAnimations.PressScale,
                        enabled = hasTracks,
                        onClick = onShuffle,
                    )
                    .yumaGlassCard(
                        shape = CircleShape,
                        backgroundColor = LocalYumaColors.current.glassBackground,
                    )
                    .clip(CircleShape)
                    .semantics(mergeDescendants = true) {
                        contentDescription = shuffleLabel
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
            )
        }
    }
}
