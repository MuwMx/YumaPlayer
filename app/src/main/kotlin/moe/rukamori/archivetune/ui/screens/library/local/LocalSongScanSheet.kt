/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library.local

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.localmedia.LocalSongScanConfig
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.viewmodels.LocalSongsScanState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
internal fun LocalSongScanSheet(
    hasStoragePermission: Boolean,
    scanState: LocalSongsScanState,
    minimumDurationSeconds: Int,
    onMinimumDurationSecondsChange: (Int) -> Unit,
    includedFolders: Set<String>,
    onIncludedFoldersChange: (Set<String>) -> Unit,
    onAddIncludedFolder: () -> Unit,
    excludedFolders: Set<String>,
    onExcludedFoldersChange: (Set<String>) -> Unit,
    onAddExcludedFolder: () -> Unit,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
) {
    val lastSummary = scanState.lastSummary
    val hasError = scanState.errorMessage != null
    val hasSummary = lastSummary != null
    val sanitizedIncludedFolders =
        remember(includedFolders) {
            LocalSongScanConfig
                .deduplicateFolderEntries(includedFolders)
                .toList()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    val sanitizedExcludedFolders =
        remember(excludedFolders) {
            LocalSongScanConfig
                .deduplicateFolderEntries(excludedFolders)
                .toList()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    val durationLabel =
        if (minimumDurationSeconds <= 0) {
            stringResource(R.string.dark_theme_off)
        } else {
            pluralStringResource(R.plurals.seconds, minimumDurationSeconds, minimumDurationSeconds)
        }

    val heroIcon =
        when {
            scanState.isScanning -> R.drawable.sync
            hasError -> R.drawable.error
            !hasStoragePermission -> R.drawable.security
            hasSummary -> R.drawable.done
            else -> R.drawable.library_music
        }

    val heroTint =
        when {
            hasError -> MaterialTheme.colorScheme.error
            scanState.isScanning -> MaterialTheme.colorScheme.primary
            !hasStoragePermission -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

    val heroContainerColor =
        when {
            hasError -> MaterialTheme.colorScheme.errorContainer
            scanState.isScanning -> MaterialTheme.colorScheme.primaryContainer
            !hasStoragePermission -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }

    val statusText =
        when {
            scanState.isScanning -> {
                stringResource(R.string.scanning_device)
            }

            hasError -> {
                stringResource(R.string.local_songs_scan_failed)
            }

            !hasStoragePermission -> {
                stringResource(R.string.local_songs_permission_body)
            }

            lastSummary != null -> {
                stringResource(
                    R.string.local_songs_scan_summary,
                    lastSummary.scannedSongs,
                    lastSummary.removedSongs,
                )
            }

            else -> {
                stringResource(R.string.local_songs_ready_desc)
            }
        }

    val primaryButtonText =
        if (hasStoragePermission) {
            stringResource(R.string.scan_device)
        } else {
            stringResource(R.string.allow)
        }

    val contentAlpha by animateFloatAsState(
        targetValue = if (scanState.isScanning) 0.6f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "contentAlpha",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                    .padding(bottom = SettingsDimensions.BottomSheetBottomPadding)
                    .navigationBarsPadding(),
            shape = RoundedCornerShape(SettingsDimensions.LibrarySheetRadius),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border =
                BorderStroke(
                    width = SettingsDimensions.GlassBorderThickness,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = SettingsDimensions.BottomSheetContentPaddingH)
                        .padding(
                            top = SettingsDimensions.BottomSheetContentPaddingTop,
                            bottom = SettingsDimensions.BottomSheetContentPaddingBottom,
                        ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = SettingsDimensions.BottomSheetDragHandleBottomPadding)
                            .size(
                                width = SettingsDimensions.BottomSheetDragHandleWidth,
                                height = SettingsDimensions.BottomSheetDragHandleHeight,
                            ).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )

                Surface(
                    shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                    color = heroContainerColor,
                    modifier = Modifier.size(80.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = heroIcon,
                            transitionSpec = {
                                fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                                    fadeOut(spring(stiffness = Spring.StiffnessMedium))
                            },
                            label = "heroIcon",
                        ) { icon ->
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = null,
                                tint = heroTint,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.local_songs_scan_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.local_songs_scan_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedVisibility(
                    visible = scanState.isScanning,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow)) + fadeOut(),
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .alpha(contentAlpha),
                        shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.scanning_device),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .alpha(contentAlpha),
                    shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        ScanSheetInfoRow(
                            iconRes = R.drawable.storage,
                            title = stringResource(R.string.permission_storage_title),
                            description = stringResource(R.string.permission_storage_desc),
                            trailing = {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color =
                                        if (hasStoragePermission) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    if (hasStoragePermission) R.drawable.done else R.drawable.close,
                                                ),
                                            contentDescription = null,
                                            tint =
                                                if (hasStoragePermission) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                },
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text =
                                                if (hasStoragePermission) {
                                                    stringResource(R.string.permission_status_allowed)
                                                } else {
                                                    stringResource(R.string.not_allowed)
                                                },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color =
                                                if (hasStoragePermission) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                },
                                        )
                                    }
                                }
                            },
                        )

                        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

                        ScanSheetInfoRow(
                            iconRes = R.drawable.ic_about,
                            title = stringResource(R.string.local_songs_latest_scan),
                            description = statusText,
                            trailing = null,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .alpha(contentAlpha),
                    shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.local_songs_scan_filters_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.local_songs_scan_filters_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        LocalSongScanSettingCard(
                            iconRes = R.drawable.timer,
                            title = stringResource(R.string.local_songs_scan_duration_title),
                            description = stringResource(R.string.local_songs_scan_duration_desc),
                        ) {
                            Text(
                                text = durationLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Slider(
                                value = minimumDurationSeconds.toFloat(),
                                onValueChange = { onMinimumDurationSecondsChange(it.roundToInt()) },
                                valueRange = 0f..180f,
                                steps = 11,
                                enabled = !scanState.isScanning,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        LocalSongScanSettingCard(
                            iconRes = R.drawable.snippet_folder,
                            title = stringResource(R.string.local_songs_scan_included_folders_title),
                            description = stringResource(R.string.local_songs_scan_included_folders_desc),
                            actionLabel = stringResource(R.string.local_songs_scan_included_folders_add),
                            onActionClick = {
                                if (!scanState.isScanning) {
                                    onAddIncludedFolder()
                                }
                            },
                        ) {
                            if (sanitizedIncludedFolders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.local_songs_scan_included_folders_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    sanitizedIncludedFolders.forEach { folderPath ->
                                        LocalSongFolderChip(
                                            folderPath = folderPath,
                                            enabled = !scanState.isScanning,
                                            onRemove = {
                                                onIncludedFoldersChange(
                                                    includedFolders
                                                        .filterNot {
                                                            LocalSongScanConfig
                                                                .normalizeFolderEntry(it)
                                                                .equals(folderPath, ignoreCase = true)
                                                        }.toSet(),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        LocalSongScanSettingCard(
                            iconRes = R.drawable.snippet_folder,
                            title = stringResource(R.string.local_songs_scan_folders_title),
                            description = stringResource(R.string.local_songs_scan_folders_desc),
                            actionLabel = stringResource(R.string.local_songs_scan_folders_add),
                            onActionClick = {
                                if (!scanState.isScanning) {
                                    onAddExcludedFolder()
                                }
                            },
                        ) {
                            if (sanitizedExcludedFolders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.local_songs_scan_folders_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    sanitizedExcludedFolders.forEach { folderPath ->
                                        LocalSongFolderChip(
                                            folderPath = folderPath,
                                            enabled = !scanState.isScanning,
                                            onRemove = {
                                                onExcludedFoldersChange(
                                                    excludedFolders
                                                        .filterNot {
                                                            LocalSongScanConfig
                                                                .normalizeFolderEntry(it)
                                                                .equals(folderPath, ignoreCase = true)
                                                        }.toSet(),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val scanButtonBg =
                    if (scanState.isScanning) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else if (hasStoragePermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                val scanButtonFg =
                    if (scanState.isScanning) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else if (hasStoragePermission) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onTertiary
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .yumaClickable(
                                enabled = !scanState.isScanning,
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = onPrimaryAction,
                            ).background(
                                color = scanButtonBg,
                                shape = CircleShape,
                            ).clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = scanState.isScanning,
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        },
                        label = "buttonContent",
                    ) { isScanning ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        ) {
                            if (isScanning) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = scanButtonFg,
                                )
                            } else {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (hasStoragePermission) R.drawable.sync else R.drawable.security,
                                        ),
                                    contentDescription = null,
                                    tint = scanButtonFg,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text =
                                    if (isScanning) {
                                        stringResource(R.string.scanning_device)
                                    } else {
                                        primaryButtonText
                                    },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scanButtonFg,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = hasError,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow)) + fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(SettingsDimensions.LibrarySmallRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.error),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = scanState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
