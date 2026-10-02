/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdateSummaryCard(
    currentVersion: String,
    latestVersion: String?,
    updateChannel: UpdateChannel,
    isUpdateAvailable: Boolean,
    isCheckingForUpdate: Boolean,
    imageUrl: String?,
    isDownloadReady: Boolean,
    onDownloadApk: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenAllReleases: () -> Unit,
) {
    val channelLabel = if (updateChannel == UpdateChannel.STABLE) stringResource(R.string.channel_stable) else stringResource(R.string.channel_canary)
    val supportingText = when {
        latestVersion == null -> stringResource(R.string.updates_status_checking)
        isUpdateAvailable -> stringResource(R.string.latest_version_format, latestVersion)
        else -> stringResource(R.string.updates_status_current)
    }
    val statusContainerColor = if (isUpdateAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val statusContentColor = if (isUpdateAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    val colors = LocalYumaColors.current
    val cardShape = RoundedCornerShape(28.dp)
    val imageShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .yumaGlassCard(
                shape = cardShape,
                backgroundColor = colors.glassBackground,
                borderColor = colors.glassBorder,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            )
            .padding(SettingsDimensions.BannerContentPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!imageUrl.isNullOrBlank()) {
                val context = LocalContext.current
                val imageModel = remember(context, imageUrl) {
                    coil3.request.ImageRequest.Builder(context).data(imageUrl).crossfade(true).build()
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(imageShape),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeatureIcon(iconRes = R.drawable.update, containerColor = statusContainerColor, contentColor = statusContentColor)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = stringResource(R.string.current_version), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = currentVersion, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            ) {
                StatusBadgeChip(text = channelLabel, iconRes = R.drawable.tune, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                StatusBadgeChip(
                    text = supportingText,
                    iconRes = if (isUpdateAvailable) R.drawable.download else R.drawable.check,
                    containerColor = if (isUpdateAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (isUpdateAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isCheckingForUpdate) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(text = stringResource(R.string.check_for_update), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    InteractiveChip(label = stringResource(R.string.download_apk), iconResId = R.drawable.download, onClick = onDownloadApk, enabled = isDownloadReady, modifier = Modifier.fillMaxWidth())
                    InteractiveChip(label = stringResource(R.string.check_again), iconResId = R.drawable.sync, onClick = onCheckForUpdate, modifier = Modifier.fillMaxWidth())
                    InteractiveChip(label = stringResource(R.string.view_changelog), iconResId = R.drawable.update, onClick = onOpenChangelog, modifier = Modifier.fillMaxWidth())
                    InteractiveChip(label = stringResource(R.string.all_releases_github), iconResId = R.drawable.ic_github, onClick = onOpenAllReleases, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun StatusBadgeChip(
    text: String,
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = containerColor) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}

@Composable
internal fun InteractiveChip(
    label: String,
    iconResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalYumaColors.current
    val shape = RoundedCornerShape(16.dp)
    val contentAlpha = if (enabled) 1f else 0.38f

    Box(
        modifier = modifier
            .yumaClickable(enabled = enabled, pressedScale = SettingsAnimations.PressScale, onClick = onClick)
            .yumaGlassCard(
                shape = shape,
                backgroundColor = if (enabled) colors.glassBackground else colors.glassBackground.copy(alpha = colors.glassBackground.alpha * 0.4f),
                borderColor = if (enabled) colors.glassBorder else colors.glassBorder.copy(alpha = colors.glassBorder.alpha * 0.4f),
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(painter = painterResource(iconResId), contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
