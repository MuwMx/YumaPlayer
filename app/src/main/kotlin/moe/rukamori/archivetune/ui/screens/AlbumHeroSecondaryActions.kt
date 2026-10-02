/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun AlbumHeroShuffleButton(
    actions: AlbumActions,
    modifier: Modifier = Modifier,
) {
    val shuffleLabel = stringResource(R.string.shuffle)
    Box(
        modifier =
            modifier
                .size(48.dp)
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = { actions.onShuffle() },
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

@Composable
internal fun AlbumHeroDownloadButton(
    downloadState: HeaderDownloadState,
    actions: AlbumActions,
    modifier: Modifier = Modifier,
) {
    val isDownloaded = downloadState == HeaderDownloadState.Completed
    val downloadContentDescription = stringResource(R.string.download)
    Box(
        modifier =
            modifier
                .size(48.dp)
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = { actions.onDownload() },
                )
                .yumaGlassCard(
                    shape = CircleShape,
                    backgroundColor =
                        if (isDownloaded) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            LocalYumaColors.current.glassBackground
                        },
                )
                .clip(CircleShape)
                .semantics(mergeDescendants = true) {
                    contentDescription = downloadContentDescription
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        when (val state = downloadState) {
            HeaderDownloadState.Completed -> {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }

            is HeaderDownloadState.Partial -> {
                HeaderDownloadProgressIndicator(progress = state.progress)
            }

            else -> {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }
        }
    }
}

@Composable
internal fun AlbumHeroMenuButton(
    actions: AlbumActions,
    modifier: Modifier = Modifier,
) {
    val moreOptionsLabel = stringResource(R.string.more_options)
    Box(
        modifier =
            modifier
                .size(48.dp)
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = { actions.onMenu() },
                )
                .yumaGlassCard(
                    shape = CircleShape,
                    backgroundColor = LocalYumaColors.current.glassBackground,
                )
                .clip(CircleShape)
                .semantics(mergeDescendants = true) {
                    contentDescription = moreOptionsLabel
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
        )
    }
}
