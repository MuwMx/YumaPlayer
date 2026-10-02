/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.utils.GitCommit

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
    val colors = LocalYumaColors.current
    val cardRadius = RoundedCornerShape(SettingsDimensions.GlassCornerRadius)

    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .yumaClickable(
                        pressedScale = SettingsAnimations.PressScale,
                        onClick = onToggleExpanded,
                    )
                    .yumaGlassCard(
                        shape = cardRadius,
                        backgroundColor = colors.glassBackground,
                        borderColor = colors.glassBorder,
                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                FeatureIcon(
                    iconRes = R.drawable.history,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.recent_commits),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            when {
                                isLoading -> stringResource(R.string.updates_loading_commits)
                                commits.isEmpty() -> stringResource(R.string.updates_no_commits)
                                else -> stringResource(R.string.updates_recent_commits_count, commits.size)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotationAngle),
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            when {
                isLoading -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .yumaGlassCard(
                                    shape = cardRadius,
                                    backgroundColor = colors.glassBackground,
                                    borderColor = colors.glassBorder,
                                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                                )
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

                commits.isEmpty() -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .yumaGlassCard(
                                    shape = cardRadius,
                                    backgroundColor = colors.glassBackground,
                                    borderColor = colors.glassBorder,
                                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                                )
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.updates_no_commits),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
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
