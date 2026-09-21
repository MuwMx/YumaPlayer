/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.utils.GitCommit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

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
    val channelLabel =
        when (updateChannel) {
            UpdateChannel.STABLE -> stringResource(R.string.channel_stable)
            else -> stringResource(R.string.channel_canary)
        }
    val supportingText =
        when {
            latestVersion == null -> stringResource(R.string.updates_status_checking)
            isUpdateAvailable -> stringResource(R.string.latest_version_format, latestVersion)
            else -> stringResource(R.string.updates_status_current)
        }
    val statusContainerColor =
        if (isUpdateAvailable) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val statusContentColor =
        if (isUpdateAvailable) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }

    val colors = LocalYumaColors.current
    val cardShape = RoundedCornerShape(28.dp)
    val imageShape = RoundedCornerShape(16.dp)

    Box(
        modifier =
            Modifier
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
                    coil3.request.ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(imageShape),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeatureIcon(
                    iconRes = R.drawable.update,
                    containerColor = statusContainerColor,
                    contentColor = statusContentColor,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.current_version),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = currentVersion,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            ) {
                StatusBadgeChip(
                    text = channelLabel,
                    iconRes = R.drawable.tune,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.check_for_update),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    InteractiveChip(
                        label = stringResource(R.string.download_apk),
                        iconResId = R.drawable.download,
                        onClick = onDownloadApk,
                        enabled = isDownloadReady,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    InteractiveChip(
                        label = stringResource(R.string.check_again),
                        iconResId = R.drawable.sync,
                        onClick = onCheckForUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    InteractiveChip(
                        label = stringResource(R.string.view_changelog),
                        iconResId = R.drawable.update,
                        onClick = onOpenChangelog,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    InteractiveChip(
                        label = stringResource(R.string.all_releases_github),
                        iconResId = R.drawable.ic_github,
                        onClick = onOpenAllReleases,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
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
            .yumaClickable(
                enabled = enabled,
                pressedScale = SettingsAnimations.PressScale,
                onClick = onClick,
            )
            .yumaGlassCard(
                shape = shape,
                backgroundColor = if (enabled) colors.glassBackground else colors.glassBackground.copy(alpha = colors.glassBackground.alpha * 0.4f),
                borderColor = if (enabled) colors.glassBorder else colors.glassBorder.copy(alpha = colors.glassBorder.alpha * 0.4f),
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun UpdateChannelPanel(
    updateChannel: UpdateChannel,
    onStableSelected: () -> Unit,
    onCanarySelected: () -> Unit,
) {
    val isCanary = updateChannel != UpdateChannel.STABLE
    PreferenceEntry(
        title = { Text(text = stringResource(R.string.update_channel)) },
        description = stringResource(R.string.update_channel_desc),
        icon = {
            Icon(
                painter = painterResource(R.drawable.tune),
                contentDescription = null,
            )
        },
        content = {
            Spacer(Modifier.height(10.dp))
            val colors = LocalYumaColors.current
            val barShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(barShape)
                    .background(colors.glassBackground)
                    .border(SettingsDimensions.GlassBorderThickness, colors.glassBorder, barShape)
                    .padding(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ChannelSelectChip(
                        label = stringResource(R.string.channel_stable),
                        isSelected = !isCanary,
                        onClick = onStableSelected,
                        modifier = Modifier.weight(1f),
                    )
                    ChannelSelectChip(
                        label = stringResource(R.string.channel_canary_soon),
                        isSelected = isCanary,
                        onClick = onCanarySelected,
                        modifier = Modifier.weight(1f),
                        enabled = false,
                    )
                }
            }
        },
    )
}

@Composable
internal fun ChannelSelectChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else if (!enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier = modifier
            .yumaClickable(enabled = enabled, pressedScale = SettingsAnimations.PressScale, onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun NightlyInstallPanel(
    latestCommit: GitCommit?,
    onInstallNightly: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.channel_nightly)) {
        item {
            PreferenceEntry(
                title = { Text(text = stringResource(R.string.updates_nightly_title)) },
                description = stringResource(R.string.updates_nightly_description) + "\n" +
                        stringResource(R.string.updates_latest_commit, latestCommit?.sha ?: "-"),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                    )
                },
                onClick = onInstallNightly,
            )
        }
    }
}

@Composable
internal fun CommitHistorySection(
    commits: List<GitCommit>,
    isLoading: Boolean,
    isExpanded: Boolean,
    rotationAngle: Float,
    modifier: Modifier = Modifier,
    onToggleExpanded: () -> Unit,
    onCommitClick: (GitCommit) -> Unit,
) {
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentedListItem(
            onClick = onToggleExpanded,
            shapes =
                ListItemDefaults.shapes(
                    shape = MaterialTheme.shapes.extraLarge,
                ),
            modifier = Modifier.fillMaxWidth(),
            colors =
                ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            leadingContent = {
                FeatureIcon(
                    iconRes = R.drawable.history,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    modifier = Modifier.rotate(rotationAngle),
                )
            },
            supportingContent = {
                Text(
                    text =
                        when {
                            isLoading -> {
                                stringResource(R.string.updates_loading_commits)
                            }

                            commits.isEmpty() -> {
                                stringResource(R.string.updates_no_commits)
                            }

                            else -> {
                                stringResource(
                                    R.string.updates_recent_commits_count,
                                    commits.size,
                                )
                            }
                        },
                )
            },
            content = {
                Text(
                    text = stringResource(R.string.recent_commits),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )

        AnimatedVisibility(visible = isExpanded) {
            when {
                isLoading -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LoadingIndicator(modifier = Modifier.size(32.dp))
                                Text(
                                    text = stringResource(R.string.updates_loading_commits),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                commits.isEmpty() -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = stringResource(R.string.updates_no_commits),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        commits.forEachIndexed { index, commit ->
                            key(commit.sha) {
                                CommitItem(
                                    commit = commit,
                                    index = index,
                                    count = commits.size,
                                    onClick = { onCommitClick(commit) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeatureIcon(
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier =
                Modifier
                    .padding(12.dp)
                    .size(22.dp),
        )
    }
}

@Composable
internal fun CommitItem(
    commit: GitCommit,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        leadingContent = {
            CommitAvatar(avatarUrl = commit.authorAvatarUrl)
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = commit.sha,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text =
                        if (commit.date.isNotEmpty()) {
                            commit.author + " - " + formatCommitDate(commit.date)
                        } else {
                            commit.author
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        content = {
            Text(
                text = commit.message,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
internal fun CommitAvatar(avatarUrl: String?) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

internal fun formatCommitDate(isoDate: String): String =
    try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(isoDate)
        val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        outputFormat.format(date!!)
    } catch (e: Exception) {
        isoDate.take(10)
    }

@Composable
internal fun ColumnScope.UpdateReleaseSheetContent(
    version: String?,
    notes: String?,
    useInAppUpdateInstaller: Boolean,
    onDownloadOrInstall: () -> Unit,
) {
    Text(
        text = stringResource(R.string.new_update_available),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 16.dp),
    )

    Spacer(Modifier.height(8.dp))

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = version ?: "",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    Spacer(Modifier.height(12.dp))

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
    ) {
        if (notes != null && notes.isNotBlank()) {
            MarkdownText(
                markdown = notes,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = stringResource(R.string.release_notes_unavailable),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    val updateButtonText =
        if (useInAppUpdateInstaller) {
            stringResource(R.string.update_text)
        } else {
            stringResource(R.string.download)
        }

    Button(
        onClick = onDownloadOrInstall,
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
    ) {
        Text(text = updateButtonText)
    }

    Spacer(Modifier.height(12.dp))
}

@Composable
internal fun EnableUpdateNotificationConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enable_update_notification)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.updates_channel_warning_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.updates_channel_warning_stable_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.updates_channel_warning_stable_source),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.updates_channel_warning_stable_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.updates_channel_warning_nightly_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.updates_nightly_hosting_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.updates_channel_warning_nightly_risk),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = stringResource(R.string.updates_channel_warning_nightly_unstable),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.updates_channel_warning_acknowledgement),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun NightlyChannelConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.channel_nightly)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.updates_channel_warning_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.updates_channel_warning_stable_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.updates_channel_warning_stable_source),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.updates_channel_warning_stable_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.updates_channel_warning_nightly_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.updates_nightly_hosting_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.updates_channel_warning_nightly_risk),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = stringResource(R.string.updates_channel_warning_nightly_unstable),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.updates_channel_warning_acknowledgement),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun DailyNightlyChannelConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.channel_daily_nightly)) },
        text = {
            Text(
                text = stringResource(R.string.updates_daily_channel_confirmation),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun UpdateCheckingDialog() {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            LoadingIndicator(
                modifier = Modifier.size(24.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.updates_status_checking),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        confirmButton = {},
    )
}

@Composable
internal fun UpdateDownloadDialog(
    updateChannel: UpdateChannel,
    latestCommitSha: String?,
    version: String?,
    progress: Float?,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "updateDownloadProgress",
    )
    val centeredDialogContentModifier = remember { Modifier.fillMaxWidth() }
    val determinateProgressModifier = remember { Modifier.size(96.dp) }
    val determinateIndicatorModifier = remember { Modifier.fillMaxSize() }
    val indeterminateIndicatorModifier = remember { Modifier.size(72.dp) }

    val downloadTitle =
        buildString {
            when (updateChannel) {
                UpdateChannel.DAILY_NIGHTLY -> append("${context.getString(R.string.app_name)} Nightly")
                else -> append(context.getString(R.string.app_name))
            }
            append(' ')
            if (updateChannel == UpdateChannel.NIGHTLY) {
                append(latestCommitSha?.take(7) ?: version ?: "?")
            } else {
                append(version ?: "?")
            }
        }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = downloadTitle,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = centeredDialogContentModifier,
            )
        },
        text = {
            Column(
                modifier = centeredDialogContentModifier,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (progress != null) {
                    Box(
                        modifier = determinateProgressModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { animatedProgress },
                            modifier = determinateIndicatorModifier,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.download_progress_percent,
                                    (animatedProgress * 100f).roundToInt().coerceIn(0, 100),
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    CircularWavyProgressIndicator(
                        modifier = indeterminateIndicatorModifier,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun UpdateUpToDateDialog(
    version: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.updates_status_current),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = version ?: BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        },
    )
}

@Composable
internal fun UpdateErrorDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.error_loading_changelog),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
