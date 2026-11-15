/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import moe.rukamori.archivetune.playlist.source.PlaylistCapabilities
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState

/**
 * The unified hero action row, faithful to each origin screen's legacy order:
 *  - Local (editable: delete / else: like, play, shuffle, download)
 *  - Online (like/radio, play, shuffle, download)
 *  - Auto (download, play, shuffle, add-to-queue)
 *  - Top   (download, play, shuffle, add-to-queue)
 *  - Cache (play, shuffle, add-to-queue)
 * Download/queue visibility: download ⇔ supportsDownload; queue ⇔ auto, top
 * or cache sources (Local/Online legacy heroes have no queue button).
 * LOCAL EDIT SLOT: the Edit/Delete and pick-cover buttons join here with the
 * local flip via [leadingAction]; ONLINE like/radio joins via the same slot.
 */
@Composable
internal fun PlaylistDetailHeroActions(
    capabilities: PlaylistCapabilities,
    downloadState: HeaderDownloadState,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
    leadingAction: (@Composable () -> Unit)? = null,
) {
    val showsDownloadButton = capabilities.supportsDownload
    // Queue tail: auto/top/cache legacy heroes add to queue; Local/Online/
    // Spotify legacy heroes do not (editable or paginated sources).
    val showsQueueButton = !capabilities.editable && !capabilities.paginated
    val playLabel = stringResource(R.string.play)
    val shuffleLabel = stringResource(R.string.shuffle)
    val downloadLabel = stringResource(R.string.download)
    val queueLabel = stringResource(R.string.add_to_queue)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingAction?.invoke()

        if (showsDownloadButton) {
            HeroGlassButton(
                label = downloadLabel,
                onClick = onDownload,
            ) {
                when (downloadState) {
                    HeaderDownloadState.Completed ->
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )

                    is HeaderDownloadState.Partial ->
                        CircularProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            strokeWidth = 2.dp,
                        )

                    HeaderDownloadState.None ->
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                }
            }
        }

        // PLAY — primary pill
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .yumaClickable(
                        pressedScale = SettingsAnimations.PressScale,
                        onClick = onPlay,
                    ).background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ).clip(CircleShape)
                    .semantics(mergeDescendants = true) { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
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

        HeroGlassButton(
            label = shuffleLabel,
            onClick = onShuffle,
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
            )
        }

        // CACHE/AUTO/TOP add-to-queue tail button
        if (showsQueueButton) {
            HeroGlassButton(
                label = queueLabel,
                onClick = onAddToQueue,
            ) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }
        }
    }
}

/** The 48dp glass-circle button every legacy hero used. */
@Composable
private fun HeroGlassButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
                ).yumaGlassCard(
                    shape = CircleShape,
                    backgroundColor = LocalYumaColors.current.glassBackground,
                ).clip(CircleShape)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
